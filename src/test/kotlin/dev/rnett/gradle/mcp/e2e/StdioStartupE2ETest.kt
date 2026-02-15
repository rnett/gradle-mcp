package dev.rnett.gradle.mcp.e2e

import dev.rnett.gradle.mcp.Application
import dev.rnett.gradle.mcp.Transport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.koin.core.context.stopKoin
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class StdioStartupE2ETest {

    @AfterTest
    fun cleanup() {
        stopKoin()
    }

    @Test
    fun `application starts with stdio transport and client can initialize`() = runBlocking {
        withTimeout(10.seconds) {
            val serverInReader = PipedInputStream()
            val clientOutWriter = PipedOutputStream(serverInReader)

            val clientInReader = PipedInputStream()
            val serverOutWriter = PipedOutputStream(clientInReader)

            val serverIn = serverInReader.asSource().buffered()
            val serverOut = serverOutWriter.asSink().buffered()

            val clientIn = clientInReader.asSource().buffered()
            val clientOut = clientOutWriter.asSink().buffered()

            // Disable logback status messages to stdout
            System.setProperty("logback.statusListenerClass", "ch.qos.logback.core.status.NopStatusListener")

            val application = Application(emptyArray(), Transport.Stdio(serverIn, serverOut))
            try {
                val serverJob = launch(Dispatchers.IO) {
                    System.setProperty("gradle.maxConnections", "4")
                    System.setProperty("gradle.ttl", "PT5M")
                    System.setProperty("gradle.allowPublicScansPublishing", "true")

                    application.start()
                }

                val client = Client(
                    Implementation("test-client", "1.0"),
                    ClientOptions()
                )

                val transport = StdioClientTransport(clientIn, clientOut)
                client.connect(transport)

                val tools = client.listTools()
                assertTrue(tools.tools.isNotEmpty(), "Tools should not be empty")

                client.close()
                application.stop()
                serverJob.cancel()
            } finally {
                // pipes will be closed by transports/client
            }
        }
    }
}
