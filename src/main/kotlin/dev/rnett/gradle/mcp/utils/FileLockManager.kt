package dev.rnett.gradle.mcp.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createDirectories

object FileLockManager {
    private val LOGGER = LoggerFactory.getLogger(FileLockManager::class.java)

    /**
     * Executes the [action] with a file lock on the specified [lockFile].
     * Retries acquisition until [timeout] is reached.
     * If [shared] is true, multiple readers can hold the lock simultaneously.
     */
    suspend fun <T> withLock(
        lockFile: Path,
        timeout: Duration = Duration.ofSeconds(60),
        shared: Boolean = false,
        action: suspend () -> T
    ): T {
        val absolutePath = lockFile.toAbsolutePath()
        absolutePath.parent.createDirectories()

        val start = Instant.now()
        var lastLog = Instant.now()

        return withContext(Dispatchers.IO) {
            FileChannel.open(
                absolutePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
            ).use { channel ->
                var lock: FileLock? = null
                while (lock == null) {
                    try {
                        lock = channel.tryLock(0L, Long.MAX_VALUE, shared)
                    } catch (e: OverlappingFileLockException) {
                        // Lock held by another thread in the same JVM
                    } catch (e: IOException) {
                        // On Windows, this can happen if the file is being deleted or other transient issues
                        if (e.message?.contains("Access is denied", ignoreCase = true) != true) {
                            throw e
                        }
                    }

                    if (lock == null) {
                        val now = Instant.now()
                        if (Duration.between(start, now) > timeout) {
                            throw IOException("Failed to acquire ${if (shared) "shared" else "exclusive"} lock on $absolutePath after ${timeout.toSeconds()}s")
                        }

                        if (Duration.between(lastLog, now) > Duration.ofSeconds(5)) {
                            LOGGER.info("Still waiting for ${if (shared) "shared" else "exclusive"} lock on $absolutePath... (${Duration.between(start, now).toSeconds()}s elapsed)")
                            lastLog = now
                        }

                        delay(100)
                    }
                }

                return@withContext try {
                    LOGGER.debug("Acquired {} lock on {}", if (shared) "shared" else "exclusive", absolutePath)
                    action()
                } finally {
                    lock.release()
                    LOGGER.debug("Released {} lock on {}", if (shared) "shared" else "exclusive", absolutePath)
                }
            }
        }
    }
}
