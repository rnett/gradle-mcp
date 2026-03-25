package dev.rnett.gradle.mcp.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

object FileLockManager {
    val LOGGER = LoggerFactory.getLogger(FileLockManager::class.java)

    /**
     * Executes the [action] with a file lock on the specified [lockFile].
     * Retries acquisition until [timeout] is reached.
     * If [shared] is true, multiple readers can hold the lock simultaneously.
     */
    suspend inline fun <T> withLock(
        lockFile: Path,
        timeout: Duration = 60.seconds,
        shared: Boolean = false,
        timeSource: TimeSource = TimeSource.Monotonic,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        crossinline action: suspend () -> T
    ): T {
        val absolutePath = lockFile.toAbsolutePath()

        val start = timeSource.markNow()
        var lastLog = timeSource.markNow()
        val originalContext = currentCoroutineContext()

        return withContext(dispatcher) {
            var channel: FileChannel? = null
            var lock: FileLock? = null
            try {
                while (lock == null) {
                    try {
                        if (channel == null || !channel.isOpen) {
                            absolutePath.parent.createDirectories()
                            channel = FileChannel.open(
                                absolutePath,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.READ,
                                StandardOpenOption.WRITE
                            )
                        }
                        lock = channel!!.tryLock(0L, Long.MAX_VALUE, shared)
                    } catch (e: OverlappingFileLockException) {
                        // Lock held by another thread in the same JVM
                    } catch (e: IOException) {
                        // On Windows, this can happen if the file is being deleted or other transient issues
                        val msg = e.message?.lowercase() ?: ""
                        if (!msg.contains("access is denied") && !msg.contains("no such file")) {
                            throw e
                        }
                        // Close channel on IO exception so we retry opening it
                        channel?.close()
                        channel = null
                    }

                    if (lock == null) {
                        if (start.elapsedNow() > timeout) {
                            throw IOException("Failed to acquire ${if (shared) "shared" else "exclusive"} lock on $absolutePath after ${timeout.inWholeSeconds}s")
                        }

                        if (lastLog.elapsedNow() > 5.seconds) {
                            LOGGER.info("Still waiting for ${if (shared) "shared" else "exclusive"} lock on $absolutePath... (${start.elapsedNow().inWholeSeconds}s elapsed)")
                            lastLog = timeSource.markNow()
                        }

                        delay(500)
                    }
                }

                LOGGER.debug("Acquired {} lock on {}", if (shared) "shared" else "exclusive", absolutePath)
                return@withContext withContext(originalContext) { action() }
            } finally {
                lock?.release()
                channel?.close()
                LOGGER.debug("Released {} lock on {}", if (shared) "shared" else "exclusive", absolutePath)
            }
        }
    }

    /**
     * Attempts to acquire a non-blocking advisory lock on [lockFile].
     * Returns an [AdvisoryLock] if successful, or null if denied.
     * The lock is released when [AdvisoryLock.close] is called.
     */
    fun tryLockAdvisory(lockFile: Path): AdvisoryLock? {
        val absolutePath = lockFile.toAbsolutePath()
        try {
            absolutePath.parent.createDirectories()
            val channel = FileChannel.open(
                absolutePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
            )
            val lock = try {
                channel.tryLock()
            } catch (e: OverlappingFileLockException) {
                null
            } catch (e: Exception) {
                null
            }

            return if (lock != null) {
                AdvisoryLock(channel, lock)
            } else {
                channel.close()
                null
            }
        } catch (e: Exception) {
            return null
        }
    }
}

/**
 * A wrapper for a [FileLock] and its associated [FileChannel] that implements [AutoCloseable].
 */
class AdvisoryLock(private val channel: FileChannel, private val lock: FileLock) : AutoCloseable {
    override fun close() {
        try {
            lock.release()
        } catch (e: Exception) {
            // Ignored on close
        } finally {
            channel.close()
        }
    }
}


