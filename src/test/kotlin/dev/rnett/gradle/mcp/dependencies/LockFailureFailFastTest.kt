package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import dev.rnett.gradle.mcp.utils.FileLockManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LockFailureFailFastTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `test waitForBase failure detection`() = runTest {
        val env = GradleMcpEnvironment(tempDir.resolve("mcp"))
        val storageService = DefaultSourceStorageService(env)

        val hash = "test-hash"
        val casBaseDir = tempDir.resolve("cas").resolve(hash.take(2)).resolve(hash)
        val casDir = CASDependencySourcesDir(hash, casBaseDir)

        // Case 1: Successfully completed
        casDir.baseCompletedMarker.parent.createDirectories()
        casDir.baseCompletedMarker.writeText("")

        assertTrue(storageService.waitForBase(casDir), "Should return true if marker exists")

        // Case 2: Lock released but marker missing (Failure)
        casDir.baseCompletedMarker.deleteIfExists()
        val hash2 = "test-hash-2"
        val casBaseDir2 = tempDir.resolve("cas").resolve(hash2.take(2)).resolve(hash2)
        val casDir2 = CASDependencySourcesDir(hash2, casBaseDir2)

        FileLockManager.tryLockAdvisory(casDir2.baseLockFile)!!.use {
            // Simulated work that fails/crashes
            delay(100)
        }

        assertFalse(storageService.waitForBase(casDir2), "Should return false if lock released but marker missing")
    }
}
