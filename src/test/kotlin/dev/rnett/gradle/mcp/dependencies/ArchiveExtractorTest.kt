package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class ArchiveExtractorTest {

    @Test
    fun `test extraction with progress`() = runTest {
        val tempDir = createTempDirectory("archive-extractor-test")
        try {
            val target = tempDir.resolve("target")
            val zipFile = tempDir.resolve("test.zip")

            zipFile.outputStream().use { fos ->
                ZipOutputStream(fos).use { zos ->
                    zos.putNextEntry(ZipEntry("file1.txt"))
                    zos.write("content1".toByteArray())
                    zos.closeEntry()
                    zos.putNextEntry(ZipEntry("dir/file2.txt"))
                    zos.write("content2".toByteArray())
                    zos.closeEntry()
                }
            }

            val progressUpdates = mutableListOf<Pair<Double, String?>>()
            zipFile.inputStream().buffered().use {
                with(ProgressReporter { progress, _, message ->
                    progressUpdates.add(progress to message)
                }) {
                    ArchiveExtractor.extractInto(target, ZipInputStream(it))
                }
            }

            assertTrue(target.resolve("file1.txt").exists())
            assertEquals("content1", target.resolve("file1.txt").readText())
            assertTrue(target.resolve("dir/file2.txt").exists())
            assertEquals("content2", target.resolve("dir/file2.txt").readText())

            assertTrue(progressUpdates.isEmpty(), "Expected NO progress updates from ArchiveExtractor, got ${progressUpdates.size}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `test extraction skipping single first dir`() = runTest {
        val tempDir = createTempDirectory("archive-extractor-test-skip")
        try {
            val target = tempDir.resolve("target")
            val zipFile = tempDir.resolve("test-skip.zip")

            zipFile.outputStream().use { fos ->
                ZipOutputStream(fos).use { zos ->
                    zos.putNextEntry(ZipEntry("root/file1.txt"))
                    zos.write("content1".toByteArray())
                    zos.closeEntry()
                }
            }

            zipFile.inputStream().buffered().use {
                with(ProgressReporter.PRINTLN) {
                    ArchiveExtractor.extractInto(target, ZipInputStream(it), skipSingleFirstDir = true)
                }
            }

            assertTrue(target.resolve("file1.txt").exists())
            assertEquals("content1", target.resolve("file1.txt").readText())
            assertTrue(!target.resolve("root").exists() || target == target.resolve("root")) // target should contain file1.txt directly
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `test extract without target directory`() = runTest {
        val tempDir = createTempDirectory("archive-extractor-test-no-target")
        try {
            val zipFile = tempDir.resolve("test-no-target.zip")

            zipFile.outputStream().use { fos ->
                ZipOutputStream(fos).use { zos ->
                    zos.putNextEntry(ZipEntry("file1.txt"))
                    zos.write("content1".toByteArray())
                    zos.closeEntry()
                    zos.putNextEntry(ZipEntry("dir/file2.txt"))
                    zos.write("content2".toByteArray())
                    zos.closeEntry()
                }
            }

            val extractedFiles = mutableMapOf<String, String>()
            with(ProgressReporter.PRINTLN) {
                ArchiveExtractor.extract(zipFile) { path, bytes ->
                    extractedFiles[path] = String(bytes)
                }
            }

            assertEquals(2, extractedFiles.size)
            assertEquals("content1", extractedFiles["file1.txt"])
            assertEquals("content2", extractedFiles["dir/file2.txt"])
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
