package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.tools.GradleProjectRootInput
import dev.rnett.gradle.mcp.tools.resolve
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
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
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

class McpSdkIntegrationTest {

    @Serializable
    private data class EmptyArgs(val unused: String? = null)

    @Serializable
    private data class StructuredResult(val value: String)

    @Serializable
    private data class RequiredArgs(val value: String)

    private fun server(): Server = Server(
        Implementation("test-server", "1.0"),
        ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
            enforceStrictCapabilities = false
        )
    )

    private fun client(name: String): Client = Client(
        Implementation(name, "1.0"),
        ClientOptions()
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
            init {
                tool<EmptyArgs, String>("slow", "waits for cancellation") { _, _ ->
                slowStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    slowCancelled.complete(Unit)
                }
            }
            tool<EmptyArgs, String>("quick", "returns immediately") { _, _ -> ToolCallResult("quick") }
            }
        }
        val server = server().also { component.register(it, DI.json) }
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
    fun `client without roots capability resolves an explicit project root`() = runTest(timeout = 30.seconds) {
        val component = object : McpServerComponent("no-roots", "no-roots") {
            init {
                tool<EmptyArgs, String>("resolve-root", "resolves an explicit root") { _, _ ->
                ToolCallResult(GradleProjectRootInput(System.getProperty("user.dir")).resolve().projectRoot)
            }
            }
        }
        val server = server().also { component.register(it, DI.json) }
        val client = client("no-roots-client")
        val transports = ChannelTransport.createLinkedPair()

        try {
            withContext(Dispatchers.Default) {
                server.createSession(transports.serverTransport)
                client.connect(transports.clientTransport)
            }

            val result = withContext(Dispatchers.Default) { client.callTool("resolve-root", emptyMap()) }
            assertFalse(result.isError == true, "Explicit root resolution must not require roots capability")
            kotlin.test.assertTrue(text(result).isNotBlank())
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
            init {
                tool<EmptyArgs, String>("marked-error", "returns marked content") { _, _ ->
                ToolCallResult("expected content", isError = true)
            }
            }
        }
        val server = server().also { component.register(it, DI.json) }
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
    fun `adapter converts every output kind and tool failure path`() = runTest(timeout = 30.seconds) {
        val component = object : McpServerComponent("conversion", "conversion") {
            init {
                tool<EmptyArgs, String>("string", "string") { _, _ -> ToolCallResult("text") }
            tool<RequiredArgs, String>("required", "required") { args, _ -> ToolCallResult(args.value) }
            tool<EmptyArgs, Unit>("unit", "unit") { _, _ -> ToolCallResult(Unit) }
            tool<EmptyArgs, String?>("null", "null") { _, _ -> ToolCallResult(null) }
            tool<EmptyArgs, StructuredResult>("structured", "structured") { _, _ ->
                ToolCallResult(StructuredResult("structured"))
            }
            tool<EmptyArgs, io.modelcontextprotocol.kotlin.sdk.types.CallToolResult>("direct-error", "direct") { _, _ ->
                ToolCallResult(
                    io.modelcontextprotocol.kotlin.sdk.types.CallToolResult(
                        listOf(TextContent("direct")),
                        isError = true
                    )
                )
            }
            tool<EmptyArgs, io.modelcontextprotocol.kotlin.sdk.types.CallToolResult>("wrapper-error", "direct") { _, _ ->
                ToolCallResult(
                    io.modelcontextprotocol.kotlin.sdk.types.CallToolResult(listOf(TextContent("wrapper"))),
                    isError = true
                )
            }
            tool<EmptyArgs, String>("exception", "exception") { _, _ -> error("expected failure") }
            }
        }
        val server = server().also { component.register(it, DI.json) }
        val client = client("conversion-client")
        val transports = ChannelTransport.createLinkedPair()

        try {
            withContext(Dispatchers.Default) {
                server.createSession(transports.serverTransport)
                client.connect(transports.clientTransport)
            }

            suspend fun call(name: String, arguments: Map<String, Any?> = emptyMap()) =
                withContext(Dispatchers.Default) { client.callTool(name, arguments) }

            assertEquals("text", text(call("string")))
            assertEquals(emptyList(), call("unit").content)
            assertEquals(emptyList(), call("null").content)

            val structured = call("structured")
            assertEquals(JsonPrimitive("structured"), structured.structuredContent?.get("value"))
            assertEquals(true, call("direct-error").isError)
            assertEquals(true, call("wrapper-error").isError)

            val decodeFailure = call("required")
            assertEquals(true, decodeFailure.isError)
            kotlin.test.assertContains(text(decodeFailure), "Error executing tool required")

            val exception = call("exception")
            assertEquals(true, exception.isError)
            kotlin.test.assertContains(text(exception), "expected failure")
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
    fun `tool declarations preserve registration order`() = runTest(timeout = 30.seconds) {
        val component = object : McpServerComponent("ordered", "ordered") {
            init {
                tool<EmptyArgs, String>("first", "first") { _, _ -> ToolCallResult("first") }
                tool<EmptyArgs, String>("second", "second") { _, _ -> ToolCallResult("second") }
            }
        }
        val server = server().also { component.register(it, DI.json) }
        val client = client("ordered-client")
        val transports = ChannelTransport.createLinkedPair()

        try {
            withContext(Dispatchers.Default) {
                server.createSession(transports.serverTransport)
                client.connect(transports.clientTransport)
            }

            val tools = withContext(Dispatchers.Default) { client.listTools() }
            assertEquals(listOf("first", "second"), tools.tools.map { it.name })
        } finally {
            client.close()
            closeServer(server, listOf(component))
            transports.clientTransport.close()
            transports.serverTransport.close()
        }
    }

    @Test
    fun `closeServer initiates handler cancellation observed through a separate signal`() = runTest(timeout = 30.seconds) {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val component = object : McpServerComponent("active", "active") {
            init {
                tool<EmptyArgs, String>("active-slow", "waits") { _, _ ->
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            }
        }
        val server = server().also { component.register(it, DI.json) }
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
