package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.runCatchingExceptCancellation
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.PromptMessageContent
import io.modelcontextprotocol.kotlin.sdk.Request
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.WithMeta
import kotlinx.coroutines.async
import kotlinx.serialization.serializer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun McpServer.add(component: McpServerComponent) {
    component.register(this)
}

abstract class McpServerComponent(val name: String, val description: String) {
    open fun register(server: McpServer) {
        _parts.forEach { it.register(server) }
    }

    open suspend fun close() {}

    fun interface Registerer<T> {
        fun register(server: McpServer): T
    }

    private val _parts = mutableListOf<Registerer<*>>()

    @PublishedApi
    internal fun <T> register(part: Registerer<T>): PropertyDelegateProvider<McpServerComponent, ReadOnlyProperty<McpServerComponent, Registerer<T>>> {
        _parts.add(part)
        return Delegate(part)
    }

    private class Delegate<T>(private val registerer: Registerer<T>) : PropertyDelegateProvider<McpServerComponent, ReadOnlyProperty<McpServerComponent, Registerer<T>>> {
        override fun provideDelegate(thisRef: McpServerComponent, property: KProperty<*>): ReadOnlyProperty<McpServerComponent, Registerer<T>> {
            return ReadOnlyProperty { _, _ -> registerer }
        }
    }

    class McpToolContext(server: McpServer, request: Request, withMeta: WithMeta) : McpContext(server, request, withMeta) {
        private val additionalResults = mutableListOf<PromptMessageContent>()

        fun addAdditionalContent(content: PromptMessageContent) {
            additionalResults.add(content)
        }

        var isError: Boolean = false

        @PublishedApi
        internal fun auxiliaryResults(): AuxiliaryResults = AuxiliaryResults(additionalResults.toList(), isError)

        data class AuxiliaryResults(val additionalResults: List<PromptMessageContent>, val isError: Boolean)
    }

    // avoid using structured output when possible, just return strings
    inline fun <reified I, reified O> tool(
        name: String,
        description: String,
        title: String? = null,
        toolAnnotations: ToolAnnotations? = null,
        crossinline handler: suspend McpToolContext.(I) -> O
    ) = register { server ->
        val inputSerializer = server.json.serializersModule.serializer<I>()

        val inputSchema = JsonSchemaFactory.generateSchema(inputSerializer, server.json.serializersModule)
        val outputSchema = if (O::class == String::class || O::class == Unit::class || O::class == CallToolResult::class) {
            null
        } else {
            val outputSerializer = server.json.serializersModule.serializer<O>()
            JsonSchemaFactory.generateSchema(outputSerializer, server.json.serializersModule).toOutput()
        }
        val tool = Tool(
            name = name,
            description = description,
            title = title,
            annotations = toolAnnotations,
            inputSchema = inputSchema.toInput(),
            outputSchema = outputSchema
        )

        server.addTool(
            tool
        ) { request ->
            McpToolHelper.logger.info("Executing tool call {} (request={})", tool.name, request)
            val input = server.json.decodeFromJsonElement(inputSerializer, request.arguments)

            // The request ID was injected into the coroutine context by McpServer's transport
            // interceptor — the only point where the raw JSONRPCRequest.id is accessible.
            val requestId = coroutineContext[ToolCallRequestId]?.value

            // Launch the handler in server.scope so it can be cancelled independently of the
            // SDK's message-processing coroutine. This allows notifications/cancelled to unblock
            // the message loop by cancelling this deferred, even when the tool is waiting on a
            // long-running operation (e.g., awaitFinished() on a hung build).
            val deferred = server.scope.async {
                McpToolContext(server, request, request).use {
                    runCatchingExceptCancellation { handler(it, input) } to it.auxiliaryResults()
                }
            }
            server.registerToolCallJob(requestId, deferred)

            val (output, aux) = try {
                deferred.await()
            } catch (e: CancellationException) {
                // Re-throw so Protocol.onRequest's catch(Throwable) sends an error response
                // and the message processing loop can resume for the next request.
                throw e
            } finally {
                server.unregisterToolCallJob(requestId)
                McpToolHelper.logger.info("Finished tool call {} (request={})", tool.name, request)
            }
            output.fold(
                {

                    if (it is String) {
                        return@fold CallToolResult(
                            listOf(TextContent(it)) + aux.additionalResults,
                            isError = aux.isError
                        )
                    }

                    if (it is Unit) {
                        return@fold CallToolResult(
                            listOf(TextContent("Done")) + aux.additionalResults,
                            isError = aux.isError
                        )
                    }

                    if (it is CallToolResult) {
                        return@fold it
                    }

                    val outputSerializer = server.json.serializersModule.serializer<O>()
                    val structured = server.json.encodeToJsonElement(outputSerializer, it)
                    val text = server.json.encodeToString(structured)
                    CallToolResult(
                        listOf(TextContent(text)) + aux.additionalResults,
                        structured as? kotlinx.serialization.json.JsonObject,
                        isError = aux.isError
                    )
                },
                {
                    McpToolHelper.logger.error("Error while executing tool call $request", it)
                    CallToolResult(listOf(TextContent("Error executing tool ${tool.name}: ${it.message ?: "Unknown error"}")), isError = true)
                }
            )
        }

        tool
    }
}