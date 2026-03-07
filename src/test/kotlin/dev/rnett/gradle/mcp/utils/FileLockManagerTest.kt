package dev.rnett.gradle.mcp.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalPathApi::class)
class FileLockManagerTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("file-lock-manager-test")
    }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `withLock acquires lock and executes action`() = runTest {
        val lockFile = tempDir.resolve("test.lock")
        var executed = false
        FileLockManager.withLock(lockFile) {
            executed = true
        }
        assertEquals(true, executed)
    }

    @Test
    fun `withLock prevents concurrent access`() = runTest {
        val lockFile = tempDir.resolve("concurrent.lock")
        var counter = 0

        val jobs = List(5) {
            async(Dispatchers.IO) {
                FileLockManager.withLock(lockFile) {
                    val current = counter
                    delay(50)
                    counter = current + 1
                }
            }
        }

        jobs.awaitAll()
        assertEquals(5, counter)
    }

    @Test
    fun `withLock fails after timeout`() = runTest {
        val lockFile = tempDir.resolve("timeout.lock")

        val job = async(Dispatchers.IO) {
            FileLockManager.withLock(lockFile) {
                delay(500)
            }
        }

        // Wait for job to start and acquire lock
        delay(100)

        assertFailsWith<IOException> {
            FileLockManager.withLock(lockFile, timeout = Duration.ofMillis(100)) {
                // Should not reach here
            }
        }

        job.await() // Wait for first job to finish
    }
}
