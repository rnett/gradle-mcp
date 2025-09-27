package dev.rnett.gradle.mcp.mcp

import io.modelcontextprotocol.kotlin.sdk.EmptyRequestResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.Notification
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions

class McpServer(serverInfo: Implementation, options: ServerOptions) : Server(serverInfo, options) {
    private var logLevel: LoggingLevel = LoggingLevel.info

    init {
        setRequestHandler<LoggingMessageNotification.SetLevelRequest>(Method.Defined.LoggingSetLevel) { it, _ ->
            logLevel = it.level
            EmptyRequestResult()
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