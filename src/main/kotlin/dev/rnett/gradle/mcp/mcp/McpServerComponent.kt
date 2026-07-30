package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.ProgressReporter
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.shared.currentRequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

data class ToolCallResult<O>(val content: O?, val isError: Boolean = false)
abstract class McpServerComponent(val name: String, val description: String) {
    open fun register(server: Server, json: Json) {
        registrations.forEach { it(server, json) }
    }

    open suspend fun close() {}

    @PublishedApi
    internal val registrations = mutableListOf<(Server, Json) -> Unit>()

    // Avoid structured output when possible; plain strings are easier for clients to consume.
    inline fun <reified I, reified O> tool(
        name: String,
        description: String,
        title: String? = null,
        toolAnnotations: ToolAnnotations? = null,
        noinline handler: suspend (input: I, progressReporter: ProgressReporter) -> ToolCallResult<O>
    ) {
        registrations += { server, json ->
            val inputSerializer = json.serializersModule.serializer<I>()
            val outputSerializer = outputSerializer<O>(json)
            val outputConverter = outputConverter<O>(json, outputSerializer)

            val inputSchema = JsonSchemaFactory.generateSchema(inputSerializer, json.serializersModule)
            val outputSchema = outputSerializer?.let {
                JsonSchemaFactory.generateSchema(it, json.serializersModule).toOutput()
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
                MCP_TOOL_LOGGER.info("Executing tool call {} (request={})", tool.name, request)
                val extra = currentRequestHandlerExtra()
                val progress = ProgressNotificationPipeline(this, request.meta?.progressToken, extra)
                try {
                    val input = json.decodeFromJsonElement(
                        inputSerializer,
                        request.arguments ?: JsonObject(emptyMap())
                    )
                    outputConverter(handler(input, progress.progressReporter))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    MCP_TOOL_LOGGER.error("Error while executing tool call $request", e)
                    CallToolResult(
                        listOf(TextContent("Error executing tool ${tool.name}: ${e.message ?: "Unknown error"}")),
                        isError = true
                    )
                } finally {
                    progress.close()
                    MCP_TOOL_LOGGER.info("Finished tool call {} (request={})", tool.name, request)
                }
            }
        }
    }

    @PublishedApi
    internal inline fun <reified O> outputSerializer(json: Json): KSerializer<O>? =
        if (O::class == String::class || O::class == Unit::class || O::class == CallToolResult::class) {
            null
        } else {
            json.serializersModule.serializer<O>()
        }

    @PublishedApi
    internal inline fun <reified O> outputConverter(
        json: Json,
        outputSerializer: KSerializer<O>?
    ): (ToolCallResult<O>) -> CallToolResult = when (O::class) {
        String::class -> { result ->
            val content = result.content as String?
            CallToolResult(content?.let { listOf(TextContent(it)) } ?: emptyList(), isError = result.isError)
        }

        Unit::class -> { result ->
            CallToolResult(emptyList(), isError = result.isError)
        }

        CallToolResult::class -> { result ->
            val direct = result.content as CallToolResult?
            direct?.copy(isError = direct.isError == true || result.isError)
                ?: CallToolResult(emptyList(), isError = result.isError)
        }

        else -> {
            val serializer = requireNotNull(outputSerializer);
            { result: ToolCallResult<O> ->
                val content = result.content
                if (content == null) {
                    CallToolResult(emptyList(), isError = result.isError)
                } else {
                    val structured = json.encodeToJsonElement(serializer, content)
                    CallToolResult(
                        listOf(TextContent(json.encodeToString(structured))),
                        structuredContent = structured as? JsonObject,
                        isError = result.isError
                    )
                }
            }
        }
    }
}
