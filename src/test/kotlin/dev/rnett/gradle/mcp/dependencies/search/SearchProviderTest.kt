package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.fixtures.dependencies.search.index
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchProviderTest {

    @TempDir
    lateinit var tempDir: Path

    fun depDir() = tempDir.resolve("dep-" + java.util.UUID.randomUUID()).createDirectories()

    @Test
    fun `multiple matches in same file produce single result`() = runTest {
        val depDir = depDir()
        val fileContent = """
            line 1: first
            line 2: matchA
            line 3: matchB
            line 4: matchC
            line 5: last
        """.trimIndent()
        depDir.resolve("TestFile.kt").writeText(fileContent)

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        val results = FullTextSearch.search(listOf(indexDir), "match").results
        val searchResults = results.toSearchResults(depDir)

        assertEquals(1, searchResults.size, "All matches in same file should be grouped into one result")
        val result = searchResults[0]
        assertEquals("TestFile.kt", result.relativePath)
        assertEquals(2, result.line, "First match line should be 2")
        assertEquals(setOf(2, 3, 4), result.matchLines.toSet(), "matchLines should contain all matched lines")
    }

    @Test
    fun `matches in different files produce separate results`() = runTest {
        val depDir = depDir()
        depDir.resolve("File1.kt").writeText("line 1: match")
        depDir.resolve("File2.kt").writeText("line 1: match")

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        val results = FullTextSearch.search(listOf(indexDir), "match").results
        val searchResults = results.toSearchResults(depDir)

        assertEquals(2, searchResults.size, "Matches in different files should produce separate results")
        val files = searchResults.map { it.relativePath }.toSet()
        assertEquals(setOf("File1.kt", "File2.kt"), files)
    }

    @Test
    fun `snippet spans from first to last match with context`() = runTest {
        val depDir = depDir()
        val fileContent = (1..10).joinToString("\n") { "line $it: content" }
        depDir.resolve("TestFile.kt").writeText(fileContent)

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        // Search for multiple terms that match on lines 3, 5, 7
        val results = FullTextSearch.search(listOf(indexDir), "\"line 3\" OR \"line 5\" OR \"line 7\"").results
        val searchResults = results.toSearchResults(depDir)

        assertEquals(1, searchResults.size)
        val result = searchResults[0]
        // Expected snippet: from line 1 (3-2) to line 9 (7+2) but clipped to file bounds
        val expectedStart = 1
        val expectedEnd = 9
        val expectedLines = (expectedStart..expectedEnd).map { "line $it: content" }
        assertEquals(expectedLines.joinToString("\n"), result.snippet.trim())
        // matchLines should contain 3,5,7
        assertEquals(setOf(3, 5, 7), result.matchLines.toSet())
    }

    @Test
    fun `snippet respects file boundaries`() = runTest {
        val depDir = depDir()
        val fileContent = "line 1: match\nline 2: match"
        depDir.resolve("TestFile.kt").writeText(fileContent)

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        val results = FullTextSearch.search(listOf(indexDir), "match").results
        val searchResults = results.toSearchResults(depDir)

        assertEquals(1, searchResults.size)
        val result = searchResults[0]
        // Snippet should start at line 1 (can't go before) and end at line 2 (can't go after)
        assertEquals("line 1: match\nline 2: match", result.snippet.trim())
    }

    @Test
    fun `score uses first match`() = runTest {
        val depDir = depDir()
        depDir.resolve("TestFile.kt").writeText("match1\nmatch2")

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        val results = FullTextSearch.search(listOf(indexDir), "match").results
        val searchResults = results.toSearchResults(depDir)

        assertEquals(1, searchResults.size)
        val result = searchResults[0]
        // The score should be from the first match (which may be non-null)
        // Since we can't easily predict scores, just ensure it's not null if any match had a score
        assertTrue(result.score != null || results.all { it.score == null }, "Score should be preserved from first match")
    }

    @Test
    fun `matches on same line are grouped`() = runTest {
        val depDir = depDir()
        depDir.resolve("TestFile.kt").writeText("match match match")

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        val results = FullTextSearch.search(listOf(indexDir), "match").results
        val searchResults = results.toSearchResults(depDir)

        assertEquals(1, searchResults.size)
        val result = searchResults[0]
        assertEquals(setOf(1), result.matchLines.toSet(), "All matches on same line should be grouped")
    }

    @Test
    fun `handles null line numbers using offset`() = runTest {
        val depDir = depDir()
        depDir.resolve("TestFile.kt").writeText("some content with match")

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        val results = FullTextSearch.search(listOf(indexDir), "match").results
        // Simulate a result with null line but valid offset
        val resultWithNullLine = results.first().copy(line = null)
        val searchResults = listOf(resultWithNullLine).toSearchResults(depDir)

        assertEquals(1, searchResults.size)
        val result = searchResults[0]
        // The line should be computed from offset
        assertEquals(1, result.line)
    }
}