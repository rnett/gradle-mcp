package dev.rnett.gradle.mcp.dependencies

import kotlinx.coroutines.test.runTest
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
import kotlin.test.Test
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
                with(dev.rnett.gradle.mcp.ProgressReporter { progress, total, message ->
                    progressUpdates.add(progress to message)
                }) {
                    ArchiveExtractor.extractInto(target, ZipInputStream(it))
                }
            }

            assertTrue(target.resolve("file1.txt").exists())
            assertEquals("content1", target.resolve("file1.txt").readText())
            assertTrue(target.resolve("dir/file2.txt").exists())
            assertEquals("content2", target.resolve("dir/file2.txt").readText())

            // It might be 3 if "dir/" is also an entry, let's see.
            // In my case I only put file1.txt and dir/file2.txt.
            // ZipOutputStream might not create "dir/" entry automatically if I don't ask for it, 
            // but nextEntry will find dir/file2.txt.

            // Actually, if I didn't add "dir/", it might only be 2.
            assertTrue(progressUpdates.size >= 2, "Expected at least 2 progress updates, got ${progressUpdates.size}")
            assertTrue(progressUpdates.any { it.first == 1.0 && it.second!!.contains("file1.txt") }, "Missing update for file1.txt")
            assertTrue(progressUpdates.any { it.first == 2.0 && it.second!!.contains("dir/file2.txt") }, "Missing update for dir/file2.txt")
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
                with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
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
}
