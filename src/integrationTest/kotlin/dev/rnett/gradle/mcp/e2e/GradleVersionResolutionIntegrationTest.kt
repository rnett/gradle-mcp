package dev.rnett.gradle.mcp.e2e

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.DefaultGradleVersionService
import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.dependencies.DefaultGradleSourceService
import dev.rnett.gradle.mcp.dependencies.DefaultJdkSourceService
import dev.rnett.gradle.mcp.dependencies.DefaultSourceIndexService
import dev.rnett.gradle.mcp.dependencies.DefaultSourceStorageService
import dev.rnett.gradle.mcp.dependencies.DefaultSourcesService
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.GradleSourceService
import dev.rnett.gradle.mcp.dependencies.JdkSourceService
import dev.rnett.gradle.mcp.dependencies.SourceIndexService
import dev.rnett.gradle.mcp.dependencies.SourceStorageService
import dev.rnett.gradle.mcp.dependencies.SourcesService
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
import dev.rnett.gradle.mcp.dependencies.search.DefaultIndexService
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.ParserDownloader
import dev.rnett.gradle.mcp.dependencies.search.TreeSitterDeclarationExtractor
import dev.rnett.gradle.mcp.dependencies.search.TreeSitterLanguageProvider
import dev.rnett.gradle.mcp.fixtures.SharedTestInfrastructure
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.lucene.LuceneReaderCache
import dev.rnett.gradle.mcp.maven.DepsDevService
import dev.rnett.gradle.mcp.maven.MavenCentralService
import dev.rnett.gradle.mcp.maven.MavenRepoService
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.repl.DefaultReplEnvironmentService
import dev.rnett.gradle.mcp.repl.DefaultReplManager
import dev.rnett.gradle.mcp.repl.ReplEnvironmentService
import dev.rnett.gradle.mcp.repl.ReplManager
import dev.rnett.gradle.mcp.tools.ToolNames
import dev.rnett.gradle.mcp.utils.DefaultEnvProvider
import dev.rnett.gradle.mcp.utils.EnvProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes


class GradleVersionResolutionIntegrationTest : BaseMcpServerTest() {

    private val testVersion = "9.9.9"

    override fun createTestModule() = module {
        single { DI.json }
        single { DI.xml }
        single<EnvProvider> { DefaultEnvProvider }

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
            GradleConfiguration()
        }
        single<InitScriptProvider> { DefaultInitScriptProvider(SharedTestInfrastructure.sharedWorkingDir.resolve("init-scripts")) }
        single<BundledJarProvider> { DefaultBundledJarProvider(SharedTestInfrastructure.sharedWorkingDir.resolve("jars")) }
        single { buildManager }
        single<ReplManager> { DefaultReplManager(get()) }
        single<ReplEnvironmentService> { DefaultReplEnvironmentService(get()) }
        single { GradleMcpEnvironment(SharedTestInfrastructure.sharedMcpWorkingDir) }
        single<MarkdownService> { DefaultMarkdownService(get()) }
        single<GradleVersionService> { DefaultGradleVersionService(get()) }

        single { HtmlConverter(get()) }
        single { LuceneReaderCache() }
        single<DistributionDownloaderService> { DefaultDistributionDownloaderService(get(), get()) }
        single<ContentExtractorService> { DefaultContentExtractorService(get(), get(), get()) }
        single<GradleDocsIndexService> { DefaultGradleDocsIndexService(get(), get(), get(), get()) }

        single<GradleDocsService> { DefaultGradleDocsService(get(), get(), get(), get()) }

        single<GradleDependencyService> { mockk<GradleDependencyService>(relaxed = true) }
        single<MavenRepoService> { mockk<MavenRepoService>(relaxed = true) }
        single<MavenCentralService> { mockk<MavenCentralService>(relaxed = true) }
        single<DepsDevService> { mockk<DepsDevService>(relaxed = true) }

        single { ParserDownloader(get(), TestFixturesBuildConfig.TREE_SITTER_LANGUAGE_PACK_VERSION) }
        single { TreeSitterLanguageProvider(get()) }
        single { TreeSitterDeclarationExtractor(get()) }
        single<dev.rnett.gradle.mcp.dependencies.search.SearchProvider> { dev.rnett.gradle.mcp.dependencies.search.DeclarationSearch(get()) }
        single<dev.rnett.gradle.mcp.dependencies.search.SearchProvider> { dev.rnett.gradle.mcp.dependencies.search.FullTextSearch() }
        single<dev.rnett.gradle.mcp.dependencies.search.SearchProvider> { dev.rnett.gradle.mcp.dependencies.search.GlobSearch() }

        single<IndexService> { DefaultIndexService(get(), getAll()) }
        single<SourceStorageService> { DefaultSourceStorageService(get()) }
        single<CoroutineDispatcher> { Dispatchers.IO }
        single<SourceIndexService> { DefaultSourceIndexService(get()) }
        single<SourcesService> { DefaultSourcesService(get(), get(), get(), get()) }
        single<GradleSourceService> { DefaultGradleSourceService(get(), get(), get(), get(), get()) }
        single<JdkSourceService> { DefaultJdkSourceService(get(), get()) }

        single<GradleProvider> {
            createProvider()
        }

        factory {
            val provider: GradleProvider = get()
            val replManager: ReplManager = get()
            val replEnvironmentService: ReplEnvironmentService = get()
            val envProvider: EnvProvider = get()
            val gradleDocsService: GradleDocsService = get()
            val gradleVersionService: GradleVersionService = get()
            val gradleDependencyService: GradleDependencyService = get()
            val depsDevService: DepsDevService = get()
            val sourcesService: SourcesService = get()
            val gradleSourceService: GradleSourceService = get()
            val indexService: SourceIndexService = get()
            val searchProviders: List<dev.rnett.gradle.mcp.dependencies.search.SearchProvider> = getAll()
            DI.components(
                provider,
                replManager,
                replEnvironmentService, envProvider,
                gradleDocsService,
                gradleVersionService,
                gradleDependencyService,
                depsDevService,
                sourcesService,
                gradleSourceService,
                indexService,
                searchProviders
            )
        }

        single {
            val components: List<McpServerComponent> = get()
            DI.createServer(get(), components)
        }
    }

    @Test
    fun `calling docs tool with current resolves to concrete version and creates versioned cache dir`() = runTest(timeout = 10.minutes) {
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
