package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.tools.GradleProjectRootInput
import dev.rnett.gradle.mcp.tools.resolve
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Root
import io.modelcontextprotocol.kotlin.sdk.types.McpException
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class McpSdkIntegrationTest {

    @Serializable
    private data class EmptyArgs(val unused: String? = null)

    private fun server(): Server = Server(
        Implementation("test-server", "1.0"),
        ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
            enforceStrictCapabilities = false
        )
    )

    private fun client(name: String, roots: Boolean = false): Client = Client(
        Implementation(name, "1.0"),
        ClientOptions(
            capabilities = ClientCapabilities(
                roots = ClientCapabilities.Roots(listChanged = false).takeIf { roots }
            )
        )
    )

    private suspend fun awaitSignal(signal: CompletableDeferred<Unit>) {
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) { signal.await() }
        }
    }

    private fun text(result: io.modelcontextprotocol.kotlin.sdk.types.CallToolResult?): String =
        (result?.content?.single() as TextContent).text

    @Test
    fun `SDK dispatches concurrent calls and isolates cancellation on one session`() = runTest(timeout = 30.seconds) {
        val slowStarted = CompletableDeferred<Unit>()
        val slowCancelled = CompletableDeferred<Unit>()
        val component = object : McpServerComponent("test", "test") {
            val slow by tool<EmptyArgs, String>("slow", "waits for cancellation") {
                slowStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    slowCancelled.complete(Unit)
                }
            }
            val quick by tool<EmptyArgs, String>("quick", "returns immediately") { "quick" }
        }
        val server = server().apply { add(component, DI.json) }
        val client = client("channel-client")
        val transports = ChannelTransport.createLinkedPair()

        try {
            withContext(Dispatchers.Default) {
                server.createSession(transports.serverTransport)
                client.connect(transports.clientTransport)
            }

            val slowCall = backgroundScope.async(Dispatchers.Default) { client.callTool("slow", emptyMap()) }
            awaitSignal(slowStarted)

            val firstQuick = withContext(Dispatchers.Default) {
                withTimeout(10.seconds) { client.callTool("quick", emptyMap()) }
            }
            assertEquals("quick", text(firstQuick))
            assertFalse(slowCall.isCompleted)

            slowCall.cancelAndJoin()
            awaitSignal(slowCancelled)

            val secondQuick = withContext(Dispatchers.Default) {
                withTimeout(10.seconds) { client.callTool("quick", emptyMap()) }
            }
            assertEquals("quick", text(secondQuick))
        } finally {
            client.close()
            closeServer(server, listOf(component))
            transports.clientTransport.close()
            transports.serverTransport.close()
        }
    }

    @Test
    fun `client roots APIs round trip independently for multiple sessions`() = runTest(timeout = 30.seconds) {
        val component = object : McpServerComponent("roots", "roots") {
            val roots by tool<EmptyArgs, String>("roots", "lists roots") {
                session?.listRoots()?.roots.orEmpty().map { it.name ?: it.uri }.sorted().joinToString(",")
            }
        }
        val server = server().apply { add(component, DI.json) }
        val clientA = client("client-a", roots = true)
        val clientB = client("client-b", roots = true)
        val transportsA = ChannelTransport.createLinkedPair()
        val transportsB = ChannelTransport.createLinkedPair()

        try {
            withContext(Dispatchers.Default) {
                server.createSession(transportsA.serverTransport)
                server.createSession(transportsB.serverTransport)
                clientA.connect(transportsA.clientTransport)
                clientB.connect(transportsB.clientTransport)
            }

            clientA.addRoot("file:///a", "A")
            clientB.addRoots(listOf(Root("file:///b", "B"), Root("file:///temporary", "temporary")))
            assertTrue(clientB.removeRoot("file:///temporary"))
            clientB.addRoots(listOf(Root("file:///c", "C"), Root("file:///d", "D")))
            assertEquals(2, clientB.removeRoots(listOf("file:///c", "file:///d")))

            val rootsA = withContext(Dispatchers.Default) { clientA.callTool("roots", emptyMap()) }
            val rootsB = withContext(Dispatchers.Default) { clientB.callTool("roots", emptyMap()) }
            assertEquals("A", text(rootsA))
            assertEquals("B", text(rootsB))
        } finally {
            clientA.close()
            clientB.close()
            closeServer(server, listOf(component))
            transportsA.clientTransport.close()
            transportsA.serverTransport.close()
            transportsB.clientTransport.close()
            transportsB.serverTransport.close()
        }
    }

    @Test
    fun `client without roots capability resolves an explicit project root`() = runTest(timeout = 30.seconds) {
        val component = object : McpServerComponent("no-roots", "no-roots") {
            val resolveRoot by tool<EmptyArgs, String>("resolve-root", "resolves an explicit root") {
                GradleProjectRootInput(System.getProperty("user.dir")).resolve().projectRoot
            }
        }
        val server = server().apply { add(component, DI.json) }
        val client = client("no-roots-client")
        val transports = ChannelTransport.createLinkedPair()

        try {
            withContext(Dispatchers.Default) {
                server.createSession(transports.serverTransport)
                client.connect(transports.clientTransport)
            }

            val result = withContext(Dispatchers.Default) { client.callTool("resolve-root", emptyMap()) }
            assertFalse(result.isError == true, "Explicit root resolution must not call listRoots without capability")
            assertTrue(text(result).isNotBlank())
        } finally {
            client.close()
            closeServer(server, listOf(component))
            transports.clientTransport.close()
            transports.serverTransport.close()
        }
    }

    @Test
    fun `tool context isError is preserved for converted results`() = runTest(timeout = 30.seconds) {
        val component = object : McpServerComponent("error-result", "error-result") {
            val markedError by tool<EmptyArgs, String>("marked-error", "returns marked content") {
                isError = true
                "expected content"
            }
        }
        val server = server().apply { add(component, DI.json) }
        val client = client("error-result-client")
        val transports = ChannelTransport.createLinkedPair()

        try {
            withContext(Dispatchers.Default) {
                server.createSession(transports.serverTransport)
                client.connect(transports.clientTransport)
            }

            val result = withContext(Dispatchers.Default) { client.callTool("marked-error", emptyMap()) }
            assertEquals(true, result.isError)
            assertEquals("expected content", text(result))
        } finally {
            client.close()
            closeServer(server, listOf(component))
            transports.clientTransport.close()
            transports.serverTransport.close()
        }
    }
    @Test
    fun `closeServer is SDK first sequential best effort and repeat safe`() = runTest(timeout = 30.seconds) {
        val order = CopyOnWriteArrayList<String>()
        val server = server().apply { onClose { order += "server" } }
        fun component(name: String, failure: Boolean = false) = object : McpServerComponent(name, name) {
            override suspend fun close() {
                order += name
                if (failure) error("close failure")
            }
        }
        val components = listOf(component("first"), component("failing", failure = true), component("last"))

        closeServer(server, components)
        closeServer(server, components)

        assertEquals(
            listOf("server", "first", "failing", "last", "server", "first", "failing", "last"),
            order
        )
    }

    @Test
    fun `closeServer initiates handler cancellation observed through a separate signal`() = runTest(timeout = 30.seconds) {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val component = object : McpServerComponent("active", "active") {
            val slow by tool<EmptyArgs, String>("active-slow", "waits") {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
        val server = server().apply { add(component, DI.json) }
        val client = client("close-client")
        val transports = ChannelTransport.createLinkedPair()

        try {
            withContext(Dispatchers.Default) {
                server.createSession(transports.serverTransport)
                client.connect(transports.clientTransport)
            }
            val call = backgroundScope.async(Dispatchers.Default) {
                try {
                    client.callTool("active-slow", emptyMap())
                    null
                } catch (e: McpException) {
                    e
                }
            }
            awaitSignal(started)

            closeServer(server, listOf(component))
            awaitSignal(cancelled)
            assertEquals("Connection closed", call.await()?.message)
        } finally {
            client.close()
            transports.clientTransport.close()
            transports.serverTransport.close()
        }
    }
}
