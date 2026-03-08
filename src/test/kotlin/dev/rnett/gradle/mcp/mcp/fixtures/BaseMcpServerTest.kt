package dev.rnett.gradle.mcp.mcp.fixtures

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.DI.components
import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.GradleSourceService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DefaultMarkdownService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.MarkdownService
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
        single { DI.xml }
        single { DI.createHttpClient(get(), get()) }
        single<GradleConfiguration> {
            GradleConfiguration(4, kotlin.time.Duration.parse("10m"), false)
        }
        single<InitScriptProvider> { DefaultInitScriptProvider(SharedTestInfrastructure.sharedWorkingDir.resolve("init-scripts")) }
        single<BundledJarProvider> { DefaultBundledJarProvider(SharedTestInfrastructure.sharedWorkingDir.resolve("jars")) }
        single { buildManager }
        single<dev.rnett.gradle.mcp.repl.ReplManager> { dev.rnett.gradle.mcp.repl.DefaultReplManager(get()) }
        single<ReplEnvironmentService> { DefaultReplEnvironmentService(get()) }
        single { GradleMcpEnvironment(SharedTestInfrastructure.sharedMcpWorkingDir) }
        single<MarkdownService> { DefaultMarkdownService() }
        single<GradleDocsService> { mockk<GradleDocsService>(relaxed = true) }
        single<dev.rnett.gradle.mcp.GradleVersionService> { mockk<dev.rnett.gradle.mcp.GradleVersionService>(relaxed = true) }
        single<GradleDependencyService> { mockk<GradleDependencyService>(relaxed = true) }
        single<dev.rnett.gradle.mcp.maven.MavenRepoService> { mockk<dev.rnett.gradle.mcp.maven.MavenRepoService>(relaxed = true) }
        single<dev.rnett.gradle.mcp.maven.MavenCentralService> { mockk<dev.rnett.gradle.mcp.maven.MavenCentralService>(relaxed = true) }
        single<dev.rnett.gradle.mcp.dependencies.SourcesService> { mockk<dev.rnett.gradle.mcp.dependencies.SourcesService>(relaxed = true) }
        single<GradleSourceService> { mockk<GradleSourceService>(relaxed = true) }

        single<GradleProvider> {
            createProvider()
        }

        factory {
            val provider: GradleProvider = get()
            val replManager: dev.rnett.gradle.mcp.repl.ReplManager = get()
            val replEnvironmentService: ReplEnvironmentService = get()
            val gradleDocsService: GradleDocsService = get()
            val gradleVersionService: dev.rnett.gradle.mcp.GradleVersionService = get()
            val gradleDependencyService: GradleDependencyService = get()
            val mavenRepoService: dev.rnett.gradle.mcp.maven.MavenRepoService = get()
            val mavenCentralService: dev.rnett.gradle.mcp.maven.MavenCentralService = get()
            val sourcesService: dev.rnett.gradle.mcp.dependencies.SourcesService = get()
            val gradleSourceService: GradleSourceService = get()
            components(provider, replManager, replEnvironmentService, gradleDocsService, gradleVersionService, gradleDependencyService, mavenRepoService, mavenCentralService, sourcesService, gradleSourceService)
        }

        single {
            val components: List<McpServerComponent> = get()
            DI.createServer(get(), components)
        }
    }

    val provider: GradleProvider get() = server.koin.get<GradleProvider>()

    open fun createFixture(): McpServerFixture = McpServerFixture(koinModules = listOf(createTestModule()))

    @BeforeTest
    open fun setup() = runTest {
        server = createFixture()
        server.start()
    }

    @AfterTest
    open fun cleanup() = runTest {
        server.close()
    }
}