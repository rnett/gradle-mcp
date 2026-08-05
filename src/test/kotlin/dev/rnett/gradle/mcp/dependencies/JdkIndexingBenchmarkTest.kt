package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.search.DefaultIndexService
import dev.rnett.gradle.mcp.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.dependencies.search.markerFileName
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.minutes
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("benchmark")
@EnabledIfEnvironmentVariable(named = "RUN_JDK_INDEX_BENCHMARK", matches = "true")
class JdkIndexingBenchmarkTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reports first plain read and first search indexing baselines`() = runTest(timeout = 5.minutes) {
        val environment = GradleMcpEnvironment(tempDir.resolve("working"))
        val storageService = DefaultSourceStorageService(environment)
        val provider = FullTextSearch()
        val indexService = DefaultIndexService(environment, listOf(provider))
        val service = DefaultJdkSourceService(storageService, indexService)
        var resolved: dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir? = null

        val firstReadMillis = measureTimeMillis {
            resolved = with(ProgressReporter.NONE) {
                service.resolveSources(System.getProperty("java.home"))
            }
        }
        val casDir = assertNotNull(resolved, "The benchmark requires a JDK installation with src.zip")
        assertFalse(casDir.index.resolve(provider.markerFileName).exists(), "Plain JDK resolution must not index")

        val firstSearchIndexMillis = measureTimeMillis {
            with(ProgressReporter.NONE) { service.ensureIndexed(casDir, provider) }
        }

        assertTrue(casDir.index.resolve(provider.markerFileName).exists(), "Search indexing must publish its completion marker")
        println("JDK indexing benchmark: first plain read=${firstReadMillis}ms, first search index=${firstSearchIndexMillis}ms")
    }
}
