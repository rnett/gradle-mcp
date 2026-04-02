package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.fixtures.dependencies.search.index
import kotlinx.coroutines.test.runTest
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.store.FSDirectory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FullTextSearchTest {

    @TempDir
    lateinit var tempDir: Path

    fun depDir() = tempDir.resolve("dep-" + Uuid.random()).createDirectories()

    @Test
    fun `search returns correct line numbers`() = runTest {
        val depDir = depDir()
        val fileContent = """
            line 0: zero
            line 1: match
            line 2: two
            line 3: match
            line 4: four
        """.trimIndent()
        depDir.resolve("TestFile.kt").writeText(fileContent)

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        val results = FullTextSearch.search(listOf(indexDir), "match").results
        val searchResults = results.toSearchResults(depDir)
        // With file-level grouping, all matches in the same file are combined into one result
        assertEquals(1, searchResults.size, "Should find 1 result after grouping matches from the same file")

        val result = searchResults[0]
        assertEquals("TestFile.kt", result.relativePath)
        assertEquals(2, result.line, "First match line should be 2")
        assertEquals(setOf(2, 4), result.matchLines.toSet(), "matchLines should contain both matched lines")
    }

    @Test
    fun `search handles word boundaries`() = runTest {
        val depDir = depDir()
        depDir.resolve("File.kt").writeText(
            """
            val camelCase = 1
            val snake_case = 2
            val withNumbers123 = 3
        """.trimIndent()
        )

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        // camelCase
        val camel = FullTextSearch.search(listOf(indexDir), "camel").results.toSearchResults(depDir)
        assertEquals(1, camel.size, "Should find 'camel' in 'camelCase'")
        assertEquals(1, camel[0].line)

        val case = FullTextSearch.search(listOf(indexDir), "Case").results.toSearchResults(depDir)
        // 'Case' matches 'camelCase' (line 1) and 'snake_case' (line 2) - grouped into one result
        assertEquals(1, case.size, "Should find 1 result after grouping matches from the same file")
        assertEquals(1, case[0].line, "First match line should be 1")
        assertEquals(setOf(1, 2), case[0].matchLines.toSet(), "matchLines should contain both matched lines")

        val camelCase = FullTextSearch.search(listOf(indexDir), "\"camelCase\"").results.toSearchResults(depDir)
        assertEquals(1, camelCase.size, "Should find 'camelCase' exactly with quotes")
        assertEquals(1, camelCase[0].line)

        // snake_case
        val snake = FullTextSearch.search(listOf(indexDir), "snake").results.toSearchResults(depDir)
        assertEquals(1, snake.size, "Should find 'snake' in 'snake_case'")
        assertEquals(2, snake[0].line)

        // withNumbers123
        val withNumbers = FullTextSearch.search(listOf(indexDir), "withNumbers").results.toSearchResults(depDir)
        assertEquals(1, withNumbers.size, "Should find 'withNumbers' in 'withNumbers123'")
        assertEquals(3, withNumbers[0].line)

        val numbers = FullTextSearch.search(listOf(indexDir), "123").results.toSearchResults(depDir)
        assertEquals(1, numbers.size, "Should find '123' in 'withNumbers123'")
        assertEquals(3, numbers[0].line)
    }

    @Test
    fun `search handles multiple matches on same line`() = runTest {
        val depDir = tempDir.resolve("dep" + Uuid.random()).createDirectories()
        depDir.resolve("File.kt").writeText("match match match")

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        val results = FullTextSearch.search(listOf(indexDir), "match").results
        // With offsets enabled, we get all occurrences
        assertEquals(3, results.size, "Should find all occurrences of 'match'")

        val searchResults = results.toSearchResults(depDir)
        assertEquals(1, searchResults.size, "toSearchResults should group matches on same line")
    }

    @Test
    fun `search handles non-existent terms`() = runTest {
        val depDir = depDir()
        depDir.resolve("File.kt").writeText("some content")

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        assertEquals(0, FullTextSearch.search(listOf(indexDir), "nonexistent").results.size)
    }

    @Test
    fun `search handles special characters`() = runTest {
        val depDir = depDir()
        depDir.resolve("File.kt").writeText("val x = a + b * c / d")

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        // Note: StandardTokenizer/WordDelimiterGraphFilter might not index operators as tokens
        // Let's see what happens.
        // Actually, Lucene query parser might treat + * / as special characters.
        // We should probably escape them or just test if we can find words around them.
        assertEquals(1, FullTextSearch.search(listOf(indexDir), "a").results.size)
        assertEquals(1, FullTextSearch.search(listOf(indexDir), "b").results.size)
    }

    @Test
    fun `index handles empty files`() = runTest {
        val depDir = depDir()
        depDir.resolve("Empty.kt").writeText("")
        depDir.resolve("NotEmpty.kt").writeText("content")

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        // Verify that we can at least find the non-empty one
        assertEquals(1, FullTextSearch.search(listOf(indexDir), "content").results.size)

        // To verify only non-empty lines are indexed, we check the index reader directly
        val finalIndexDir = indexDir.resolve(FullTextSearch.v12IndexDirName)
        FSDirectory.open(finalIndexDir).use { dir ->
            DirectoryReader.open(dir).use { reader ->
                assertEquals(1, reader.numDocs(), "Should have 1 document in index (the 'content' line)")
            }
        }
    }

    @Test
    fun `cache invalidation works`() = runTest {
        val depDir = depDir()
        val file = depDir.resolve("File.kt")
        file.writeText("initial")

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)

            assertEquals(1, FullTextSearch.search(listOf(indexDir), "initial").results.size)

            // Re-index with different content
            file.writeText("updated")
            FullTextSearch.index(depDir, indexDir)

            // FullTextSearch.index calls invalidateCache internally
            assertEquals(0, FullTextSearch.search(listOf(indexDir), "initial").results.size)
            assertEquals(1, FullTextSearch.search(listOf(indexDir), "updated").results.size)
        }
    }

    @Test
    fun `snippet generation`() = runTest {
        val depDir = depDir()
        val fileContent = (1..10).joinToString("\n") { "line $it uniqueTerm" }
        depDir.resolve("File.kt").writeText(fileContent)

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        val results = FullTextSearch.search(listOf(indexDir), "\"line 5\"").results.toSearchResults(depDir)
        assertEquals(1, results.size, "Should find exactly one match with phrase query")
        val result = results[0]
        assertEquals(5, result.line)

        val expectedSnippet = """
            line 3 uniqueTerm
            line 4 uniqueTerm
            line 5 uniqueTerm
            line 6 uniqueTerm
            line 7 uniqueTerm
        """.trimIndent()
        assertEquals(expectedSnippet, result.snippet.trim())
    }

    @Test
    fun `search handles query with colon and syntax errors`() = runTest {
        val depDir = depDir()
        depDir.resolve("Directive.kt").writeText("// LANGUAGE: +ContextParameters")

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        // Escaped colon should work
        val response = FullTextSearch.search(listOf(indexDir), "LANGUAGE\\:")
        assertTrue(response.results.isNotEmpty(), "Should find 'LANGUAGE:' in Directive.kt when escaped")

        // Unescaped colon should return an error in the response
        val errorResponse = FullTextSearch.search(listOf(indexDir), "LANGUAGE: +ContextParameters")
        assertTrue(errorResponse.error != null, "Search for 'LANGUAGE: +ContextParameters' should return error")
        assertTrue(errorResponse.error.contains("Special characters like"), "Error message should contain help text: ${errorResponse.error}")
    }

    @Test
    fun `search prioritizes exact matches`() = runTest {
        val depDir = depDir()

        // Noisy file with many occurrences of 'language' in identifiers
        depDir.resolve("Noise.kt").writeText(
            """
            fun configureCommonLanguageFeatures() { }
            val languageFeatures = emptyList<String>()
            val supportsLanguageLevel = true
        """.trimIndent()
        )

        // Target file with exact case-sensitive match
        depDir.resolve("Target.kt").writeText("val LANGUAGE = \"en\"")

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        val response = FullTextSearch.search(listOf(indexDir), "LANGUAGE")
        val results = response.results

        // 'Target.kt' should be the first result if prioritization is working correctly
        assertTrue(results.isNotEmpty(), "Should find matches for 'LANGUAGE'")
        val firstResult = results.first()
        val path = firstResult.relativePath
        assertTrue(path.endsWith("Target.kt"), "First result should be 'Target.kt', but was '$path'")
    }

    @Test
    fun `search handles Kotlin assignment fragments`() = runTest {
        val depDir = depDir()
        depDir.resolve("Assignment.kt").writeText("val x = 10")

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        // Escaped equals should work
        val response = FullTextSearch.search(listOf(indexDir), "val x \\= 10")
        assertTrue(response.error == null, "Search for 'val x \\= 10' should not return error: ${response.error}")
        assertTrue(response.results.isNotEmpty(), "Should find 'val x = 10' when escaped")

        // Phrase query should also work
        val phraseResponse = FullTextSearch.search(listOf(indexDir), "\"val x = 10\"")
        assertTrue(phraseResponse.error == null, "Search for '\"val x = 10\"' should not return error: ${phraseResponse.error}")
        assertTrue(phraseResponse.results.isNotEmpty(), "Should find 'val x = 10' as phrase")
    }

    @Test
    fun `search response includes interpreted query`() = runTest {
        val depDir = depDir()
        depDir.resolve("File.kt").writeText("some content")

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        val response = FullTextSearch.search(listOf(indexDir), "content")
        assertTrue(response.interpretedQuery != null, "Response should include interpreted query")
        assertTrue(response.interpretedQuery.contains("content"), "Interpreted query should contain 'content'")
    }

    @Test
    fun `search prioritizes code over boilerplate`() = runTest {
        val depDir = depDir()

        val file1 = depDir.resolve("File1.kt")
        file1.writeText("import com.example.TargetType\n\nclass Other { }")

        val file2 = depDir.resolve("File2.kt")
        file2.writeText("package com.example\n\nclass TargetType { }")

        val indexDir = tempDir.resolve("index-" + Uuid.random())
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir, kotlin.io.path.Path("lib"))
        }

        val results = FullTextSearch.search(listOf(indexDir), "\"TargetType\"").results
        println("Results: ${results.map { "${it.relativePath} at ${it.offset}" }}")

        assertEquals(2, results.size)
        // File2.kt has the match in code, so it should rank higher
        assertEquals("lib/File2.kt", results[0].relativePath)
        assertEquals("lib/File1.kt", results[1].relativePath)
        assertTrue(results[0].score!! > results[1].score!!)
    }

    @Test
    fun `search produces multiple results for far-apart matches`() = runTest {
        val depDir = depDir()
        // Matches on line 1 and line 10 (distance 9 > 2*DEFAULT_SNIPPET_RANGE=4) should produce 2 separate results
        val fileContent = (1..10).joinToString("\n") { if (it == 1 || it == 10) "match" else "other" }
        depDir.resolve("TestFile.kt").writeText(fileContent)

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            FullTextSearch.index(depDir, indexDir)
        }

        val results = FullTextSearch.search(listOf(indexDir), "match").results
        val searchResults = results.toSearchResults(depDir)
        // Should produce 2 separate results because matches are far apart
        assertEquals(2, searchResults.size, "Should find 2 results for far-apart matches")
        val lines = searchResults.map { it.line }.sorted()
        assertEquals(listOf(1, 10), lines, "Result lines should be 1 and 10")
        // Each result should have only one match line
        assertEquals(listOf(1), searchResults.first { it.line == 1 }.matchLines)
        assertEquals(listOf(10), searchResults.first { it.line == 10 }.matchLines)
    }
}
