package dev.rnett.gradle.mcp.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotification
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Root
import io.modelcontextprotocol.kotlin.sdk.types.RootsListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerNotification
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class ToolCallRequestId(val value: io.modelcontextprotocol.kotlin.sdk.types.RequestId) : CoroutineContext.Element {
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
    // The scope is intentionally NOT a child of the SDK session scope. Cancellation decoupling:
    // cancelling an MCP request via notifications/cancelled must not terminate the entire server session.
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { ctx, e ->
        LOGGER.error("Error in MCP server job {}", ctx[CoroutineName]?.name ?: "unnamed", e)
    })
    private val _roots = MutableStateFlow<Set<Root>?>(null)
    val roots: StateFlow<Set<Root>?> = _roots.asStateFlow()
    private val activeToolCallJobs = ConcurrentHashMap<io.modelcontextprotocol.kotlin.sdk.types.RequestId, Job>()

    @PublishedApi internal fun registerToolCallJob(requestId: io.modelcontextprotocol.kotlin.sdk.types.RequestId?, job: Job) { if (requestId != null) activeToolCallJobs[requestId] = job }
    @PublishedApi internal fun unregisterToolCallJob(requestId: io.modelcontextprotocol.kotlin.sdk.types.RequestId?) { if (requestId != null) activeToolCallJobs.remove(requestId) }

    /**
     * Wraps a transport to inject [ToolCallRequestId] into the coroutine context for tool call messages.
     * This allows tool handlers to access the raw JSON-RPC request ID for cancellation support.
     */
    private fun wrapTransport(transport: Transport): Transport {
        return object : Transport by transport {
            override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
                transport.onMessage { message ->
                    scope.launch {
                        if (message is JSONRPCRequest && message.method == Method.Defined.ToolsCall.value) {
                            withContext(ToolCallRequestId(message.id)) { block(message) }
                        } else block(message)
                    }
                }
            }
        }
    }

    /**
     * Creates a new session with the given transport, wrapping it for tool call ID injection
     * and setting up notification handlers for cancellation and roots list changes.
     */
    suspend fun connect(transport: Transport): ServerSession {
        val session = createSession(wrapTransport(transport))
        setupSessionHandlers(session)
        return session
    }

    private fun setupSessionHandlers(session: ServerSession) {
        session.setNotificationHandler<RootsListChangedNotification>(Method.Defined.NotificationsRootsListChanged) {
            scope.async { updateRootsList() }
        }
        session.setNotificationHandler<CancelledNotification>(Method.Defined.NotificationsCancelled) {
            activeToolCallJobs[it.requestId]?.cancel(CancellationException("Tool call cancelled by client: ${it.reason}"))
            scope.async {}
        }
    }

    init {
        // Note: SSE sessions bypass connect() and are created directly via createSession() by the SDK.
        // This init callback runs for every session (including those from SSE), so we set up handlers on all existing sessions.
        onConnect {
            // Set up notification handlers on all sessions (idempotent — replaces with same handler)
            sessions.values.forEach { session -> setupSessionHandlers(session) }
        }
        onClose {
            kotlinx.coroutines.runBlocking {
                components.forEach { it.close() }
                components.mapNotNull {
                    when (it) {
                        is dev.rnett.gradle.mcp.tools.ReplTools -> it.replManager
                        is dev.rnett.gradle.mcp.tools.GradleExecutionTools -> it.gradleProvider
                        is dev.rnett.gradle.mcp.tools.GradleBuildLookupTools -> it.buildResults
                        else -> null
                    }
                }.distinct().forEach { it.close() }
            }
            scope.cancel("Server closing")
        }
    }

    private suspend fun updateRootsList() {
        sessions.values.forEach { session ->
            if (session.clientCapabilities?.roots != null) {
                _roots.value = clientConnection(session.sessionId).listRoots().roots.toSet()
            }
        }
    }

    suspend fun sendNotification(notification: ServerNotification) {
        sessions.values.forEach { session ->
            clientConnection(session.sessionId).notification(notification)
        }
    }
}
