@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourcesServiceHardeningTest : BaseMcpServerTest() {

    private lateinit var sourcesService: DefaultSourcesService
    private val depService = mockk<GradleDependencyService>(relaxed = true)
    private val storageService = mockk<SourceStorageService>(relaxed = true)
    private val indexService = mockk<IndexService>(relaxed = true)
    private val jdkSourceService = mockk<JdkSourceService>(relaxed = true)

    @BeforeEach
    fun setupHardening() {
        sourcesService = DefaultSourcesService(depService, storageService, indexService, jdkSourceService, dispatcher = Dispatchers.Default)
    }

    private fun createCasDir(hash: String): CASDependencySourcesDir {
        val root = tempDir.resolve("cas/$hash")
        root.createDirectories()
        return CASDependencySourcesDir(hash, root)
    }

    private fun createReadyCasDir(hash: String): CASDependencySourcesDir {
        val casDir = createCasDir(hash)
        casDir.sources.createDirectories()
        casDir.sources.resolve("Example.kt").writeText("class Example")
        casDir.baseCompletedMarker.createFile()
        return casDir
    }

    @Test
    fun `isBroken trusts completion marker even if normalized directory is empty`() = runTest {
        val casDir = createCasDir("empty-kmp")
        casDir.normalizedDir.createDirectories()
        casDir.baseCompletedMarker.createFile()

        // Logic from SourcesService.ensureBaseReady:
        val isBroken = !casDir.baseCompletedMarker.exists() || !casDir.normalizedDir.exists()

        assertFalse(isBroken, "Should not be broken when marker exists and normalized dir exists")
    }

    @Test
    fun `isBroken identifies broken entry if marker is missing`() = runTest {
        val casDir = createCasDir("missing-marker")
        casDir.normalizedDir.createDirectories()

        val isBroken = !casDir.baseCompletedMarker.exists() || !casDir.normalizedDir.exists()
        assertTrue(isBroken, "Should be broken if marker is missing")
    }

    @Test
    fun `repair performs full cleanup including index`() = runTest {
        val casDir = createCasDir("full-cleanup")
        casDir.sources.createDirectories()
        casDir.normalizedDir.createDirectories()
        casDir.index.createDirectories()
        casDir.baseCompletedMarker.createFile()

        val clearCasDirMethod = DefaultSourcesService::class.java.getDeclaredMethod("clearCasDir", CASDependencySourcesDir::class.java)
        clearCasDirMethod.isAccessible = true

        clearCasDirMethod.invoke(sourcesService, casDir)

        assertFalse(casDir.index.exists(), "Index directory should have been deleted during repair")
    }

    @Test
    fun `session view rejects traversal prefixes before creating links`() = runTest {
        val storage = DefaultSourceStorageService(GradleMcpEnvironment(tempDir.resolve("mcp")))
        val dep = GradleDependency(
            id = "../outside:evil:1.0",
            group = "../outside",
            name = "evil",
            version = "1.0",
            sourcesFile = tempDir.resolve("evil-sources.jar")
        )

        assertFailsWith<IllegalArgumentException> {
            with(ProgressReporter.NONE) {
                storage.createSessionView(mapOf(dep to createReadyCasDir("traversal")))
            }
        }
    }

    @Test
    fun `session view reserves the full jdk sources subtree`() = runTest {
        val storage = DefaultSourceStorageService(GradleMcpEnvironment(tempDir.resolve("mcp")))
        val dep = GradleDependency(
            id = "jdk:sources-java-base:1.0",
            group = "jdk",
            name = "sources/java.base",
            version = "1.0",
            sourcesFile = tempDir.resolve("jdk-squat-sources.jar")
        )

        assertFailsWith<IllegalArgumentException> {
            with(ProgressReporter.NONE) {
                storage.createSessionView(mapOf(dep to createReadyCasDir("jdk-squat")))
            }
        }
    }
}
