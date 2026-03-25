package dev.rnett.gradle.mcp.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

object FileUtils {
    private val LOGGER = LoggerFactory.getLogger(FileUtils::class.java)

    fun createSymbolicLink(link: Path, target: Path): Boolean {
        return try {
            Files.createSymbolicLink(link, target)
            LOGGER.trace("Created symbolic link from $link to $target")
            true
        } catch (e: Exception) {
            if (OS.isWindows && target.isDirectory()) {
                if (createJunction(link, target)) return true
                LOGGER.debug("Failed to create junction from $link to $target on Windows.")
                // Junction creation already logged error if it truly failed
            }
            LOGGER.error("Failed to create symbolic link from $link to $target", e)
            false
        }
    }

    fun createJunction(link: Path, target: Path): Boolean {
        if (!OS.isWindows) return false
        return try {
            link.deleteIfExists()
            val process = ProcessBuilder("cmd.exe", "/c", "mklink", "/j", link.absolutePathString(), target.absolutePathString())
                .start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                LOGGER.trace("Created junction from $link to $target")
                true
            } else {
                LOGGER.error("Failed to create junction from $link to $target with exit code $exitCode")
                false
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to create junction from $link to $target", e)
            false
        }
    }

    /**
     * Atomically moves [source] to [target] ONLY if [target] does not exist.
     * Returns true if moved, false if [target] already existed.
     * Discards [source] if [target] already exists (as per CAS protocol).
     */
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    suspend fun atomicMoveIfAbsent(source: Path, target: Path): Boolean = withContext(Dispatchers.IO) {
        if (target.exists()) {
            try {
                source.deleteRecursively()
            } catch (e: Exception) {
                LOGGER.warn("Failed to delete redundant source $source after CAS collision", e)
            }
            return@withContext false
        }

        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (e: java.nio.file.FileAlreadyExistsException) {
            // Another process beat us to it
            try {
                source.deleteRecursively()
            } catch (e2: Exception) {
                LOGGER.warn("Failed to delete redundant source $source after CAS collision (FileAlreadyExistsException)", e2)
            }
            false
        } catch (e: Exception) {
            // Check if it exists now, maybe another process moved it while we were trying
            if (target.exists()) {
                try {
                    source.deleteRecursively()
                } catch (e2: Exception) {
                    LOGGER.warn("Failed to delete redundant source $source after CAS collision (catch Exception)", e2)
                }
                false
            } else {
                throw e
            }
        }
    }

    /**
     * Atomically replaces [target] with [source] by moving it.
     * On Windows, this is particularly tricky if [target] is a non-empty directory.
     */
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    suspend fun atomicReplaceDirectory(source: Path, target: Path) {
        withContext(Dispatchers.IO) {
            val tempOld = target.resolveSibling("${target.fileName}.old.${java.util.UUID.randomUUID()}")
            var movedToTemp = false

            try {
                if (target.exists()) {
                    try {
                        Files.move(target, tempOld, StandardCopyOption.ATOMIC_MOVE)
                        movedToTemp = true
                    } catch (e: Exception) {
                        LOGGER.warn("Failed to move $target to $tempOld for atomic replacement, falling back to delete-then-move", e)
                        target.deleteRecursively()
                    }
                }

                try {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
                }

                if (movedToTemp) {
                    try {
                        tempOld.deleteRecursively()
                    } catch (e: Exception) {
                        LOGGER.warn("Failed to delete temporary directory $tempOld after successful move", e)
                    }
                }
            } catch (e: Exception) {
                // Try to restore if we moved to temp but failed to move source
                if (movedToTemp && !target.exists()) {
                    try {
                        Files.move(tempOld, target, StandardCopyOption.ATOMIC_MOVE)
                    } catch (e2: Exception) {
                        LOGGER.error("Failed to restore $target from $tempOld after failed replacement", e2)
                    }
                }
                throw e
            }
        }
    }
}
