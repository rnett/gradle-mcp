package dev.rnett.gradle.mcp.utils

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
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
                LOGGER.debug("Failed to create symbolic link from $link to $target on Windows. Falling back to junction.")
                createJunction(link, target)
            } else {
                LOGGER.error("Failed to create symbolic link from $link to $target", e)
                false
            }
        }
    }

    fun createJunction(link: Path, target: Path): Boolean {
        if (!OS.isWindows) return false
        return try {
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
}
