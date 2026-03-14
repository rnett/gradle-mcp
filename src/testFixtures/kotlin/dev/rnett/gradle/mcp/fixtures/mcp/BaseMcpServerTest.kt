package dev.rnett.gradle.mcp.fixtures.mcp

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.GradleSourceService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DefaultMarkdownService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.MarkdownService
import dev.rnett.gradle.mcp.fixtures.SharedTestInfrastructure
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.maven.MavenCentralService
import dev.rnett.gradle.mcp.maven.MavenRepoService
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.repl.DefaultReplEnvironmentService
import dev.rnett.gradle.mcp.repl.DefaultReplManager
import dev.rnett.gradle.mcp.repl.ReplEnvironmentService
import dev.rnett.gradle.mcp.repl.ReplManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module
import java.nio.file.Path

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

    protected open fun createTestModule(): Module = module {
        single { DI.json }
        single { DI.xml }
        single { DI.createHttpClient(get(), get()) }
        single<GradleConfiguration> {
            GradleConfiguration()
        }
        single<InitScriptProvider> { DefaultInitScriptProvider(SharedTestInfrastructure.sharedWorkingDir.resolve("init-scripts")) }
        single<BundledJarProvider> { DefaultBundledJarProvider(SharedTestInfrastructure.sharedWorkingDir.resolve("jars")) }
        single { buildManager }
        single<ReplManager> { DefaultReplManager(get()) }
        single<ReplEnvironmentService> { DefaultReplEnvironmentService(get()) }
        single { GradleMcpEnvironment(SharedTestInfrastructure.sharedMcpWorkingDir) }
        single<MarkdownService> { DefaultMarkdownService() }
        single<GradleDocsService> { mockk<GradleDocsService>(relaxed = true) }
        single<GradleVersionService> { mockk<GradleVersionService>(relaxed = true) }
        single<GradleDependencyService> { mockk<GradleDependencyService>(relaxed = true) }
        single<MavenRepoService> { mockk<MavenRepoService>(relaxed = true) }
        single<MavenCentralService> { mockk<MavenCentralService>(relaxed = true) }
        single<SourcesService> { mockk<SourcesService>(relaxed = true) }
        single<GradleSourceService> { mockk<GradleSourceService>(relaxed = true) }

        single<GradleProvider> {
            createProvider()
        }

        factory {
            val provider: GradleProvider = get()
            val replManager: ReplManager = get()
            val replEnvironmentService: ReplEnvironmentService = get()
            val gradleDocsService: GradleDocsService = get()
            val gradleVersionService: GradleVersionService = get()
            val gradleDependencyService: GradleDependencyService = get()
            val mavenRepoService: MavenRepoService = get()
            val mavenCentralService: MavenCentralService = get()
            val sourcesService: SourcesService = get()
            val gradleSourceService: GradleSourceService = get()
            DI.components(provider, replManager, replEnvironmentService, gradleDocsService, gradleVersionService, gradleDependencyService, mavenRepoService, mavenCentralService, sourcesService, gradleSourceService)
        }

        single {
            val components: List<McpServerComponent> = get()
            DI.createServer(get(), components)
        }
    }

    val provider: GradleProvider get() = server.koin.get<GradleProvider>()

    open fun createFixture(): McpServerFixture = McpServerFixture(koinModules = listOf(createTestModule()))

    @BeforeEach
    open fun setup() = runTest {
        server = createFixture()
        server.start()
    }

    @AfterEach
    open fun cleanup() = runTest {
        server.close()
    }
}