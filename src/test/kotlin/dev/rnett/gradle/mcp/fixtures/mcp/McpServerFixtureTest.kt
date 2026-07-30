package dev.rnett.gradle.mcp.fixtures.mcp

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.modelcontextprotocol.kotlin.sdk.server.Server
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
}
