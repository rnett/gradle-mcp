package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.ProgressReporter
import io.github.smiley4.schemakenerator.jsonschema.data.CompiledJsonSchemaData
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonArray
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonBooleanValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNode
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNullValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNumericValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonObject
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonTextValue
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.shared.RequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.ServerNotification
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

object McpToolHelper {
    @PublishedApi
    internal val logger = LoggerFactory.getLogger("dev.rnett.gradle.mcp.McpTool")
}

open class McpContext(
    @PublishedApi internal val json: Json,
    val session: ServerSession?,
    val clientConnection: ClientConnection,
    private val progressToken: RequestId?,
    private val extra: RequestHandlerExtra?,
) : AutoCloseable {
    private val notificationQueue = MutableSharedFlow<ServerNotification>(0, 500, BufferOverflow.DROP_OLDEST)

    protected open val disableSampling: Boolean
        get() = System.getProperty("gradle.mcp.test.disableSampling") == "true"

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private val LOGGER = LoggerFactory.getLogger(McpContext::class.java)
    }

    fun emitNotification(notification: ServerNotification) {
        notificationQueue.tryEmit(notification)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val progressReporter: ProgressReporter by lazy {
        val flow = MutableSharedFlow<Triple<Double, Double?, String?>>(10, extraBufferCapacity = 50, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        scope.launch {
            val transformed = flow.transformLatest {
                val (p, t, msg) = it
                while (currentCoroutineContext().isActive) {
                    repeat(4) {
                        val suffix = if (it == 0) "" else ".".repeat(it)
                        emit(Triple(p, t, msg + suffix))
                        delay(500)
                    }
                }
            }

            val sampled = if (disableSampling) flow else transformed.sample(100)

            sampled.collect {
                try {
                    emitProgressNotification(it.first, it.second, it.third)
                } catch (e: Exception) {
                    LOGGER.error("Failed to emit progress notification", e)
                }
            }
        }

        ProgressReporter { progress, total, message ->
            flow.tryEmit(Triple(progress, total, message))
        }
    }

    fun emitProgressNotification(progress: Double, total: Double? = null, message: String? = null) {
        if (progressToken != null) {
            emitNotification(
                ProgressNotification(
                    ProgressNotificationParams(
                        progressToken,
                        progress,
                        total,
                        message
                    )
                )
            )
        }
    }

    init {
        scope.launch {
            notificationQueue.collect { notification ->
                extra?.sendNotification(notification) ?: clientConnection.notification(notification)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}

/**
 * Converts schema-kenerator output to an MCP [ToolSchema].
 * Returns `null` if the top-level schema type is not `"object"`.
 * `$defs` from the source schema are preserved via [ToolSchema.defs].
 */
private fun CompiledJsonSchemaData.toToolSchema(): ToolSchema? {
    val obj = json.toKotlinxSerialization().jsonObject

    if (obj["type"]?.jsonPrimitive?.contentOrNull != "object") {
        return null
    }

    return ToolSchema(
        properties = obj.getValue("properties").jsonObject,
        required = obj["required"]?.jsonArray?.let { it.map { it.jsonPrimitive.content } },
        defs = obj["\$defs"]?.jsonObject
    )
}

fun CompiledJsonSchemaData.toInput(): ToolSchema =
    toToolSchema() ?: error("Object schema expected")

fun CompiledJsonSchemaData.toOutput(): ToolSchema? = toToolSchema()

fun JsonNode.toKotlinxSerialization(): JsonElement = when (this) {
    is JsonArray -> kotlinx.serialization.json.JsonArray(
        items.map { it.toKotlinxSerialization() }
    )

    is JsonObject -> {
        val props = properties.mapValues { it.value.toKotlinxSerialization() }.toMutableMap()
        // schema-kenerator emits enum schemas without a `type` field, which the MCP SDK schema model
        // rejects unless `"type": "string"` is injected. See openspec/specs/mcp-schema-simplification/spec.md
        if (props.containsKey("enum") && !props.containsKey("type")) {
            props["type"] = JsonPrimitive("string")
        }
        kotlinx.serialization.json.JsonObject(props)
    }

    is JsonBooleanValue -> JsonPrimitive(value)
    is JsonNullValue -> JsonNull
    is JsonNumericValue -> JsonPrimitive(value)
    is JsonTextValue -> JsonPrimitive(value)
}
