package dev.rnett.gradle.mcp.mcp

import io.modelcontextprotocol.kotlin.sdk.CancelledNotification
import io.modelcontextprotocol.kotlin.sdk.EmptyRequestResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.Notification
import io.modelcontextprotocol.kotlin.sdk.RequestId
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.RootsListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Coroutine context element that carries the JSON-RPC request ID of the active tools/call.
 * Injected by [McpServer]'s transport interceptor so tool handlers can register themselves
 * under their exact request ID for precise cancellation matching.
 */
class ToolCallRequestId(val value: RequestId) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<ToolCallRequestId>

    override val key: CoroutineContext.Key<*> = Key
}

class McpServer(
    serverInfo: Implementation,
    options: ServerOptions,
    val json: Json,
    private val components: List<McpServerComponent> = emptyList()
) : Server(serverInfo, options) {
    private val LOGGER = LoggerFactory.getLogger(McpServer::class.java)
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { ctx, e ->
        val name = ctx[CoroutineName]
        LOGGER.error("Error in MCP server job {}", name?.name ?: "unnamed", e)
    })
    private var logLevel: LoggingLevel = LoggingLevel.info

    private val _roots = MutableStateFlow<Set<Root>?>(null)
    val roots: StateFlow<Set<Root>?> = _roots.asStateFlow()

    // Active tool call jobs keyed by their JSON-RPC request ID.
    // Populated/cleared by registerToolCallJob/unregisterToolCallJob called from McpServerComponent.
    private val activeToolCallJobs = ConcurrentHashMap<RequestId, Job>()

    @PublishedApi
    internal fun registerToolCallJob(requestId: RequestId?, job: Job) {
        if (requestId != null) activeToolCallJobs[requestId] = job
    }

    @PublishedApi
    internal fun unregisterToolCallJob(requestId: RequestId?) {
        if (requestId != null) activeToolCallJobs.remove(requestId)
    }

    // Override connect() to inject the JSON-RPC request ID into the coroutine context for each
    // tools/call message. The addTool handler receives only a deserialized CallToolRequest (no
    // raw ID), so this transport interceptor is the sole point where both are available.
    // Using withContext propagates the ID through the entire call chain to the tool handler.
    override suspend fun connect(transport: Transport) {
        super.connect(object : Transport by transport {
            override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
                transport.onMessage { message ->
                    if (message is JSONRPCRequest && message.method == Method.Defined.ToolsCall.value) {
                        withContext(currentCoroutineContext() + ToolCallRequestId(message.id)) {
                            block(message)
                        }
                    } else {
                        block(message)
                    }
                }
            }
        })
    }

    init {
        setRequestHandler<LoggingMessageNotification.SetLevelRequest>(Method.Defined.LoggingSetLevel) { it, _ ->
            logLevel = it.level
            EmptyRequestResult()
        }
        setNotificationHandler<RootsListChangedNotification>(Method.Defined.NotificationsRootsListChanged) { it ->
            scope.async {
                updateRootsList()
            }
        }
        // When the client cancels a tool call (e.g., user interrupted), cancel the running
        // handler so the SDK's message processing loop is unblocked for the next request.
        setNotificationHandler<CancelledNotification>(Method.Defined.NotificationsCancelled) {
            val requestId = it.params.requestId
            LOGGER.info(
                "Received cancellation notification for request {} (reason: {})",
                requestId,
                it.params.reason
            )
            val job = activeToolCallJobs[requestId]
            if (job != null) {
                job.cancel(CancellationException("Tool call cancelled by client: ${it.params.reason}"))
            } else {
                LOGGER.debug(
                    "Ignoring cancellation for unknown or already-completed request: {}",
                    requestId
                )
            }
            scope.async {}
        }
        onClose {
            // Ensure component shutdown completes before close() returns
            kotlinx.coroutines.runBlocking {
                components.forEach { it.close() }
                components.mapNotNull {
                    when (it) {
                        is dev.rnett.gradle.mcp.tools.ReplTools -> it.replManager
                        is dev.rnett.gradle.mcp.tools.GradleExecutionTools -> it.gradleProvider
                        is dev.rnett.gradle.mcp.tools.GradleBuildLookupTools -> it.buildResults
                        else -> null
                    }
                }.distinct().forEach {
                    it.close()
                }
            }
            scope.cancel("Server closing")
        }
    }

    private suspend fun updateRootsList() {
        if (clientCapabilities?.roots != null) {
            val roots = listRoots()
            _roots.value = roots.roots.toSet()
        }
    }

    /**
     * Sends the notification if it should be sent.  Takes things like the configured log level into account.
     */
    suspend fun sendNotification(notification: Notification) {
        when (notification) {
            is LoggingMessageNotification -> {
                if (notification.params.level < logLevel) {
                    return
                }
            }

            else -> {}
        }
        notification(notification)
    }
}