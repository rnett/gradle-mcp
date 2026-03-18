package dev.rnett.gradle.mcp.dependencies

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
    suspend fun extractInto(
        target: Path,
        archivePath: Path,
        skipSingleFirstDir: Boolean = true,
        writeFiles: Boolean = true,
        onFileExtracted: (suspend (String, ByteArray) -> Unit)? = null
    ) {
        ZipFile(archivePath.toFile()).use { zip ->
            val total = zip.size().toDouble()

            var singleFirstDirPrefix: String? = null
            if (skipSingleFirstDir) {
                singleFirstDirPrefix = calculateSingleFirstDirPrefix(zip)
            }

            extractInternal(target, skipSingleFirstDir, writeFiles, total, singleFirstDirPrefix, onFileExtracted) { block ->
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
    suspend fun extractInto(
        target: Path,
        archiveStream: ZipInputStream,
        skipSingleFirstDir: Boolean = true,
        writeFiles: Boolean = true,
        onFileExtracted: (suspend (String, ByteArray) -> Unit)? = null
    ) {
        if (skipSingleFirstDir && onFileExtracted != null) {
            LOGGER.warn("extractInto called with ZipInputStream, skipSingleFirstDir=true, and a callback. Paths passed to the callback will NOT be shifted.")
        }
        extractInternal(target, skipSingleFirstDir, writeFiles, null, null, onFileExtracted) { block ->
            var entry = archiveStream.nextEntry
            while (entry != null) {
                block(entry, archiveStream)
                entry = archiveStream.nextEntry
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    suspend fun extract(
        archivePath: Path,
        skipSingleFirstDir: Boolean = true,
        onFileExtracted: suspend (String, ByteArray) -> Unit
    ) {
        ZipFile(archivePath.toFile()).use { zip ->
            val total = zip.size().toDouble()

            var singleFirstDirPrefix: String? = null
            if (skipSingleFirstDir) {
                singleFirstDirPrefix = calculateSingleFirstDirPrefix(zip)
            }

            extractInternal(null, skipSingleFirstDir, false, total, singleFirstDirPrefix, onFileExtracted) { block ->
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
    suspend fun extract(
        archiveStream: ZipInputStream,
        skipSingleFirstDir: Boolean = true,
        onFileExtracted: suspend (String, ByteArray) -> Unit
    ) {
        if (skipSingleFirstDir) {
            LOGGER.warn("extract called with ZipInputStream, skipSingleFirstDir=true. Paths passed to the callback will NOT be shifted.")
        }
        extractInternal(null, skipSingleFirstDir, false, null, null, onFileExtracted) { block ->
            var entry = archiveStream.nextEntry
            while (entry != null) {
                block(entry, archiveStream)
                entry = archiveStream.nextEntry
            }
        }
    }

    private fun calculateSingleFirstDirPrefix(zip: ZipFile): String? {
        val topLevelDirs = mutableSetOf<String>()
        val entries = zip.entries()
        var hasFilesAtRoot = false
        while (entries.hasMoreElements()) {
            val name = entries.nextElement().name
            val slashIdx = name.indexOf('/')
            if (slashIdx == -1 && name.isNotEmpty()) {
                hasFilesAtRoot = true
                break
            }
            if (slashIdx != -1) {
                topLevelDirs.add(name.substring(0, slashIdx + 1))
            }
        }
        if (!hasFilesAtRoot && topLevelDirs.size == 1) {
            return topLevelDirs.first()
        }
        return null
    }

    @OptIn(ExperimentalPathApi::class)
    context(progress: ProgressReporter)
    private suspend fun extractInternal(
        target: Path?,
        skipSingleFirstDir: Boolean,
        writeFiles: Boolean,
        total: Double?,
        singleFirstDirPrefix: String?,
        onFileExtracted: (suspend (String, ByteArray) -> Unit)?,
        entriesProcessor: suspend (block: suspend (ZipEntry, InputStream) -> Unit) -> Unit
    ) {
        var tempTarget: Path? = null
        if (writeFiles) {
            @Suppress("BlockingMethodInNonBlockingContext")
            tempTarget = Files.createTempDirectory("gradle-mcp-extract-")
        }

        try {
            var entryCount = 0
            entriesProcessor { entry, input ->
                entryCount++
                progress.report(entryCount.toDouble(), total, "Extracting ${entry.name}")

                val finalPath = if (singleFirstDirPrefix != null && entry.name.startsWith(singleFirstDirPrefix)) {
                    entry.name.substring(singleFirstDirPrefix.length)
                } else {
                    entry.name
                }

                if (writeFiles) {
                    val targetDir = requireNotNull(tempTarget)
                    val outPath = targetDir.resolve(entry.name).normalize()
                    if (!outPath.startsWith(targetDir)) {
                        throw IllegalStateException("Archive entry attempts to escape destination: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outPath.createDirectories()
                    } else {
                        outPath.parent?.createDirectories()
                        if (onFileExtracted != null) {
                            // If we need the content for a callback AND we are writing to disk,
                            // we have to read it. We'll read it once.
                            val bytes = input.readAllBytes()
                            outPath.outputStream().use { out -> out.write(bytes) }
                            onFileExtracted.invoke(finalPath, bytes)
                        } else {
                            outPath.outputStream().use { out -> input.copyTo(out) }
                        }
                    }
                } else {
                    if (!entry.isDirectory && onFileExtracted != null) {
                        onFileExtracted.invoke(finalPath, input.readAllBytes())
                    }
                }
            }

            if (writeFiles) {
                val finalTarget = requireNotNull(target)
                finalTarget.toFile().parentFile.mkdirs()

                if (finalTarget.exists()) {
                    finalTarget.deleteRecursively()
                }

                if (skipSingleFirstDir) {
                    val targetDir = requireNotNull(tempTarget)
                    val child = targetDir.listDirectoryEntries().filter { !it.name.startsWith(".") }.singleOrNull()
                    if (child != null && child.isDirectory()) {
                        child.moveTo(finalTarget, StandardCopyOption.REPLACE_EXISTING)
                    } else {
                        targetDir.moveTo(finalTarget, StandardCopyOption.REPLACE_EXISTING)
                    }
                } else {
                    requireNotNull(tempTarget).moveTo(finalTarget, StandardCopyOption.REPLACE_EXISTING)
                }

                if (tempTarget.exists()) {
                    check(tempTarget.listDirectoryEntries().filter { !it.name.startsWith(".") }.isEmpty())
                }
            }
        } finally {
            if (tempTarget != null) {
                try {
                    tempTarget.deleteRecursively()
                } catch (e: Exception) {
                    LOGGER.warn("Failed to delete temporary extraction directory $tempTarget", e)
                }
            }
        }
    }
}
