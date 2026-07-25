package dev.rnett.gradle.mcp.e2e

import dev.rnett.gradle.mcp.Application
import dev.rnett.gradle.mcp.Transport
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.random.Random
import kotlin.test.assertTrue
import kotlin.test.fail

class StreamableHttpStartupE2ETest {

    @AfterEach
    fun cleanup() {
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    suspend fun `application starts with streamable-http transport and accepts JSON-RPC initialize`() {
        coroutineScope {
            val port = Random.nextInt(6300, 6600)

            System.setProperty("ktor.deployment.port", port.toString())
            System.setProperty("gradle.maxConnections", "4")
            System.setProperty("gradle.ttl", "PT5M")
            System.setProperty("gradle.allowPublicScansPublishing", "true")

            val application = Application(arrayOf("$$-test-nowait"), Transport.StreamableHttp())
            application.start(false)

            val httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()

            // The MCP SDK's mcpStreamableHttp() plugin exposes its endpoint at /mcp
            // and requires Accept headers for both application/json and text/event-stream
            try {
                val jsonRpcBody = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0"}}}"""

                val req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$port/mcp"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json,text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRpcBody))
                    .build()

                val response: HttpResponse<String> = httpClient.send(req, HttpResponse.BodyHandlers.ofString())

                assertTrue(response.statusCode() == 200, "Server responded with OK (status: ${response.statusCode()}, body: ${response.body()})")

                val body = response.body()
                assertTrue(body.contains("\"result\""), "Initialize result should be present (response: $body)")
            } catch (e: Exception) {
                fail("Server did not start properly: ${e.message}", e)
            } finally {
                application.stop()
            }
        }
    }
}
