package dev.rnett.gradle.mcp.e2e

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServer
import dev.rnett.gradle.mcp.repl.ReplManager
import io.ktor.server.config.MapApplicationConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.test.KoinTest
import org.koin.test.check.checkModules
import kotlin.test.assertNotNull


class DIE2ETest : KoinTest {

    private var koinApp: KoinApplication? = null
    override fun getKoin(): Koin = koinApp!!.koin

    @AfterEach
    fun cleanup() {
        koinApp?.close()
        koinApp = null
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

        koinApp = DI.createKoin(config)
        koinApp!!.checkModules()
    }

    @Test
    fun `Application can be initialized with real DI`() = runBlocking {
        val config = loadConfig()

        koinApp = DI.createKoin(config)
        val koin = koinApp!!.koin

        // This replicates what Application(args) does
        val provider = koin.get<GradleProvider>()
        val replManager = koin.get<ReplManager>()
        val mcpServer = koin.get<McpServer>()

        assertNotNull(provider)
        assertNotNull(replManager)
        assertNotNull(mcpServer)

        mcpServer.close()
    }
}
