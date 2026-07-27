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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class ToolCallRequestId(val value: io.modelcontextprotocol.kotlin.sdk.types.RequestId) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<ToolCallRequestId>
    override val key: CoroutineContext.Key<*> = Key
}

/**
 * Grace period [McpServer.shutdown] waits for the tool-execution scope to drain before abandoning stuck work. Long
 * enough for cooperative teardown of well-behaved tools, short enough not to hang CI (or a `runTest` cleanup) on a
 * tool body that ignores cancellation.
 */
private const val SHUTDOWN_GRACE_MS = 5_000L

class McpServer(
    serverInfo: Implementation,
    options: ServerOptions,
    val json: Json,
    private val components: List<McpServerComponent> = emptyList(),
) : Server(serverInfo, options) {
    private val LOGGER = LoggerFactory.getLogger(McpServer::class.java)

    private val closed = AtomicBoolean(false)

    // The scope is intentionally NOT a child of the SDK session scope. Cancellation decoupling:
    // cancelling an MCP request via notifications/cancelled must not terminate the entire server session.
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { ctx, e ->
        LOGGER.error("Error in MCP server job {}", ctx[CoroutineName]?.name ?: "unnamed", e)
    })
    private val _roots = MutableStateFlow<Set<Root>?>(null)
    val roots: StateFlow<Set<Root>?> = _roots.asStateFlow()

    /**
     * Sets the roots for testing purposes, bypassing the normal client notification flow.
     * This allows tests to simulate roots being configured without a real client round-trip.
     *
     * Note: This method is intended for testing only.
     */
    fun setRootsForTesting(roots: Set<Root>) {
        _roots.value = roots
    }

    private val activeToolCallJobs = ConcurrentHashMap<io.modelcontextprotocol.kotlin.sdk.types.RequestId, Job>()

    @PublishedApi internal fun registerToolCallJob(requestId: io.modelcontextprotocol.kotlin.sdk.types.RequestId?, job: Job) { if (requestId != null) activeToolCallJobs[requestId] = job }
    @PublishedApi internal fun unregisterToolCallJob(requestId: io.modelcontextprotocol.kotlin.sdk.types.RequestId?) { if (requestId != null) activeToolCallJobs.remove(requestId) }

    /**
     * Wraps a transport to inject [ToolCallRequestId] into the coroutine context for tool-call messages, so tool
     * handlers can read the raw JSON-RPC request id and key their cancellation jobs by it.
     *
     * Each inbound message is dispatched on [scope], decoupled from the transport's receive loop, so a long-running
     * or client-cancelled tool call neither blocks the message stream nor tears down the session (see [scope]).
     *
     * NOTE (SDK gap): the SDK does not expose the JSON-RPC request id to tool handlers — `Server.addTool` receives
     * only the deserialized `CallToolRequest` and `RequestHandlerExtra` is empty — so this context injection is the
     * only way to key cancellation. It is applied in [connect], which only the stdio transport goes through. SSE and
     * StreamableHttp sessions are created inside the SDK's Ktor extensions (`Application.mcp` / `mcpStreamableHttp`)
     * with no transport-interception hook, so they bypass this wrapper and client-driven cancellation does not reach
     * them. Cancellation is therefore stdio-only until the SDK exposes a request id or a transport hook.
     */
    private fun wrapTransport(transport: Transport): Transport {
        return object : Transport by transport {
            override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
                transport.onMessage { message ->
                    scope.launch {
                        if (message is JSONRPCRequest && message.method == Method.Defined.ToolsCall.value) {
                            withContext(ToolCallRequestId(message.id)) { block(message) }
                        } else {
                            block(message)
                        }
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
            CompletableDeferred(Unit)
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
            // The SDK invokes this synchronously from Server.close(), so it must stay cheap and non-blocking.
            // The suspending cleanup (closing components and joining the tool scope) lives in shutdown(), which
            // the fixture/Application call and await. Cancelling the scope here is synchronous and idempotent.
            activeToolCallJobs.clear()
            scope.cancel("Server closing")
        }
    }

    /**
     * Deterministic, suspending shutdown — the SINGLE teardown entry point. Closes the SDK sessions and notification
     * service (which fires the synchronous [onClose] cleanup), then closes the components, and finally cancels AND
     * joins the tool-execution [scope] so no orphaned work outlives the server. Idempotent — callers (fixture,
     * Application) must await it. The synchronous [onClose] callback performs only cheap, non-blocking state cleanup
     * (clearing jobs + cancelling the scope) and must NOT be relied on to close components.
     */
    suspend fun shutdown() {
        if (!closed.compareAndSet(false, true)) return
        // Server.close() is final; it closes all sessions + the notification service and fires the onClose callback,
        // which cancels the tool scope BEFORE components are closed. This ordering is deliberate: closing components
        // unblocks resource-bound tool bodies (running builds / REPL sessions) so the bounded join below can complete.
        // Do not reorder.
        close()
        components.forEach { runCatching { it.close() } }
        scope.cancel("Server closing")
        // Bounded join: a non-cooperatively-cancellable tool body must not hang shutdown forever — which, inside
        // runTest { server.close() }, reproduces the exact UncompletedCoroutinesError this teardown avoids. After the
        // grace period we log a warning and abandon the stuck work rather than block.
        val joined = withTimeoutOrNull(SHUTDOWN_GRACE_MS) { scope.coroutineContext[Job]?.join() }
        if (joined == null) {
            LOGGER.warn(
                "MCP server shutdown timed out after {}ms waiting for the tool-execution scope to drain; abandoning stuck work",
                SHUTDOWN_GRACE_MS
            )
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
