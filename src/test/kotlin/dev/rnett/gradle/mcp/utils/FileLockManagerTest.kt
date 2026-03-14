package dev.rnett.gradle.mcp.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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

    private class TestTimeSource(private val scheduler: TestCoroutineScheduler) : TimeSource {
        override fun markNow(): TimeMark = object : TimeMark {
            val start = scheduler.currentTime
            override fun elapsedNow(): Duration = (scheduler.currentTime - start).milliseconds
        }
    }

    @Test
    fun `withLock acquires lock and executes action`() = runTest {
        val lockFile = tempDir.resolve("test.lock")
        var executed = false
        FileLockManager.withLock(lockFile, timeSource = TestTimeSource(testScheduler), dispatcher = StandardTestDispatcher(testScheduler)) {
            executed = true
        }
        assertEquals(true, executed)
    }

    @Test
    fun `withLock prevents concurrent access`() = runTest {
        val lockFile = tempDir.resolve("concurrent.lock")
        var counter = 0
        val timeSource = TestTimeSource(testScheduler)
        val dispatcher = StandardTestDispatcher(testScheduler)

        val jobs = List(5) {
            async(dispatcher) {
                FileLockManager.withLock(lockFile, timeSource = timeSource, dispatcher = dispatcher) {
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
        val timeSource = TestTimeSource(testScheduler)
        val dispatcher = StandardTestDispatcher(testScheduler)

        val lockAcquired = CompletableDeferred<Unit>()
        val job = async(dispatcher) {
            FileLockManager.withLock(lockFile, timeSource = timeSource, dispatcher = dispatcher) {
                lockAcquired.complete(Unit)
                delay(500)
            }
        }

        // Wait for job to start and acquire lock
        lockAcquired.await()

        assertFailsWith<IOException> {
            FileLockManager.withLock(lockFile, timeout = 100.milliseconds, timeSource = timeSource, dispatcher = dispatcher) {
                // Should not reach here
            }
        }

        job.await() // Wait for first job to finish
    }
}
