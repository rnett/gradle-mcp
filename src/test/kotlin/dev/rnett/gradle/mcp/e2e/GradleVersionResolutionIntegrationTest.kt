package dev.rnett.gradle.mcp.e2e

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.DefaultGradleVersionService
import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.GradleSourceService
import dev.rnett.gradle.mcp.dependencies.gradle.DefaultDistributionDownloaderService
import dev.rnett.gradle.mcp.dependencies.gradle.DistributionDownloaderService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.ContentExtractorService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DefaultContentExtractorService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DefaultGradleDocsIndexService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DefaultGradleDocsService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DefaultMarkdownService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsIndexService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.HtmlConverter
import dev.rnett.gradle.mcp.dependencies.gradle.docs.MarkdownService
import dev.rnett.gradle.mcp.fixtures.SharedTestInfrastructure
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.lucene.LuceneReaderCache
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.repl.DefaultReplEnvironmentService
import dev.rnett.gradle.mcp.repl.ReplEnvironmentService
import dev.rnett.gradle.mcp.tools.ToolNames
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.test.assertTrue

@Tag("integration")
class GradleVersionResolutionIntegrationTest : BaseMcpServerTest() {

    private val testVersion = "9.9.9"

    override fun createTestModule() = module {
        single { DI.json }
        single { DI.xml }

        val mockClient = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(DI.json) }
            engine {
                addHandler { request ->
                    val urlStr = request.url.toString()
                    when {
                        urlStr == "https://services.gradle.org/versions/current" -> {
                            respond(
                                """{"version": "$testVersion"}""",
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        urlStr.endsWith(".zip") -> {
                            val bos = ByteArrayOutputStream()
                            ZipOutputStream(bos).use { }
                            respond(
                                bos.toByteArray(),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/zip")
                            )
                        }

                        else -> respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        }
        single { mockClient }

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
        single<dev.rnett.gradle.mcp.GradleVersionService> { DefaultGradleVersionService(get()) }

        single { HtmlConverter(get()) }
        single { LuceneReaderCache() }
        single<DistributionDownloaderService> { DefaultDistributionDownloaderService(get(), get()) }
        single<ContentExtractorService> { DefaultContentExtractorService(get(), get(), get()) }
        single<GradleDocsIndexService> { DefaultGradleDocsIndexService(get(), get(), get(), get()) }

        single<GradleDocsService> { DefaultGradleDocsService(get(), get(), get(), get()) }

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
            DI.components(provider, replManager, replEnvironmentService, gradleDocsService, gradleVersionService, gradleDependencyService, mavenRepoService, mavenCentralService, sourcesService, gradleSourceService)
        }

        single {
            val components: List<McpServerComponent> = get()
            DI.createServer(get(), components)
        }
    }

    @Test
    fun `calling docs tool with current resolves to concrete version and creates versioned cache dir`() = runTest {
        val env = server.koin.get<GradleMcpEnvironment>()

        // Call a tool that triggers version resolution
        try {
            server.client.callTool(ToolNames.GRADLE_DOCS, emptyMap())
        } catch (e: Exception) {
            // Should not fail now if docs download is mocked
        }

        val cacheDir = env.cacheDir.resolve("reading_gradle_docs")
        val versionedDir = cacheDir.resolve(testVersion)
        val literalCurrentDir = cacheDir.resolve("current")

        assertTrue(versionedDir.exists(), "Cache directory for resolved version $testVersion should exist (was: $versionedDir)")
        assertTrue(!literalCurrentDir.exists(), "Literal 'current' cache directory should NOT exist")
    }
}
