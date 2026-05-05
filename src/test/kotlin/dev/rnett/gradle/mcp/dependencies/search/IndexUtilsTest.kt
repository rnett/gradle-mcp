package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.ProgressReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IndexUtilsTest {

    @TempDir
    lateinit var tempDir: Path

    private val mockIndexService: IndexService = mockk(relaxed = true)
    private val mockProvider: SearchProvider = mockk(relaxed = true)

    private fun testIndex(indexBaseDir: Path) = Index(indexBaseDir.resolve("index"))

    @Test
    fun `indexSources handles empty source directory`() = runTest {
        val sourcesRoot = tempDir.resolve("empty").createDirectories()
        val indexBaseDir = tempDir.resolve("index")

        coEvery {
            with(any<ProgressReporter>()) { mockIndexService.indexFiles(indexBaseDir, any<Flow<IndexEntry>>(), mockProvider) }
        } returns testIndex(indexBaseDir)

        with(ProgressReporter.NONE) {
            indexSources(mockIndexService, indexBaseDir, sourcesRoot, mockProvider)
        }

        coVerify(exactly = 1) {
            with(any<ProgressReporter>()) {
                mockIndexService.indexFiles(indexBaseDir, any<Flow<IndexEntry>>(), mockProvider)
            }
        }
    }

    @Test
    fun `indexSources indexes only source files`() = runTest {
        val sourcesRoot = tempDir.resolve("sources").createDirectories()
        val indexBaseDir = tempDir.resolve("index")

        sourcesRoot.resolve("Foo.java").writeText("package p; class Foo {}")
        sourcesRoot.resolve("Bar.kt").writeText("package p; class Bar")
        sourcesRoot.resolve("readme.xml").writeText("<readme/>")
        sourcesRoot.resolve("notes.txt").writeText("notes")

        val entries = mutableListOf<IndexEntry>()
        coEvery {
            with(any<ProgressReporter>()) { mockIndexService.indexFiles(indexBaseDir, any<Flow<IndexEntry>>(), mockProvider) }
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val flow = invocation.args[2] as Flow<IndexEntry>
            flow.collect { entries.add(it) }
            testIndex(indexBaseDir)
        }

        with(ProgressReporter.NONE) {
            indexSources(mockIndexService, indexBaseDir, sourcesRoot, mockProvider)
        }

        val paths = entries.map { it.relativePath }
        assertTrue("Foo.java" in paths, "Should index .java files")
        assertTrue("Bar.kt" in paths, "Should index .kt files")
        assertTrue("readme.xml" !in paths, "Should NOT index .xml files")
        assertTrue("notes.txt" !in paths, "Should NOT index .txt files")
    }

    @Test
    fun `indexSources closes channel after walk`() = runTest {
        val sourcesRoot = tempDir.resolve("sources").createDirectories()
        val indexBaseDir = tempDir.resolve("index")

        sourcesRoot.resolve("Foo.java").writeText("package p; class Foo {}")

        val entries = mutableListOf<IndexEntry>()
        coEvery {
            with(any<ProgressReporter>()) { mockIndexService.indexFiles(indexBaseDir, any<Flow<IndexEntry>>(), mockProvider) }
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val flow = invocation.args[2] as Flow<IndexEntry>
            flow.collect { entries.add(it) }
            testIndex(indexBaseDir)
        }

        with(ProgressReporter.NONE) {
            indexSources(mockIndexService, indexBaseDir, sourcesRoot, mockProvider)
        }

        assertEquals(1, entries.size)
    }

    @Test
    fun `indexSources handles cancellation`() = runTest {
        val sourcesRoot = tempDir.resolve("sources").createDirectories()
        val indexBaseDir = tempDir.resolve("index")

        // Create many files to ensure the coroutine is still running when cancelled
        repeat(100) { i ->
            sourcesRoot.resolve("File$i.java").writeText("package p; class File$i {}")
        }

        coEvery {
            with(any<ProgressReporter>()) { mockIndexService.indexFiles(indexBaseDir, any<Flow<IndexEntry>>(), mockProvider) }
        } throws CancellationException("Cancelled")

        assertFailsWith<CancellationException> {
            with(ProgressReporter.NONE) {
                indexSources(mockIndexService, indexBaseDir, sourcesRoot, mockProvider)
            }
        }
    }

    @Test
    fun `indexSources handles error during indexFiles`() = runTest {
        val sourcesRoot = tempDir.resolve("sources").createDirectories()
        val indexBaseDir = tempDir.resolve("index")

        sourcesRoot.resolve("Foo.java").writeText("package p; class Foo {}")

        coEvery {
            with(any<ProgressReporter>()) { mockIndexService.indexFiles(indexBaseDir, any<Flow<IndexEntry>>(), mockProvider) }
        } throws RuntimeException("Indexing failed")

        val failure = assertFailsWith<RuntimeException> {
            with(ProgressReporter.NONE) {
                indexSources(mockIndexService, indexBaseDir, sourcesRoot, mockProvider)
            }
        }
        assertEquals("Indexing failed", failure.message)
    }
}
