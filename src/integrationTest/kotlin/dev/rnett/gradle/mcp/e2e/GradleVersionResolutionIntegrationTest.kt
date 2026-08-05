package dev.rnett.gradle.mcp.e2e

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.DefaultGradleVersionService
import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.dependencies.DefaultSourceStorageService
import dev.rnett.gradle.mcp.dependencies.DefaultSourcesService
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DefaultGradleDocsService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsService
import dev.rnett.gradle.mcp.dependencies.search.DefaultIndexService
import dev.rnett.gradle.mcp.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.dependencies.search.GlobSearch
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.fixtures.SharedTestInfrastructure
import dev.rnett.gradle.mcp.fixtures.dependencies.NoJdkSourceService
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
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
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class GradleVersionResolutionIntegrationTest : BaseMcpServerTest() {

    private val testVersion = "9.9.9"
    private val mockClient = HttpClient(MockEngine) {
        install(ContentNegotiation) { json(DI.json) }
        engine {
            addHandler { request ->
                when {
                    request.url.toString() == "https://services.gradle.org/versions/current" -> respond(
                        """{"version":"$testVersion"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )

                    request.url.toString().endsWith(".zip") -> {
                        val bytes = ByteArrayOutputStream().use { output ->
                            ZipOutputStream(output).use { }
                            output.toByteArray()
                        }
                        respond(
                            bytes,
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/zip")
                        )
                    }

                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        }
    }

    override fun createTestModules(): List<Module> = listOf(
        super.createTestModule(),
        module {
            single { mockClient }
            single<GradleVersionService> { DefaultGradleVersionService(get()) }
            single<GradleDocsService> { DefaultGradleDocsService(get(), get(), get(), get()) }
            single<SourcesService> { sharedSourcesService }
        }
    )

    override fun cleanup() {
        try {
            super.cleanup()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun `calling docs tool with current resolves to concrete version and creates versioned cache dir`() = runTest(timeout = 10.minutes) {
        val env = server.koin.get<GradleMcpEnvironment>()

        try {
            server.client.callTool(ToolNames.GRADLE_DOCS, emptyMap())
        } catch (_: Exception) {
            // The empty test archive need not contain a documentation index; version resolution happens first.
        }

        val cacheDir = env.cacheDir.resolve("reading_gradle_docs")
        val versionedDir = cacheDir.resolve(testVersion)
        val literalCurrentDir = cacheDir.resolve("current")

        assertTrue(versionedDir.exists(), "Cache directory for resolved version $testVersion should exist (was: $versionedDir)")
        assertTrue(!literalCurrentDir.exists(), "Literal 'current' cache directory should NOT exist")
    }

    companion object {
        // The session-view cache is per [SourcesService] instance. This class builds real MCP
        // servers but runs no real Gradle builds (its `GradleProvider` is the inherited relaxed mock
        // and `GradleDependencyService` is mocked), so per spec it shares only its real
        // [SourcesService] at class scope; the per-method mock provider lifecycle is unchanged.
        // [SourcesService] has no close(), so no companion `@AfterAll` close is required.
        private val sharedSourcesService: SourcesService by lazy {
            val environment = GradleMcpEnvironment(SharedTestInfrastructure.sharedMcpWorkingDir)
            DefaultSourcesService(
                depService = mockk<GradleDependencyService>(relaxed = true),
                storageService = DefaultSourceStorageService(environment),
                indexService = DefaultIndexService(environment, listOf<SearchProvider>(FullTextSearch(), GlobSearch())),
                jdkSourceService = NoJdkSourceService
            )
        }
    }
}
