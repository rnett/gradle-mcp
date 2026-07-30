package dev.rnett.gradle.mcp.e2e

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.mcp.ToolCallResult
import dev.rnett.gradle.mcp.mcp.closeServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

class SseCancellationE2ETest {

    @Serializable
    private data class EmptyArgs(val unused: String? = null)

    @Test
    fun `SSE cancellation is cooperative and suppresses the normal response`() = runTest(timeout = 45.seconds) {
        val handlerStarted = CompletableDeferred<Unit>()
        val handlerCancelled = CompletableDeferred<Unit>()
        val normalResponse = CompletableDeferred<Unit>()
        val component = object : McpServerComponent("sse-test", "sse-test") {
            init {
                tool<EmptyArgs, String>("sse-slow", "waits for cancellation") { _, _ ->
                handlerStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    handlerCancelled.complete(Unit)
                }
            }
                tool<EmptyArgs, String>("sse-quick", "returns immediately") { _, _ -> ToolCallResult("quick") }
            }
        }
        val server = Server(
            Implementation("sse-test-server", "1.0"),
            ServerOptions(
                capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
                enforceStrictCapabilities = false
            )
        ).also { component.register(it, DI.json) }
        val port = Random.nextInt(6601, 6900)
        val ktorServer = embeddedServer(Netty, port = port) {
            mcp { server }
        }
        val httpClient = HttpClient(CIO) { install(SSE) }
        val client = Client(Implementation("sse-test-client", "1.0"), ClientOptions())
        val transport = SseClientTransport(httpClient, "http://localhost:$port")

        try {
            ktorServer.start(wait = false)
            withContext(Dispatchers.Default) {
                withTimeout(10.seconds) { client.connect(transport) }
            }

            val slowCall = backgroundScope.async(Dispatchers.Default) {
                client.callTool("sse-slow", emptyMap()).also { normalResponse.complete(Unit) }
            }
            withContext(Dispatchers.Default) {
                withTimeout(10.seconds) { handlerStarted.await() }
            }

            slowCall.cancelAndJoin()
            withContext(Dispatchers.Default) {
                withTimeout(10.seconds) { handlerCancelled.await() }
            }
            assertFalse(normalResponse.isCompleted)

            val quick = withContext(Dispatchers.Default) {
                withTimeout(10.seconds) { client.callTool("sse-quick", emptyMap()) }
            }
            assertEquals("quick", (quick?.content?.single() as TextContent).text)
        } finally {
            client.close()
            closeServer(server, listOf(component))
            transport.close()
            httpClient.close()
            ktorServer.stop(1_000, 5_000)
        }
    }
}
