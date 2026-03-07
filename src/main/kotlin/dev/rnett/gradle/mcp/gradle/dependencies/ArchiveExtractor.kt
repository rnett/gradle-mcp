package dev.rnett.gradle.mcp.gradle.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.outputStream

object ArchiveExtractor {
    private val LOGGER = LoggerFactory.getLogger(ArchiveExtractor::class.java)

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    fun extractInto(target: Path, archivePath: Path, skipSingleFirstDir: Boolean = true) {
        ZipFile(archivePath.toFile()).use { zip ->
            val total = zip.size().toDouble()
            extractIntoInternal(target, skipSingleFirstDir, total) { block ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    zip.getInputStream(entry).use { input ->
                        block(entry, input)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    fun extractInto(target: Path, archiveStream: ZipInputStream, skipSingleFirstDir: Boolean = true) {
        extractIntoInternal(target, skipSingleFirstDir, null) { block ->
            var entry = archiveStream.nextEntry
            while (entry != null) {
                block(entry, archiveStream)
                entry = archiveStream.nextEntry
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    private fun extractIntoInternal(
        target: Path,
        skipSingleFirstDir: Boolean,
        total: Double?,
        entriesProcessor: (block: (ZipEntry, InputStream) -> Unit) -> Unit
    ) {
        val tempTarget = Files.createTempDirectory("gradle-mcp-extract-")

        try {
            var entryCount = 0
            entriesProcessor { entry, input ->
                entryCount++
                progress(entryCount.toDouble(), total, "Extracting ${entry.name}")
                val outPath = tempTarget.resolve(entry.name).normalize()
                if (!outPath.startsWith(tempTarget)) {
                    throw IllegalStateException("Archive entry attempts to escape destination: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outPath.createDirectories()
                } else {
                    outPath.parent?.createDirectories()
                    outPath.outputStream().use { out ->
                        input.copyTo(out)
                    }
                }
            }

            target.createParentDirectories()

            if (target.exists()) {
                target.deleteRecursively()
            }

            if (skipSingleFirstDir) {
                val child = tempTarget.listDirectoryEntries().filter { !it.name.startsWith(".") }.singleOrNull()
                if (child != null && child.isDirectory()) {
                    child.moveTo(target, StandardCopyOption.REPLACE_EXISTING)
                } else {
                    tempTarget.moveTo(target, StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                tempTarget.moveTo(target, StandardCopyOption.REPLACE_EXISTING)
            }

            if (tempTarget.exists()) {
                check(tempTarget.listDirectoryEntries().filter { !it.name.startsWith(".") }.isEmpty())
            }
        } finally {
            try {
                tempTarget.deleteRecursively()
            } catch (e: Exception) {
                LOGGER.warn("Failed to delete temporary extraction directory $tempTarget", e)
            }
        }
    }
}
