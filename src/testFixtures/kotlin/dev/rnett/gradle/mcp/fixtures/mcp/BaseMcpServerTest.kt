package dev.rnett.gradle.mcp.fixtures.mcp

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.LatestStableGradleVersion
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.GradleSourceService
import dev.rnett.gradle.mcp.dependencies.JdkSourceService
import dev.rnett.gradle.mcp.dependencies.SourceIndexService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsService
import dev.rnett.gradle.mcp.fixtures.SharedTestInfrastructure
import dev.rnett.gradle.mcp.fixtures.dependencies.NoJdkSourceService
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.maven.DepsDevService
import dev.rnett.gradle.mcp.maven.MavenCentralService
import dev.rnett.gradle.mcp.maven.MavenRepoService
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
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
import kotlin.time.Duration.Companion.minutes

private class TestGradleVersionService : GradleVersionService {
    private val resolution = LatestStableGradleVersion(
        BuildConfig.GRADLE_VERSION,
        LatestStableGradleVersion.Source.BUNDLED_FALLBACK
    )

    override suspend fun resolveVersion(version: String?): String =
        if (version == null || version.equals("current", ignoreCase = true) || version.equals("latest", ignoreCase = true)) {
            resolution.version
        } else {
            version
        }

    override suspend fun resolveLatestStable(): LatestStableGradleVersion = resolution
}

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

    protected open fun createTestConfig(): ApplicationConfig = MapApplicationConfig(
        "gradle.maxConnections" to "10",
        "gradle.ttl" to "PT5M",
        "gradle.allowPublicScansPublishing" to "true"
    )

    /** Overrides only test boundaries; all other definitions come from [DI.createModule]. */
    protected open fun createTestModule(): Module = module {
        single<InitScriptProvider> {
            DefaultInitScriptProvider(SharedTestInfrastructure.sharedWorkingDir.resolve("init-scripts"))
        }
        single<BundledJarProvider> {
            DefaultBundledJarProvider(SharedTestInfrastructure.sharedWorkingDir.resolve("jars"))
        }
        single { buildManager }
        single { GradleMcpEnvironment(SharedTestInfrastructure.sharedMcpWorkingDir) }
        single<GradleVersionService> { TestGradleVersionService() }

        single<GradleDocsService> { mockk<GradleDocsService>(relaxed = true) }
        single<GradleDependencyService> { mockk<GradleDependencyService>(relaxed = true) }
        single<MavenRepoService> { mockk<MavenRepoService>(relaxed = true) }
        single<MavenCentralService> { mockk<MavenCentralService>(relaxed = true) }
        single<DepsDevService> { mockk<DepsDevService>(relaxed = true) }
        single<SourcesService> { mockk<SourcesService>(relaxed = true) }
        single<SourceIndexService> { mockk<SourceIndexService>(relaxed = true) }
        single<GradleSourceService> { mockk<GradleSourceService>(relaxed = true) }
        single<JdkSourceService> { NoJdkSourceService }

        single<GradleProvider> { createProvider() }
    }

    protected open fun createTestModules(): List<Module> = listOf(createTestModule())

    val provider: GradleProvider get() = server.koin.get<GradleProvider>()

    open fun createFixture(): McpServerFixture = McpServerFixture(
        koinModules = listOf(DI.createModule(createTestConfig())) + createTestModules()
    )

    @BeforeEach
    open fun setup() = runTest(timeout = 2.minutes) {
        server = createFixture()
        server.start()
    }

    @AfterEach
    open fun cleanup() = runTest(timeout = 2.minutes) {
        server.close()
    }
}
