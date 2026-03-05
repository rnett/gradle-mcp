package dev.rnett.gradle.mcp.mcp.fixtures

import dev.rnett.gradle.mcp.mcp.McpServer
import dev.rnett.gradle.mcp.repl.ReplManager
import io.modelcontextprotocol.kotlin.sdk.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
        elicitation = kotlinx.serialization.json.buildJsonObject { }.takeIf { clientSupportsElicitation }
    ),
    private val koinModules: List<Module> = emptyList()
) {
    private val LOGGER = LoggerFactory.getLogger(McpServerFixture::class.java)

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, it ->
        LOGGER.error("Exception in fixture scope", it)
    })

    private val transports = ChannelBasedInMemoryTransport.createLinkedPair(scope)

    val koinApp = koinApplication {
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
            server.onInitialized {
                serverStarted.complete(Unit)
            }
            server.connect(transports.second)
        }
        scope.launch {
            client.connect(transports.first)
        }
        serverStarted.await()
    }

    /**
     * Force the server to believe the client has configured roots. Uses reflection to set the private MutableStateFlow.
     */
    fun setServerRoots(vararg roots: Root) {
        val field = server.javaClass.getDeclaredField("_roots")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(server) as MutableStateFlow<Set<Root>?>
        flow.value = roots.toSet()
    }

    suspend fun close() {
        runCatching { client.close() }
        runCatching { server.close() }
        // Allow onClose hooks and OS handles to settle, especially on Windows
        kotlinx.coroutines.delay(250)
        koin.get<ReplManager>().close()
        koinApp.close()
        scope.cancel("Test cleanup")
    }
}
