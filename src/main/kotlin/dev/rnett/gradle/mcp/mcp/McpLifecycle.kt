package dev.rnett.gradle.mcp.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

private val lifecycleLogger = LoggerFactory.getLogger("dev.rnett.gradle.mcp.mcp.McpLifecycle")

suspend fun closeServer(server: Server, components: List<McpServerComponent>) {
    var serverFailure: Exception? = null
    try {
        server.close()
    } catch (e: Exception) {
        serverFailure = e
    }

    var cancellation: CancellationException? = null
    components.forEach { component ->
        try {
            component.close()
        } catch (e: CancellationException) {
            if (cancellation == null) cancellation = e
        } catch (e: Exception) {
            lifecycleLogger.warn("Failed to close MCP component {}", component.name, e)
        }
    }

    serverFailure?.let { throw it }
    cancellation?.let { throw it }
}
