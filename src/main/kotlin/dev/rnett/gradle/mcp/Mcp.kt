package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.mcp.JsonSchemaFactory
import dev.rnett.gradle.mcp.mcp.McpServer
import io.github.smiley4.schemakenerator.jsonschema.data.CompiledJsonSchemaData
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonArray
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonBooleanValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNode
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNullValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNumericValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonObject
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonTextValue
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.Notification
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.ToolAnnotations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

object McpToolHelper {
    @PublishedApi
    internal val logger = LoggerFactory.getLogger("dev.rnett.gradle.mcp.McpTool")
}

class McpContext(private val server: McpServer) : AutoCloseable {
    private val notificationQueue = MutableSharedFlow<Notification>(0, 50, BufferOverflow.DROP_OLDEST)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    suspend fun sendNotification(notification: Notification) {
        server.sendNotification(notification)
    }

    fun emitNotification(notification: Notification) {
        notificationQueue.tryEmit(notification)
    }

    fun emitLoggingNotification(logger: String, loggingLevel: LoggingLevel, message: String) {
        emitNotification(LoggingMessageNotification(LoggingMessageNotification.Params(loggingLevel, logger, JsonPrimitive(message))))
    }

    init {
        scope.launch {
            notificationQueue.collect { notification ->
                sendNotification(notification)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}

context(application: Application)
inline fun <reified I, reified O> McpServer.addTool(
    name: String,
    description: String,
    title: String? = null,
    toolAnnotations: ToolAnnotations? = null,
    crossinline handler: suspend McpContext.(I) -> O,
) {
    val json: Json by application.dependencies
    val inputSchema = JsonSchemaFactory.generateSchema<I>(json.serializersModule)
    val outputSchema = JsonSchemaFactory.generateSchema<O>(json.serializersModule)
    addTool(
        name,
        description,
        inputSchema.toInput(),
        title,
        outputSchema.toOutput(),
        toolAnnotations,
    ) { request ->
        val input = json.decodeFromJsonElement<I>(request.arguments)
        val output = runCatchingExceptCancellation { handler(McpContext(this), input) }
        output.fold(
            {

                if (it is String) {
                    return@fold CallToolResult(
                        listOf(TextContent(it))
                    )
                }

                if (it is CallToolResult) {
                    return@fold it
                }

                val structured = json.encodeToJsonElement(it)
                val text = json.encodeToString(structured)
                CallToolResult(
                    listOf(TextContent(text)),
                    structured as? kotlinx.serialization.json.JsonObject
                )
            },
            {
                McpToolHelper.logger.error("Error while executing tool call $request", it)
                CallToolResult(listOf(TextContent("Error executing tool $name: ${it.message ?: "Unknown error"}")), isError = true)
            }
        )
    }
}

fun CompiledJsonSchemaData.toInput(): Tool.Input {
    val obj = json.toKotlinxSerialization().jsonObject

    if (obj["type"]?.jsonPrimitive?.contentOrNull != "object") {
        error("Object schema expected")
    }

    return Tool.Input(
        obj.getValue("properties").jsonObject,
        obj["required"]?.jsonArray?.let { it.map { it.jsonPrimitive.content } }
    )
}

fun CompiledJsonSchemaData.toOutput(): Tool.Output? {
    val obj = json.toKotlinxSerialization().jsonObject
    if (obj["type"]?.jsonPrimitive?.contentOrNull != "object") {
        return null
    }
    return Tool.Output(
        obj.getValue("properties").jsonObject,
        obj["required"]?.jsonArray?.let { it.map { it.jsonPrimitive.content } }
    )
}

fun JsonNode.toKotlinxSerialization(): JsonElement = when (this) {
    is JsonArray -> kotlinx.serialization.json.JsonArray(
        items.map { it.toKotlinxSerialization() }
    )

    is JsonObject -> kotlinx.serialization.json.JsonObject(
        properties.mapValues { it.value.toKotlinxSerialization() }
    )

    is JsonBooleanValue -> JsonPrimitive(value)
    is JsonNullValue -> JsonNull
    is JsonNumericValue -> JsonPrimitive(value)
    is JsonTextValue -> JsonPrimitive(value)
}