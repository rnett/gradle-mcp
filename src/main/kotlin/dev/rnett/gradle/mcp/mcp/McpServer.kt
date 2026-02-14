package dev.rnett.gradle.mcp.mcp

import io.modelcontextprotocol.kotlin.sdk.EmptyRequestResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.Notification
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.RootsListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

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
        onClose {
            // Ensure component shutdown completes before close() returns
            kotlinx.coroutines.runBlocking {
                components.forEach { it.close() }
                components.mapNotNull {
                    when (it) {
                        is dev.rnett.gradle.mcp.tools.ReplTools -> it.replManager
                        is dev.rnett.gradle.mcp.tools.GradleIntrospectionTools -> it.gradle
                        is dev.rnett.gradle.mcp.tools.GradleExecutionTools -> it.gradle
                        is dev.rnett.gradle.mcp.tools.BackgroundBuildTools -> it.gradle
                        is dev.rnett.gradle.mcp.tools.GradleBuildLookupTools -> it.buildResults
                        else -> null
                    }
                }.distinct().forEach {
                    if (it is AutoCloseable) it.close()
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