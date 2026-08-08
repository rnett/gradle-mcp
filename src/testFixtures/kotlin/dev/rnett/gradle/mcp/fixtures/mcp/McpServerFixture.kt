package dev.rnett.gradle.mcp.fixtures.mcp

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.tools.latestStableGradleVersionInstructionsLine
import dev.rnett.gradle.mcp.mcp.closeServer
import dev.rnett.gradle.mcp.runCatchingExceptCancellation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Notification
import io.modelcontextprotocol.kotlin.sdk.types.Request
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.slf4j.LoggerFactory

/**
 * Keeps SDK request timeouts on real time when fixture calls originate from [kotlinx.coroutines.test.runTest].
 * Caller cancellation still propagates through [withContext] to the SDK request and its peer notification.
 */
class McpFixtureClient internal constructor(private val delegate: Client) {
    suspend fun callTool(
        name: String,
        arguments: Map<String, Any?>,
        meta: Map<String, Any?> = emptyMap(),
        options: RequestOptions? = null,
    ): CallToolResult = withContext(Dispatchers.Default) {
        delegate.callTool(name, arguments, meta, options)
    }

    suspend fun callTool(request: CallToolRequest, options: RequestOptions? = null): CallToolResult =
        withContext(Dispatchers.Default) {
            delegate.callTool(request, options)
        }

    suspend fun listTools(
        request: ListToolsRequest = ListToolsRequest(),
        options: RequestOptions? = null,
    ): ListToolsResult = withContext(Dispatchers.Default) {
        delegate.listTools(request, options)
    }

    suspend fun <T : RequestResult> request(request: Request, options: RequestOptions? = null): T =
        withContext(Dispatchers.Default) {
            delegate.request(request, options)
        }

    fun <T : Notification> setNotificationHandler(method: Method, handler: (T) -> Deferred<Unit>) {
        delegate.setNotificationHandler(method, handler)
    }
}

/**
 * Test fixture that connects a real SDK server and client through a [ChannelTransport] pair.
 */
class McpServerFixture(
    private val clientCapabilities: ClientCapabilities = ClientCapabilities(),
    private val koinModules: List<Module> = emptyList(),
    /**
     * Component classes whose lifecycle is owned outside this fixture (e.g. a class-scoped
     * [dev.rnett.gradle.mcp.gradle.GradleProvider] shared across test methods). [close] skips
     * these components when closing the server, so the owning test class closes them exactly once
     * at class teardown instead of per method. Defaults to empty = current per-method close.
     */
    private val excludeFromClose: Set<KClass<out McpServerComponent>> = emptySet()
) {
    private val logger = LoggerFactory.getLogger(McpServerFixture::class.java)

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
        logger.error("Exception in fixture scope", throwable)
    })

    private val transports = ChannelTransport.createLinkedPair()

    val koinApp = koinApplication {
        allowOverride(true)
        modules(koinModules)
    }

    val koin = koinApp.koin
    val components = koin.get<List<McpServerComponent>>()
    val server = DI.createServer(
        koin.get(),
        components,
        latestGradleVersionInstructions = koin.getOrNull<GradleVersionService>()?.let { versionService ->
            runBlocking { latestStableGradleVersionInstructionsLine(versionService.resolveLatestStable()) }
        } ?: "The latest Gradle version is ${BuildConfig.GRADLE_VERSION}."
    )
    private val sdkClient = Client(
        Implementation("gradle-mcp-test-client", "test"),
        ClientOptions(clientCapabilities)
    )
    val client = McpFixtureClient(sdkClient)

    suspend fun start() = withContext(Dispatchers.Default) {
        server.createSession(transports.serverTransport)
        sdkClient.connect(transports.clientTransport)
    }

    suspend fun close() {
        runCatchingExceptCancellation { sdkClient.close() }
        runCatchingExceptCancellation {
            closeServer(server, components.filterNot { it::class in excludeFromClose })
        }
        runCatchingExceptCancellation { transports.clientTransport.close() }
        runCatchingExceptCancellation { transports.serverTransport.close() }
        koinApp.close()
        scope.cancel("Test cleanup")
        scope.coroutineContext[Job]?.join()
    }
}
