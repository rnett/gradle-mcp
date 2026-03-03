package dev.rnett.gradle.mcp.gradle.dependencies

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.notExists
import kotlin.io.path.outputStream

object ArchiveExtractor {
    private val LOGGER = LoggerFactory.getLogger(ArchiveExtractor::class.java)

    @OptIn(ExperimentalPathApi::class)
    fun extractInto(target: Path, archiveStream: ZipInputStream, skipSingleFirstDir: Boolean = true) {
        val tempTarget = Files.createTempDirectory("gradle-mcp-extract-")

        try {
            archiveStream.use { tis ->
                var entry = tis.nextEntry
                while (entry != null) {
                    val outPath = tempTarget.resolve(entry.name).normalize()
                    if (!outPath.startsWith(tempTarget)) {
                        throw IllegalStateException("Archive entry attempts to escape destination: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outPath.createDirectories()
                    } else {
                        outPath.parent?.createDirectories()
                        outPath.outputStream().use { out ->
                            tis.copyTo(out)
                        }
                    }
                    entry = tis.nextEntry
                }
            }

            target.createParentDirectories()

            if (skipSingleFirstDir) {
                val child = tempTarget.listDirectoryEntries().singleOrNull()
                if (child != null && child.isDirectory()) {
                    child.moveTo(target, StandardCopyOption.REPLACE_EXISTING)
                } else {
                    tempTarget.moveTo(target, StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                tempTarget.moveTo(target, StandardCopyOption.REPLACE_EXISTING)
            }

            check(tempTarget.notExists() || tempTarget.listDirectoryEntries().isEmpty())
        } finally {
            try {
                tempTarget.deleteRecursively()
            } catch (e: Exception) {
                LOGGER.warn("Failed to delete temporary extraction directory $tempTarget", e)
            }
        }
    }
}