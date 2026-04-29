package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.DefaultGradleVersionService
import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.dependencies.DefaultGradleDependencyService
import dev.rnett.gradle.mcp.dependencies.DefaultGradleSourceService
import dev.rnett.gradle.mcp.dependencies.DefaultSourceIndexService
import dev.rnett.gradle.mcp.dependencies.DefaultSourceStorageService
import dev.rnett.gradle.mcp.dependencies.DefaultSourcesService
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.GradleSourceService
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
import dev.rnett.gradle.mcp.fixtures.gradle.GradleProjectFixture
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultGradleConnectionService
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleConnectionService
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.gradle.build.BuildExecutionService
import dev.rnett.gradle.mcp.gradle.build.DefaultBuildExecutionService
import dev.rnett.gradle.mcp.lucene.LuceneReaderCache
import dev.rnett.gradle.mcp.maven.DefaultDepsDevService
import dev.rnett.gradle.mcp.maven.DefaultMavenCentralService
import dev.rnett.gradle.mcp.maven.DefaultMavenRepoService
import dev.rnett.gradle.mcp.maven.DepsDevService
import dev.rnett.gradle.mcp.maven.MavenCentralService
import dev.rnett.gradle.mcp.maven.MavenRepoService
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.tools.ToolNames
import dev.rnett.gradle.mcp.utils.DefaultEnvProvider
import dev.rnett.gradle.mcp.utils.EnvProvider
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.test.assertTrue
import kotlin.test.fail

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseReplIntegrationTest : BaseMcpServerTest() {

    protected lateinit var project: GradleProjectFixture

    @BeforeAll
    fun setupAll() {
        tempDir = Files.createTempDirectory("gradle-mcp-test")
        super.setup()
    }

    @AfterAll
    fun cleanupAll() {
        runBlocking {
            try {
                server.client.callTool(ToolNames.REPL, mapOf("command" to "stop"))
            } catch (e: Exception) {
                // Ignore
            }
            if (::project.isInitialized) {
                project.close()
            }
            super.cleanup()
        }
        try {
            tempDir.toFile().deleteRecursively()
        } catch (e: Exception) {
            // Ignore
        }
    }

    @BeforeEach
    override fun setup() {
        // Do nothing here, we call it in @BeforeAll
    }

    @AfterEach
    override fun cleanup() {
        // Do nothing here, we call it in @AfterAll
    }

    protected fun initProject(fixture: GradleProjectFixture) {
        project = fixture
        server.setServerRoots(Root(fixture.path().toUri().toString(), "root"))
    }

    protected suspend fun startRepl(projectPath: String = ":", sourceSet: String = "main") {
        val startResponse = server.client.callTool(
            ToolNames.REPL, mapOf(
                "command" to "start",
                "projectPath" to projectPath,
                "sourceSet" to sourceSet
            )
        ) as CallToolResult
        assertTrue(
            (startResponse.content.first() as TextContent).text!!.startsWith("REPL session started"),
            "Expected REPL to start, but got: ${(startResponse.content.first() as TextContent).text}"
        )
    }

    override fun Scope.createProvider(): GradleProvider {
        return DefaultGradleProvider(
            config = get<GradleConfiguration>(),
            connectionService = get<GradleConnectionService>(),
            executionService = get<BuildExecutionService>(),
            buildManager = get<BuildManager>()
        )
    }

    override fun createTestModule() = module {
        single { DI.json }
        single { DI.xml }
        single { DI.createHttpClient(get(), get()) }
        single { GradleConfiguration() }
        single { DefaultInitScriptProvider(SharedTestInfrastructure.sharedWorkingDir.resolve("init-scripts")) } bind InitScriptProvider::class
        single { DefaultBundledJarProvider(SharedTestInfrastructure.sharedWorkingDir.resolve("jars")) } bind BundledJarProvider::class
        single { BuildManager() }
        single<EnvProvider> { DefaultEnvProvider }
        single<GradleConnectionService> { DefaultGradleConnectionService() }
        single<BuildExecutionService> { DefaultBuildExecutionService(envProvider = get()) }
        single<ReplManager> { DefaultReplManager(get()) }
        single<ReplEnvironmentService> { DefaultReplEnvironmentService(get()) }
        single { io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) }
        single { GradleMcpEnvironment(SharedTestInfrastructure.sharedMcpWorkingDir) }
        single<MarkdownService> { DefaultMarkdownService(get()) }
        single { HtmlConverter(get()) }
        single { LuceneReaderCache() }
        single<DistributionDownloaderService> { DefaultDistributionDownloaderService(get(), get()) }
        single<ContentExtractorService> { DefaultContentExtractorService(get(), get(), get()) }
        single<GradleDocsIndexService> { DefaultGradleDocsIndexService(get(), get(), get(), get()) }
        single<GradleVersionService> { DefaultGradleVersionService(get()) }
        single<GradleDocsService> { DefaultGradleDocsService(get(), get(), get(), get()) }
        single<GradleDependencyService> { DefaultGradleDependencyService(get()) }
        single<MavenRepoService> { DefaultMavenRepoService(get()) }
        single<MavenCentralService> { DefaultMavenCentralService(get()) }
        single<DepsDevService> { DefaultDepsDevService(get()) }
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
        single<GradleProvider> { createProvider() }
        single {
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
                replEnvironmentService,
                envProvider,
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

    protected suspend fun runSnippetAndAssertImage(code: String, resourceName: String) {
        val response = server.client.callTool(
            ToolNames.REPL, mapOf(
                "command" to "run",
                "code" to code
            )
        ) as CallToolResult
        assert(!response.isError!!) { "Snippet failed: ${response.content.joinToString { if (it is TextContent) it.text!! else "Image(${it})" }}" }

        val imageContent = response.content.filterIsInstance<ImageContent>().firstOrNull()
            ?: fail("Expected image content in response, but got: ${response.content}")

        ImageAssert.assertImage(imageContent.data, resourceName)
    }

    protected suspend fun runSnippet(code: String): String {
        val response = server.client.callTool(
            ToolNames.REPL, mapOf(
                "command" to "run",
                "code" to code
            )
        ) as CallToolResult
        assert(!response.isError!!) { "Snippet failed: ${response.content.joinToString { if (it is TextContent) it.text!! else "Image(${it})" }}" }

        return response.content.joinToString("\n") {
            when (it) {
                is TextContent -> it.text!!
                is ImageContent -> "Image(${it.mimeType})"
                else -> it.toString()
            }
        }
    }
}
