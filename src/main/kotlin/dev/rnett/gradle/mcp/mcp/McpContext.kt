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
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.PrimitiveSchemaDefinition
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.ServerNotification
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.shared.DEFAULT_REQUEST_TIMEOUT
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
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
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import kotlin.time.Duration

object McpToolHelper {
    @PublishedApi
    internal val logger = LoggerFactory.getLogger("dev.rnett.gradle.mcp.McpTool")
}

sealed interface ElicitationResult<out T> {
    data object Decline : ElicitationResult<Nothing>
    data object NotSupported : ElicitationResult<Nothing>
    data object Cancel : ElicitationResult<Nothing>
    data class Accept<out T>(val data: T) : ElicitationResult<T>

    val isAccepted: Boolean get() = this is Accept
}

open class McpContext(
    val server: McpServer,
    val clientConnection: ClientConnection,
    private val request: CallToolRequest,
) : AutoCloseable {
    private val notificationQueue = MutableSharedFlow<ServerNotification>(0, 500, BufferOverflow.DROP_OLDEST)

    protected open val disableSampling: Boolean
        get() = System.getProperty("gradle.mcp.test.disableSampling") == "true"

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @PublishedApi
    internal val json = server.json

    companion object {
        private val LOGGER = LoggerFactory.getLogger(McpContext::class.java)
    }

    suspend fun sendNotification(notification: ServerNotification) {
        clientConnection.notification(notification)
    }

    fun emitNotification(notification: ServerNotification) {
        notificationQueue.tryEmit(notification)
    }

    fun emitLoggingNotification(logger: String, loggingLevel: LoggingLevel, message: String) {
        emitNotification(LoggingMessageNotification(LoggingMessageNotificationParams(loggingLevel, JsonPrimitive(message), logger)))
    }

    suspend inline fun <reified O> elicit(message: String, timeout: Duration = DEFAULT_REQUEST_TIMEOUT): ElicitationResult<O> {
        val session = server.sessions[clientConnection.sessionId] ?: return ElicitationResult.NotSupported
        if (session.clientCapabilities?.elicitation == null) return ElicitationResult.NotSupported

        val responseSerializer = json.serializersModule.serializer<O>()
        val responseSchema = JsonSchemaFactory.generateSchema<O>(json.serializersModule)
        val result = clientConnection.createElicitation(message, responseSchema.toRequestedSchema(), RequestOptions(timeout = timeout))
        return when (result.action) {
            ElicitResult.Action.Accept -> ElicitationResult.Accept(json.decodeFromJsonElement(responseSerializer, result.content ?: JsonNull))
            ElicitResult.Action.Decline -> ElicitationResult.Decline
            ElicitResult.Action.Cancel -> ElicitationResult.Cancel
        }
    }

    suspend inline fun elicitUnit(message: String, timeout: Duration = DEFAULT_REQUEST_TIMEOUT): ElicitationResult<Unit> {
        val session = server.sessions[clientConnection.sessionId] ?: return ElicitationResult.NotSupported
        if (session.clientCapabilities?.elicitation == null) return ElicitationResult.NotSupported

        val result = clientConnection.createElicitation(message, ElicitRequestParams.RequestedSchema(properties = emptyMap()), RequestOptions(timeout = timeout))
        return when (result.action) {
            ElicitResult.Action.Accept -> ElicitationResult.Accept(Unit)
            ElicitResult.Action.Decline -> ElicitationResult.Decline
            ElicitResult.Action.Cancel -> ElicitationResult.Cancel
        }
    }

    private val progressToken: RequestId? = request.meta?.progressToken

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
                sendNotification(notification)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}

fun CompiledJsonSchemaData.toInput(): ToolSchema {
    val obj = json.toKotlinxSerialization().jsonObject

    if (obj["type"]?.jsonPrimitive?.contentOrNull != "object") {
        error("Object schema expected")
    }

    return ToolSchema(
        properties = obj.getValue("properties").jsonObject,
        required = obj["required"]?.jsonArray?.let { it.map { it.jsonPrimitive.content } }
    )
}

fun CompiledJsonSchemaData.toRequestedSchema(): ElicitRequestParams.RequestedSchema {
    val obj = json.toKotlinxSerialization().jsonObject

    if (obj["type"]?.jsonPrimitive?.contentOrNull != "object") {
        error("Object schema expected")
    }

    return ElicitRequestParams.RequestedSchema(
        properties = obj.getValue("properties").jsonObject.mapValues { (_, v) ->
            McpJson.decodeFromJsonElement<PrimitiveSchemaDefinition>(v)
        },
        required = obj["required"]?.jsonArray?.let { it.map { it.jsonPrimitive.content } }
    )
}

fun CompiledJsonSchemaData.toOutput(): ToolSchema? {
    val obj = json.toKotlinxSerialization().jsonObject
    if (obj["type"]?.jsonPrimitive?.contentOrNull != "object") {
        return null
    }
    return ToolSchema(
        properties = obj.getValue("properties").jsonObject,
        required = obj["required"]?.jsonArray?.let { it.map { it.jsonPrimitive.content } }
    )
}

fun JsonNode.toKotlinxSerialization(): JsonElement = when (this) {
    is JsonArray -> kotlinx.serialization.json.JsonArray(
        items.map { it.toKotlinxSerialization() }
    )

    is JsonObject -> {
        val props = properties.mapValues { it.value.toKotlinxSerialization() }.toMutableMap()
        if (props.containsKey("enum") && !props.containsKey("type")) {
            // Schema-generator quirk: enums without an explicit "type" field are coerced to string
            // so that kotlinx.serialization can deserialize them. See openspec/specs/mcp-schema-simplification/spec.md
            props["type"] = JsonPrimitive("string")
        }
        kotlinx.serialization.json.JsonObject(props)
    }

    is JsonBooleanValue -> JsonPrimitive(value)
    is JsonNullValue -> JsonNull
    is JsonNumericValue -> JsonPrimitive(value)
    is JsonTextValue -> JsonPrimitive(value)
}
