package dev.rnett.gradle.mcp.e2e

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.LatestStableGradleVersion
import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.mcp.closeServer
import dev.rnett.gradle.mcp.repl.ReplManager
import io.ktor.server.config.MapApplicationConfig
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
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

        koinApp = koinApplication {
            allowOverride(true)
            modules(
                DI.createModule(config),
                module {
                    single<GradleVersionService> {
                        StubGradleVersionService(
                            LatestStableGradleVersion(TestFixturesBuildConfig.GRADLE_VERSION, LatestStableGradleVersion.Source.FETCHED_LIVE)
                        )
                    }
                }
            )
        }
        koinApp!!.checkModules()
    }

    @Test
    fun `Application can be initialized with real DI`() = runBlocking {
        val config = loadConfig()

        koinApp = koinApplication {
            allowOverride(true)
            modules(
                DI.createModule(config),
                module {
                    single<GradleVersionService> {
                        StubGradleVersionService(
                            LatestStableGradleVersion(TestFixturesBuildConfig.GRADLE_VERSION, LatestStableGradleVersion.Source.FETCHED_LIVE)
                        )
                    }
                }
            )
        }
        val koin = koinApp!!.koin

        // This replicates what Application(args) does
        val provider = koin.get<GradleProvider>()
        val replManager = koin.get<ReplManager>()
        val mcpServer = koin.get<Server>()

        assertNotNull(provider)
        assertNotNull(replManager)
        assertNotNull(mcpServer)

        closeServer(mcpServer, koin.get<List<McpServerComponent>>())
    }
}
