package dev.rnett.gradle.mcp.e2e

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.LatestStableGradleVersion
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.mcp.closeServer
import dev.rnett.gradle.mcp.runCatchingExceptCancellation
import dev.rnett.gradle.mcp.tools.ToolNames
import io.ktor.server.config.MapApplicationConfig
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class StubGradleVersionService(private val resolution: LatestStableGradleVersion) : GradleVersionService {
    val resolveLatestStableCalls = AtomicInteger()

    override suspend fun resolveLatestStable(): LatestStableGradleVersion {
        resolveLatestStableCalls.incrementAndGet()
        return resolution
    }

    override suspend fun resolveVersion(version: String?): String {
        val lower = version?.lowercase()
        return if (version == null || lower == "current" || lower == "latest") {
            resolveLatestStable().version
        } else {
            version
        }
    }
}

class LatestGradleVersionWiringE2ETest {

    private var koinApp: KoinApplication? = null
    private var server: Server? = null
    private var components: List<McpServerComponent>? = null
    private var client: Client? = null
    private var clientTransport: ChannelTransport? = null
    private var serverTransport: ChannelTransport? = null

    private fun config() = MapApplicationConfig(
        "gradle.maxConnections" to "10",
        "gradle.ttl" to "PT5M",
        "gradle.allowPublicScansPublishing" to "true"
    )

    private suspend fun startServer(stub: StubGradleVersionService) {
        val app = koinApplication {
            allowOverride(true)
            modules(
                DI.createModule(config()),
                module { single<GradleVersionService> { stub } }
            )
        }
        koinApp = app
        val koin = app.koin
        val resolvedServer = koin.get<Server>()
        server = resolvedServer
        components = koin.get<List<McpServerComponent>>()

        val transports = ChannelTransport.createLinkedPair()
        clientTransport = transports.clientTransport
        serverTransport = transports.serverTransport
        val sdkClient = Client(Implementation("gradle-mcp-test-client", "test"), ClientOptions())
        client = sdkClient
        resolvedServer.createSession(transports.serverTransport)
        sdkClient.connect(transports.clientTransport)
    }

    @AfterEach
    fun cleanup() {
        runBlocking {
            client?.let { runCatchingExceptCancellation { it.close() } }
            if (server != null && components != null) {
                runCatchingExceptCancellation { closeServer(server!!, components!!) }
            }
            clientTransport?.let { runCatchingExceptCancellation { it.close() } }
            serverTransport?.let { runCatchingExceptCancellation { it.close() } }
            koinApp?.close()
        }
    }

    @Test
    fun `fetched live latest stable version is wired into tool descriptions`() = runBlocking {
        val stub = StubGradleVersionService(
            LatestStableGradleVersion("9.99.99", LatestStableGradleVersion.Source.FETCHED_LIVE)
        )
        startServer(stub)

        assertTrue(stub.resolveLatestStableCalls.get() >= 1, "resolveLatestStable should have been called during server wiring")

        val tools = client!!.listTools().tools

        val gradleDocs = tools.first { it.name == ToolNames.GRADLE_DOCS }.description.orEmpty()
        assertTrue(gradleDocs.contains("The latest stable Gradle version is **9.99.99**"))
        assertTrue(gradleDocs.contains("https://services.gradle.org/versions/current"))
        assertFalse(gradleDocs.contains("is reported instead"))

        val gradle = tools.first { it.name == ToolNames.GRADLE }.description.orEmpty()
        assertFalse(gradle.contains("The latest stable Gradle version is **9.99.99**"), "GRADLE description should not contain the fetched live note")
        assertFalse(gradle.contains("https://services.gradle.org/versions/current"), "GRADLE description should not contain the latest version provenance URL")

        val toolsWithVersion = tools.filter { it.description.orEmpty().contains("9.99.99") }.map { it.name }.sorted()
        assertEquals(listOf(ToolNames.GRADLE_DOCS), toolsWithVersion)
    }

    @Test
    fun `bundled fallback latest stable version is wired into tool descriptions`() = runBlocking {
        val stub = StubGradleVersionService(
            LatestStableGradleVersion("8.88.88", LatestStableGradleVersion.Source.BUNDLED_FALLBACK)
        )
        startServer(stub)

        assertTrue(stub.resolveLatestStableCalls.get() >= 1, "resolveLatestStable should have been called during server wiring")

        val tools = client!!.listTools().tools

        val gradleDocs = tools.first { it.name == ToolNames.GRADLE_DOCS }.description.orEmpty()
        val gradle = tools.first { it.name == ToolNames.GRADLE }.description.orEmpty()

        assertTrue(gradleDocs.contains("could not be verified"))
        assertTrue(gradleDocs.contains("newer versions may exist"))
        assertTrue(gradleDocs.contains("8.88.88"))
        assertFalse(gradleDocs.contains("latest stable Gradle version is **"), "Fallback note must not claim the bundled version is the live latest")

        assertFalse(gradle.contains("could not be verified"), "GRADLE description should not contain the bundled fallback note")
        assertFalse(gradle.contains("8.88.88"), "GRADLE description should not contain the bundled fallback version")

        val toolsWithVersion = tools.filter { it.description.orEmpty().contains("8.88.88") }.map { it.name }.sorted()
        assertEquals(listOf(ToolNames.GRADLE_DOCS), toolsWithVersion)
    }
}
