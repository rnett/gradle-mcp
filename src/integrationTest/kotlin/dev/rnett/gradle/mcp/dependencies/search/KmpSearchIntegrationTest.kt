package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.dependencies.DefaultGradleDependencyService
import dev.rnett.gradle.mcp.dependencies.DefaultSourceIndexService
import dev.rnett.gradle.mcp.dependencies.DefaultSourceStorageService
import dev.rnett.gradle.mcp.dependencies.DefaultSourcesService
import dev.rnett.gradle.mcp.dependencies.model.SessionViewSourcesDir
import dev.rnett.gradle.mcp.fixtures.gradle.testGradleProject
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class KmpSearchIntegrationTest : KoinTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: Path

    private lateinit var koinApp: KoinApplication
    override fun getKoin(): Koin = koinApp.koin

    lateinit var environment: GradleMcpEnvironment
    lateinit var provider: DefaultGradleProvider
    lateinit var indexService: DefaultSourceIndexService
    lateinit var sourcesService: DefaultSourcesService

    @BeforeEach
    fun setup() {
        environment = GradleMcpEnvironment(tempDir.resolve("mcp"))
        koinApp = koinApplication {
            modules(module {
                single { environment }
                single { io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) }
                single { ParserDownloader(get()) }
                single { TreeSitterLanguageProvider(get()) }
                single { TreeSitterDeclarationExtractor(get()) }
                single { DeclarationSearch(get()) }
                single<IndexService> { DefaultIndexService(get(), listOf(get<DeclarationSearch>())) }
            })
        }

        provider = DefaultGradleProvider(
            config = GradleConfiguration(),
            buildManager = BuildManager()
        )
        val depService = DefaultGradleDependencyService(provider)
        val storageService = DefaultSourceStorageService(environment)
        val rawIndexService = getKoin().get<IndexService>()
        indexService = DefaultSourceIndexService(rawIndexService)
        sourcesService = DefaultSourcesService(depService, storageService, rawIndexService)
    }

    @AfterEach
    fun tearDown() {
        getKoin().getOrNull<HttpClient>()?.close()
        koinApp.close()
        provider.close()
    }

    @Test
    fun `test that common KMP symbols return only one result`() = runTest(timeout = 15.minutes) {
        testGradleProject {
            useKotlinDsl(true)
            buildScript(
                """
                plugins { id("org.jetbrains.kotlin.multiplatform") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
                repositories { mavenCentral() }
                kotlin {
                    jvm()
                    linuxX64()
                    sourceSets {
                        commonMain {
                            dependencies {
                                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${TestFixturesBuildConfig.KOTLINX_SERIALIZATION_VERSION}")
                            }
                        }
                    }
                }
            """.trimIndent()
            )
        }.use { project ->
            val projectRoot = GradleProjectRoot(project.pathString())

            // Resolve and process sources
            val sourcesDir = with(ProgressReporter.PRINTLN) {
                sourcesService.resolveAndProcessProjectSources(projectRoot, ":", providerToIndex = getKoin().get<DeclarationSearch>())
            }

            assertNotNull(sourcesDir)
            assertTrue(sourcesDir is SessionViewSourcesDir)
            val sessionView = sourcesDir as SessionViewSourcesDir

            // Search for 'Json' interface in kotlinx.serialization.json
            // It's defined in commonMain.
            val searchResponse = indexService.search(sessionView, getKoin().get<DeclarationSearch>(), "fqn:kotlinx.serialization.json.Json")

            println("Search results for 'Json':")
            searchResponse.results.forEach { println("  ${it.relativePath} at line ${it.line}") }

            // Should have results only from the common artifact if deduplication and isolation work correctly.
            // There might be multiple results (e.g. class and factory function), but they must be from the same artifact.
            assertTrue(searchResponse.results.isNotEmpty(), "Should find results for 'Json' in common artifact. Found none.")

            searchResponse.results.forEach { result ->
                assertTrue(result.relativePath.contains("kotlinx-serialization-json/"), "Result ${result.relativePath} should be from common artifact")
                assertFalse(result.relativePath.contains("-jvm/"), "Result ${result.relativePath} should NOT be from JVM platform artifact")
                assertFalse(result.relativePath.contains("-linuxx64/"), "Result ${result.relativePath} should NOT be from Linux platform artifact")
            }
        }
    }

    @Test
    fun `listPackageContents returns each symbol exactly once when common and platform artifacts are in scope`() = runTest(timeout = 15.minutes) {
        testGradleProject {
            useKotlinDsl(true)
            buildScript(
                """
                plugins { id("org.jetbrains.kotlin.multiplatform") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
                repositories { mavenCentral() }
                kotlin {
                    jvm()
                    linuxX64()
                    sourceSets {
                        commonMain {
                            dependencies {
                                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${TestFixturesBuildConfig.KOTLINX_SERIALIZATION_VERSION}")
                            }
                        }
                    }
                }
            """.trimIndent()
            )
        }.use { project ->
            val projectRoot = GradleProjectRoot(project.pathString())

            val sourcesDir = with(ProgressReporter.PRINTLN) {
                sourcesService.resolveAndProcessProjectSources(projectRoot, ":", providerToIndex = getKoin().get<DeclarationSearch>())
            }

            assertNotNull(sourcesDir)
            val contents = indexService.listPackageContents(sourcesDir, "kotlinx.serialization.json")
            assertNotNull(contents, "Expected package contents for kotlinx.serialization.json")

            // Each symbol name must appear exactly once — deduplication spans all index dirs via MultiReader.
            val duplicates = contents.symbols.groupBy { it }.filter { it.value.size > 1 }.keys
            assertTrue(duplicates.isEmpty(), "Duplicate symbols in listPackageContents: $duplicates")
            // Sanity lower-bound: a regression returning an empty list must be caught.
            assertTrue(contents.symbols.size > 10, "Expected at least 10 symbols but got ${contents.symbols.size}")
        }
    }
}
