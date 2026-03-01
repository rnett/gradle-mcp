package dev.rnett.gradle.mcp.mcp.fixtures

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.DI.components
import dev.rnett.gradle.mcp.DefaultMarkdownService
import dev.rnett.gradle.mcp.GradleDocsService
import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.MarkdownService
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.repl.DefaultReplEnvironmentService
import dev.rnett.gradle.mcp.repl.ReplEnvironmentService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import org.koin.core.scope.Scope
import org.koin.dsl.module
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BaseMcpServerTest {

    @TempDir
    lateinit var tempDir: Path

    protected lateinit var server: McpServerFixture
    val buildManager = BuildManager()
    protected open fun Scope.createProvider(): GradleProvider {
        val provider = mockk<GradleProvider>(relaxed = true)
        every { provider.buildManager } returns buildManager
        return provider
    }

    protected open fun createTestModule(): org.koin.core.module.Module = module {
        single { DI.json }
        single<GradleConfiguration> {
            GradleConfiguration(4, kotlin.time.Duration.parse("10m"), false)
        }
        single<InitScriptProvider> { DefaultInitScriptProvider(tempDir.resolve("init-scripts")) }
        single<BundledJarProvider> { DefaultBundledJarProvider(tempDir.resolve("jars")) }
        single { buildManager }
        single<dev.rnett.gradle.mcp.repl.ReplManager> { dev.rnett.gradle.mcp.repl.DefaultReplManager(get()) }
        single<ReplEnvironmentService> { DefaultReplEnvironmentService(get()) }
        single { GradleMcpEnvironment(tempDir) }
        single<MarkdownService> { DefaultMarkdownService() }
        single<GradleDocsService> { mockk<GradleDocsService>(relaxed = true) }

        single<GradleProvider> {
            createProvider()
        }

        factory {
            val provider: GradleProvider = get()
            val replManager: dev.rnett.gradle.mcp.repl.ReplManager = get()
            val replEnvironmentService: ReplEnvironmentService = get()
            val gradleDocsService: GradleDocsService = get()
            components(provider, replManager, replEnvironmentService, gradleDocsService)
        }

        single {
            val components: List<McpServerComponent> = get()
            DI.createServer(get(), components)
        }
    }

    val provider: GradleProvider get() = server.koin.get<GradleProvider>()

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