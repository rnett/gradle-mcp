package dev.rnett.gradle.mcp.mcp.fixtures

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.tools.BackgroundBuildTools
import dev.rnett.gradle.mcp.tools.GradleBuildLookupTools
import dev.rnett.gradle.mcp.tools.GradleExecutionTools
import dev.rnett.gradle.mcp.tools.GradleIntrospectionTools
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import org.koin.dsl.module
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BaseMcpServerTest {

    @TempDir
    lateinit var tempDir: Path

    protected lateinit var server: McpServerFixture
    protected val provider = mockk<GradleProvider>(relaxed = true)

    protected open fun createTestModule(): org.koin.core.module.Module = module {
        single { DI.json }
        single<GradleConfiguration> {
            GradleConfiguration(4, kotlin.time.Duration.parse("10m"), false)
        }
        single<InitScriptProvider> { DefaultInitScriptProvider(tempDir.resolve("init-scripts")) }
        single<BundledJarProvider> { DefaultBundledJarProvider(tempDir.resolve("jars")) }
        single { BuildManager() }
        single<dev.rnett.gradle.mcp.repl.ReplManager> { dev.rnett.gradle.mcp.repl.DefaultReplManager(get()) }
        single<GradleProvider> {
            every { provider.buildManager } returns get()
            provider
        }

        single {
            val provider: GradleProvider = get()
            val replManager: dev.rnett.gradle.mcp.repl.ReplManager = get()
            listOf(
                GradleIntrospectionTools(provider),
                GradleExecutionTools(provider),
                dev.rnett.gradle.mcp.tools.ReplTools(provider, replManager),
                BackgroundBuildTools(provider),
                GradleBuildLookupTools(get()),
            )
        }

        single {
            val components: List<McpServerComponent> = get()
            DI.createServer(get(), components)
        }
    }

    @BeforeTest
    open fun setup() = runTest {
        server = McpServerFixture(koinModules = listOf(createTestModule()))
        server.start()
    }

    @AfterTest
    open fun cleanup() = runTest {
        server.close()
    }
}