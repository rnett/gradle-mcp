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
import io.modelcontextprotocol.kotlin.sdk.CreateElicitationRequest
import io.modelcontextprotocol.kotlin.sdk.CreateElicitationResult
import io.modelcontextprotocol.kotlin.sdk.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.Notification
import io.modelcontextprotocol.kotlin.sdk.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.Request
import io.modelcontextprotocol.kotlin.sdk.RequestId
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.WithMeta
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
import kotlinx.coroutines.flow.collectLatest
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
    private val request: Request,
    val requestWithMeta: WithMeta
) : AutoCloseable {
    private val notificationQueue = MutableSharedFlow<Notification>(0, 500, BufferOverflow.DROP_OLDEST)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @PublishedApi
    internal val json = server.json

    companion object {
        private val LOGGER = LoggerFactory.getLogger(McpContext::class.java)
    }

    suspend fun sendNotification(notification: Notification) {
        server.sendNotification(notification)
    }

    fun emitNotification(notification: Notification) {
        notificationQueue.tryEmit(notification)
    }

    fun emitLoggingNotification(logger: String, loggingLevel: LoggingLevel, message: String) {
        emitNotification(LoggingMessageNotification(LoggingMessageNotification.Params(loggingLevel, logger, JsonPrimitive(message))))
    }

    suspend inline fun <reified O> elicit(message: String, timeout: Duration = DEFAULT_REQUEST_TIMEOUT): ElicitationResult<O> {
        if (server.clientCapabilities?.elicitation == null) return ElicitationResult.NotSupported

        val responseSerializer = json.serializersModule.serializer<O>()
        val responseSchema = JsonSchemaFactory.generateSchema<O>(json.serializersModule)
        val result = server.createElicitation(message, responseSchema.toRequestedSchema(), RequestOptions(null, timeout))
        return when (result.action) {
            CreateElicitationResult.Action.accept -> ElicitationResult.Accept(json.decodeFromJsonElement(responseSerializer, result.content ?: JsonNull))
            CreateElicitationResult.Action.decline -> ElicitationResult.Decline
            CreateElicitationResult.Action.cancel -> ElicitationResult.Cancel
        }
    }

    suspend inline fun elicitUnit(message: String, timeout: Duration = DEFAULT_REQUEST_TIMEOUT): ElicitationResult<Unit> {
        if (server.clientCapabilities?.elicitation == null) return ElicitationResult.NotSupported

        val result = server.createElicitation(message, CreateElicitationRequest.RequestedSchema(), RequestOptions(null, timeout))
        return when (result.action) {
            CreateElicitationResult.Action.accept -> ElicitationResult.Accept(Unit)
            CreateElicitationResult.Action.decline -> ElicitationResult.Decline
            CreateElicitationResult.Action.cancel -> ElicitationResult.Cancel
        }
    }

    private val progressToken: RequestId? = requestWithMeta._meta["progressToken"]?.jsonPrimitive?.let {
        it.longOrNull?.let { RequestId.NumberId(it) } ?: it.contentOrNull?.let { RequestId.StringId(it) }
    }

    protected open val disableSampling: Boolean
        get() = System.getProperty("gradle.mcp.test.disableSampling") == "true"

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val progressReporter: ProgressReporter by lazy {
        val flow = MutableSharedFlow<Triple<Double, Double?, String?>>(10, extraBufferCapacity = 50, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        scope.launch(Dispatchers.Default) {
            val transformed = flow.transformLatest {
                val (p, t, msg) = it
                while (currentCoroutineContext().isActive) {
                    repeat(4) {
                        val suffix = if (it == 0) "" else ".".repeat(it)
                        emit(Triple(p, t, msg + suffix))
                        delay(250)
                    }
                }
            }

            val sampled = if (disableSampling) transformed else transformed.sample(100)

            sampled.collectLatest {
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
                    ProgressNotification.Params(
                        progress,
                        progressToken,
                        total,
                        message
                    )
                )
            )
        }
    }

    init {
        scope.launch(Dispatchers.IO) {
            notificationQueue.collect { notification ->
                sendNotification(notification)
            }
        }
    }

    override fun close() {
        scope.cancel()
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

fun CompiledJsonSchemaData.toRequestedSchema(): CreateElicitationRequest.RequestedSchema {
    val obj = json.toKotlinxSerialization().jsonObject

    if (obj["type"]?.jsonPrimitive?.contentOrNull != "object") {
        error("Object schema expected")
    }

    return CreateElicitationRequest.RequestedSchema(
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

    is JsonObject -> {
        val props = properties.mapValues { it.value.toKotlinxSerialization() }.toMutableMap()
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