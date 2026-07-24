package dev.rnett.gradle.mcp.fixtures.mcp

import dev.rnett.gradle.mcp.mcp.McpServer
import dev.rnett.gradle.mcp.repl.ReplManager
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Root
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.slf4j.LoggerFactory

/**
 * Test fixture that starts a real MCP server and a real MCP client connected over in-memory STDIO streams.
 */
class McpServerFixture(
    private val clientSupportsElicitation: Boolean = true,
    private val clientCapabilities: ClientCapabilities = ClientCapabilities(
        elicitation = ClientCapabilities.Elicitation().takeIf { clientSupportsElicitation }
    ),
    private val koinModules: List<Module> = emptyList()
) {
    private val logger = LoggerFactory.getLogger(McpServerFixture::class.java)

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, it ->
        logger.error("Exception in fixture scope", it)
    })

    private val transports = ChannelTransport.createLinkedPair()

    val koinApp = koinApplication {
        allowOverride(true)
        modules(koinModules)
    }

    val koin = koinApp.koin

    val server = koin.get<McpServer>()

    val client = Client(
        Implementation("gradle-mcp-test-client", "test"),
        ClientOptions(clientCapabilities)
    )

    suspend fun start() {
        val serverStarted = CompletableDeferred<Unit>()
        scope.launch {
            val session = server.connect(transports.serverTransport)
            session.onInitialized {
                serverStarted.complete(Unit)
            }
        }
        scope.launch {
            client.connect(transports.clientTransport)
        }
        serverStarted.await()
    }

    /**
     * Force the server to believe the client has configured roots.
     */
    fun setServerRoots(vararg roots: Root) {
        server.setRootsForTesting(roots.toSet())
    }

    suspend fun close() {
        runCatching { client.close() }
        runCatching { server.close() }
        // Explicitly close transports to ensure proper cleanup
        runCatching { transports.clientTransport.close() }
        runCatching { transports.serverTransport.close() }
        // Allow onClose hooks and OS handles to settle, especially on Windows
        delay(50)
        koin.get<ReplManager>().close()
        koinApp.close()
        scope.cancel("Test cleanup")
    }
}