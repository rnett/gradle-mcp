package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.runCatchingExceptCancellation
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.shared.RequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.shared.currentRequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun Server.add(component: McpServerComponent, json: Json) {
    component.register(this, json)
}

abstract class McpServerComponent(val name: String, val description: String) {
    open fun register(server: Server, json: Json) {
        _parts.forEach { it.register(server, json) }
    }

    open suspend fun close() {}

    fun interface Registerer<T> {
        fun register(server: Server, json: Json): T
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

    class McpToolContext(
        json: Json,
        session: ServerSession?,
        clientConnection: ClientConnection,
        progressToken: RequestId?,
        extra: RequestHandlerExtra?,
    ) : McpContext(json, session, clientConnection, progressToken, extra) {
        var isError: Boolean = false
    }

    // Avoid structured output when possible; plain strings are easier for clients to consume.
    inline fun <reified I, reified O> tool(
        name: String,
        description: String,
        title: String? = null,
        toolAnnotations: ToolAnnotations? = null,
        crossinline handler: suspend McpToolContext.(I) -> O
    ) = register { server, json ->
        val inputSerializer = json.serializersModule.serializer<I>()

        val inputSchema = JsonSchemaFactory.generateSchema(inputSerializer, json.serializersModule)
        val outputSchema = if (O::class == String::class || O::class == Unit::class || O::class == CallToolResult::class) {
            null
        } else {
            val outputSerializer = json.serializersModule.serializer<O>()
            JsonSchemaFactory.generateSchema(outputSerializer, json.serializersModule).toOutput()
        }
        val tool = Tool(
            name = name,
            description = description,
            title = title,
            annotations = toolAnnotations,
            inputSchema = inputSchema.toInput(),
            outputSchema = outputSchema
        )

        server.addTool(tool) { request ->
            McpToolHelper.logger.info("Executing tool call {} (request={})", tool.name, request)
            val input = json.decodeFromJsonElement(
                inputSerializer,
                request.arguments ?: kotlinx.serialization.json.JsonObject(emptyMap())
            )
            val session = server.sessions[this.sessionId]
            val extra = currentRequestHandlerExtra()
            val progressToken = request.meta?.progressToken

            McpToolContext(json, session, this, progressToken, extra).use { context ->
                val output = try {
                    runCatchingExceptCancellation { handler(context, input) }
                } finally {
                    McpToolHelper.logger.info("Finished tool call {} (request={})", tool.name, request)
                }
                output.fold(
                    {
                        if (it is String) {
                            return@fold CallToolResult(
                                listOf(TextContent(it)),
                                isError = context.isError
                            )
                        }

                        if (it is Unit) {
                            return@fold CallToolResult(
                                listOf(TextContent("Done")),
                                isError = context.isError
                            )
                        }

                        if (it is CallToolResult) {
                            return@fold it
                        }

                        val outputSerializer = json.serializersModule.serializer<O>()
                        val structured = json.encodeToJsonElement(outputSerializer, it)
                        val text = json.encodeToString(structured)
                        CallToolResult(
                            listOf(TextContent(text)),
                            structuredContent = structured as? kotlinx.serialization.json.JsonObject,
                            isError = context.isError
                        )
                    },
                    {
                        McpToolHelper.logger.error("Error while executing tool call $request", it)
                        CallToolResult(
                            listOf(TextContent("Error executing tool ${tool.name}: ${it.message ?: "Unknown error"}")),
                            isError = true
                        )
                    }
                )
            }
        }

        tool
    }
}
