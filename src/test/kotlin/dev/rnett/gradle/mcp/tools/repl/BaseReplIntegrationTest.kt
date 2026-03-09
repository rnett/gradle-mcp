package dev.rnett.gradle.mcp.tools.repl

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.dependencies.DefaultGradleDependencyService
import dev.rnett.gradle.mcp.dependencies.DefaultGradleSourceService
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
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.gradle.fixtures.GradleProjectFixture
import dev.rnett.gradle.mcp.lucene.LuceneReaderCache
import dev.rnett.gradle.mcp.maven.DefaultMavenCentralService
import dev.rnett.gradle.mcp.maven.DefaultMavenRepoService
import dev.rnett.gradle.mcp.maven.MavenCentralService
import dev.rnett.gradle.mcp.maven.MavenRepoService
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import dev.rnett.gradle.mcp.mcp.fixtures.SharedTestInfrastructure
import dev.rnett.gradle.mcp.repl.DefaultReplEnvironmentService
import dev.rnett.gradle.mcp.repl.DefaultReplManager
import dev.rnett.gradle.mcp.repl.ReplEnvironmentService
import dev.rnett.gradle.mcp.repl.ReplManager
import dev.rnett.gradle.mcp.tools.ToolNames
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseReplIntegrationTest : BaseMcpServerTest() {

    protected lateinit var project: GradleProjectFixture

    @BeforeAll
    fun setupAll() {
        tempDir = java.nio.file.Files.createTempDirectory("gradle-mcp-test")
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

    @BeforeTest
    override fun setup() {
        // Do nothing here, we call it in @BeforeAll
    }

    @AfterTest
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

    override fun createTestModule() = module {
        single { DI.json }
        single { DI.xml }
        single { DI.createHttpClient(get(), get()) }
        single { GradleConfiguration(4, 10.minutes, false) }
        single { DefaultInitScriptProvider(SharedTestInfrastructure.sharedWorkingDir.resolve("init-scripts")) } bind InitScriptProvider::class
        single { DefaultBundledJarProvider(SharedTestInfrastructure.sharedWorkingDir.resolve("jars")) } bind BundledJarProvider::class
        single<BuildManager> { BuildManager() }
        single<ReplManager> { DefaultReplManager(get()) }
        single<ReplEnvironmentService> { DefaultReplEnvironmentService(get()) }
        single { GradleMcpEnvironment(SharedTestInfrastructure.sharedMcpWorkingDir) }
        single<MarkdownService> { DefaultMarkdownService() }
        single { HtmlConverter(get()) }
        single { LuceneReaderCache() }
        single<DistributionDownloaderService> { DefaultDistributionDownloaderService(get(), get()) }
        single<ContentExtractorService> { DefaultContentExtractorService(get(), get(), get()) }
        single<GradleDocsIndexService> { DefaultGradleDocsIndexService(get(), get(), get(), get()) }
        single<dev.rnett.gradle.mcp.GradleVersionService> { dev.rnett.gradle.mcp.DefaultGradleVersionService(get()) }
        single<GradleDocsService> { DefaultGradleDocsService(get(), get(), get(), get()) }
        single<GradleDependencyService> { DefaultGradleDependencyService(get()) }
        single<MavenRepoService> { DefaultMavenRepoService(get()) }
        single<MavenCentralService> { DefaultMavenCentralService(get()) }
        single<dev.rnett.gradle.mcp.dependencies.search.IndexService> { dev.rnett.gradle.mcp.dependencies.search.DefaultIndexService(get()) }
        single<dev.rnett.gradle.mcp.dependencies.SourcesService> { dev.rnett.gradle.mcp.dependencies.DefaultSourcesService(get(), get(), get()) }
        single<GradleSourceService> { DefaultGradleSourceService(get(), get(), get(), get()) }
        single<GradleProvider> {
            DefaultGradleProvider(
                get(),
                buildManager = get()
            )
        }
        single {
            DI.components(get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
        }
        single {
            DI.createServer(get(), get())
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
