package dev.rnett.gradle.mcp.fixtures.mcp

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class McpServerFixtureTest {

    private class TrackingComponent : McpServerComponent("tracking", "Tracks fixture lifecycle identity") {
        var registeredServer: Server? = null
        var closeCount = 0

        override fun register(server: Server, json: Json) {
            registeredServer = server
            super.register(server, json)
        }

        override suspend fun close() {
            closeCount++
        }
    }

    @Test
    fun `fixture closes the exact factory component instance registered with its server`() = runTest(timeout = 30.seconds) {
        val resolvedComponents = mutableListOf<TrackingComponent>()
        val testModule = module {
            single { DI.json }
            factory<List<McpServerComponent>> {
                listOf(TrackingComponent().also(resolvedComponents::add))
            }
        }
        val fixture = McpServerFixture(koinModules = listOf(testModule))
        assertNull(fixture.koin.getOrNull<Server>())

        try {
            fixture.start()
            val registeredComponent = resolvedComponents.single { it.registeredServer === fixture.server }
            assertSame(registeredComponent, fixture.components.single())
        } finally {
            fixture.close()
        }

        val registeredComponent = resolvedComponents.single { it.registeredServer === fixture.server }
        assertEquals(1, resolvedComponents.size)
        assertEquals(1, registeredComponent.closeCount)
        assertTrue(fixture.scope.coroutineContext[kotlinx.coroutines.Job]?.isCompleted == true)
    }

    @Test
    fun `fixture client forwards its default request timeout at every site and caller options win`() =
        runTest(timeout = 30.seconds) {
            val delegate = mockk<Client>(relaxed = true)
            val client = McpFixtureClient(delegate)

            val nameSlot = slot<RequestOptions>()
            client.callTool("tool", emptyMap())
            coVerify { delegate.callTool("tool", emptyMap(), any(), capture(nameSlot)) }
            assertEquals(McpFixtureClient.FIXTURE_REQUEST_TIMEOUT, nameSlot.captured.timeout)

            val callRequest = CallToolRequest(CallToolRequestParams(name = "tool"))
            val callSlot = slot<RequestOptions>()
            client.callTool(callRequest)
            coVerify { delegate.callTool(callRequest, capture(callSlot)) }
            assertEquals(McpFixtureClient.FIXTURE_REQUEST_TIMEOUT, callSlot.captured.timeout)

            val listRequest = ListToolsRequest()
            val listSlot = slot<RequestOptions>()
            client.listTools(listRequest)
            coVerify { delegate.listTools(listRequest, capture(listSlot)) }
            assertEquals(McpFixtureClient.FIXTURE_REQUEST_TIMEOUT, listSlot.captured.timeout)

            val rawRequest = ListToolsRequest()
            val rawSlot = slot<RequestOptions>()
            client.request<CallToolResult>(rawRequest)
            coVerify { delegate.request<CallToolResult>(rawRequest, capture(rawSlot)) }
            assertEquals(McpFixtureClient.FIXTURE_REQUEST_TIMEOUT, rawSlot.captured.timeout)

            val callerOptions = RequestOptions(timeout = 1.seconds)
            client.callTool("caller-tool", mapOf("key" to "value"), options = callerOptions)
            val callerSlot = slot<RequestOptions>()
            coVerify { delegate.callTool("caller-tool", mapOf("key" to "value"), any(), capture(callerSlot)) }
            assertSame(callerOptions, callerSlot.captured)
        }
}
