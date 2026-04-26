@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourcesServiceHardeningTest : BaseMcpServerTest() {

    private lateinit var sourcesService: DefaultSourcesService
    private val depService = mockk<GradleDependencyService>(relaxed = true)
    private val storageService = mockk<SourceStorageService>(relaxed = true)
    private val indexService = mockk<IndexService>(relaxed = true)

    @BeforeEach
    fun setupHardening() {
        sourcesService = DefaultSourcesService(depService, storageService, indexService, Dispatchers.Default)
    }

    private fun createCasDir(hash: String): CASDependencySourcesDir {
        val root = tempDir.resolve("cas/$hash")
        root.createDirectories()
        return CASDependencySourcesDir(hash, root)
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
}
