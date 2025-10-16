package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.runCatchingExceptCancellation
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.PromptMessageContent
import io.modelcontextprotocol.kotlin.sdk.Request
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.WithMeta
import kotlinx.serialization.serializer
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun McpServer.add(component: McpServerComponent) {
    component.register(this)
}

abstract class McpServerComponent(val name: String, val description: String) {
    fun register(server: McpServer) {
        _parts.forEach { it.register(server) }
    }

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

    inline fun <reified I, reified O> tool(
        name: String,
        description: String,
        title: String? = null,
        toolAnnotations: ToolAnnotations? = null,
        crossinline handler: suspend McpToolContext.(I) -> O
    ) = register { server ->
        val inputSerializer = server.json.serializersModule.serializer<I>()
        val outputSerializer = server.json.serializersModule.serializer<O>()

        val inputSchema = JsonSchemaFactory.generateSchema(inputSerializer, server.json.serializersModule)
        val outputSchema = JsonSchemaFactory.generateSchema(outputSerializer, server.json.serializersModule)
        val tool = Tool(
            name = name,
            description = description,
            title = title,
            annotations = toolAnnotations,
            inputSchema = inputSchema.toInput(),
            outputSchema = outputSchema.toOutput(),
        )

        server.addTool(
            tool,
        ) { request ->
            val input = server.json.decodeFromJsonElement(inputSerializer, request.arguments)
            val (output, aux) = McpToolContext(server, request, request).use {
                runCatchingExceptCancellation { handler(it, input) } to it.auxiliaryResults()
            }
            output.fold(
                {

                    if (it is String) {
                        return@fold CallToolResult(
                            listOf(TextContent(it)) + aux.additionalResults
                        )
                    }

                    if (it is Unit) {
                        return@fold CallToolResult(
                            listOf(TextContent("Done")) + aux.additionalResults
                        )
                    }

                    if (it is CallToolResult) {
                        return@fold it
                    }

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