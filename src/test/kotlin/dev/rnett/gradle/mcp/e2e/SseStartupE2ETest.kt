package dev.rnett.gradle.mcp.e2e

import dev.rnett.gradle.mcp.Application
import dev.rnett.gradle.mcp.Transport
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import kotlin.test.AfterTest
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SseStartupE2ETest {

    @AfterTest
    fun cleanup() {
//        stopKoin()
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    suspend fun `application starts with sse transport and client can initialize`() {
        coroutineScope {
            val port = 47814

            System.setProperty("ktor.deployment.port", port.toString())
            System.setProperty("gradle.maxConnections", "4")
            System.setProperty("gradle.ttl", "PT5M")
            System.setProperty("gradle.allowPublicScansPublishing", "true")

            val application = Application(arrayOf("$$-test-nowait"), Transport.Sse())
            launch {
                application.start()
            }

            // Wait for server to start
            delay(2.seconds)

            val httpClient = HttpClient(CIO) {
                install(SSE)
            }
            val client = Client(
                Implementation("test-client", "1.0"),
                ClientOptions()
            )

            val transport = SseClientTransport(httpClient, "http://localhost:$port/sse")
            try {
                client.connect(transport)

                val tools = client.listTools()
                assertTrue(tools.tools.isNotEmpty(), "Tools should not be empty")
            } finally {
                application.stop()
                httpClient.close()
            }
        }
    }
}
