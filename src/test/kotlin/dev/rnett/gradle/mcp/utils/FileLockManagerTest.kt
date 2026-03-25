package dev.rnett.gradle.mcp.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalPathApi::class)
class FileLockManagerTest {

    private lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        tempDir = createTempDirectory("file-lock-manager-test")
    }

    @AfterEach
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
    fun `withLock supports re-entrancy for nested calls`() = runTest {
        val lockFile = tempDir.resolve("reentrant.lock")
        val timeSource = TestTimeSource(testScheduler)
        val dispatcher = StandardTestDispatcher(testScheduler)
        var nestedExecuted = false

        FileLockManager.withLock(lockFile, timeSource = timeSource, dispatcher = dispatcher) {
            // Nested call should succeed immediately without re-acquiring the file lock
            FileLockManager.withLock(lockFile, timeSource = timeSource, dispatcher = dispatcher) {
                nestedExecuted = true
            }
        }

        assertEquals(true, nestedExecuted)
    }

    @Test
    fun `withLock re-entrancy respects hierarchy and isolation`() = runTest {
        val lockFile = tempDir.resolve("hierarchy.lock")
        val timeSource = TestTimeSource(testScheduler)
        val dispatcher = StandardTestDispatcher(testScheduler)
        var counter = 0

        // Parent doesn't hold the lock
        coroutineScope {
            val child1 = async(dispatcher) {
                FileLockManager.withLock(lockFile, timeSource = timeSource, dispatcher = dispatcher) {
                    delay(100)
                    counter++
                    // Nested re-entrant call
                    FileLockManager.withLock(lockFile, timeSource = timeSource, dispatcher = dispatcher) {
                        delay(100)
                        counter++
                    }
                }
            }

            val child2 = async(dispatcher) {
                // Child 2 should wait for Child 1 to release the lock, because Parent didn't hold it
                delay(50) // Ensure Child 1 starts first
                FileLockManager.withLock(lockFile, timeSource = timeSource, dispatcher = dispatcher) {
                    counter++
                }
            }

            child1.await()
            child2.await()
        }

        assertEquals(3, counter)
    }

    @Test
    fun `withLock fails on lock upgrade`() = runTest {
        val lockFile = tempDir.resolve("upgrade.lock")
        val timeSource = TestTimeSource(testScheduler)
        val dispatcher = StandardTestDispatcher(testScheduler)

        FileLockManager.withLock(lockFile, shared = true, timeSource = timeSource, dispatcher = dispatcher) {
            // Attempting to upgrade to exclusive should fail
            assertFailsWith<IOException> {
                FileLockManager.withLock(lockFile, shared = false, timeSource = timeSource, dispatcher = dispatcher) {
                    // Should not reach here
                }
            }
        }
    }
}
