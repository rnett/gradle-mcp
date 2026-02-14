package dev.rnett.gradle.mcp

import io.ktor.server.config.MapApplicationConfig
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.check.checkModules
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class StartupE2ETest : KoinTest {

    @AfterTest
    fun cleanup() {
        stopKoin()
    }

    private fun loadConfig(): MapApplicationConfig {
        return MapApplicationConfig(
            "gradle.maxConnections" to "10",
            "gradle.ttl" to "PT5M",
            "gradle.allowPublicScansPublishing" to "true"
        )
    }

    @Test
    fun `DI modules are valid and all dependencies can be resolved`() {
        val config = loadConfig()

        val koinApp = DI.createKoin(config)
        koinApp.checkModules()
    }

    @Test
    fun `Application can be initialized with real DI`() = runBlocking {
        val config = loadConfig()

        val koinApp = DI.createKoin(config)
        val koin = koinApp.koin

        // This replicates what Application(args) does
        val provider = koin.get<dev.rnett.gradle.mcp.gradle.GradleProvider>()
        val replManager = koin.get<dev.rnett.gradle.mcp.repl.ReplManager>()
        val mcpServer = koin.get<dev.rnett.gradle.mcp.mcp.McpServer>()

        assertNotNull(provider)
        assertNotNull(replManager)
        assertNotNull(mcpServer)

        mcpServer.close()
    }
}
