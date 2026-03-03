package dev.rnett.gradle.mcp.gradle.dependencies.search

import kotlinx.coroutines.test.runTest
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.store.FSDirectory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals

class FullTextSearchTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `can index and merge indices`() = runTest {
        val dep1Dir = tempDir.resolve("dep1").createDirectories()
        dep1Dir.resolve("File1.kt").writeText("Content of File1")
        dep1Dir.resolve("subdir").createDirectories().resolve("File2.kt").writeText("Content of File2")

        val dep2Dir = tempDir.resolve("dep2").createDirectories()
        dep2Dir.resolve("File3.kt").writeText("Content of File3")

        val index1Dir = tempDir.resolve("index1")
        val index2Dir = tempDir.resolve("index2")

        FullTextSearch.index(dep1Dir, index1Dir)
        FullTextSearch.index(dep2Dir, index2Dir)

        val mergedIndexDir = tempDir.resolve("merged")
        FullTextSearch.mergeIndices(
            mapOf(
                index1Dir to Path.of("lib1"),
                index2Dir to Path.of("lib2")
            ),
            mergedIndexDir
        )

        val finalIndexDir = mergedIndexDir.resolve(FullTextSearch.v2IndexDirName)
        FSDirectory.open(finalIndexDir).use { dir ->
            DirectoryReader.open(dir).use { reader ->
                assertEquals(3, reader.numDocs())
                val paths = (0 until reader.maxDoc()).map {
                    reader.storedFields().document(it).get("path")
                }.toSet()

                val expectedPaths = setOf(
                    "lib1/File1.kt",
                    "lib1/subdir/File2.kt",
                    "lib2/File3.kt"
                )
                assertEquals(expectedPaths, paths)
            }
        }

        val results = FullTextSearch.search(mergedIndexDir, "Content")
        assertEquals(3, results.size, "Should find 3 matches in merged index")
    }

    @Test
    fun `search returns correct line numbers`() = runTest {
        val depDir = tempDir.resolve("dep").createDirectories()
        val fileContent = """
            line 0: zero
            line 1: match
            line 2: two
            line 3: match
            line 4: four
        """.trimIndent()
        depDir.resolve("TestFile.kt").writeText(fileContent)

        val indexDir = tempDir.resolve("index")
        FullTextSearch.index(depDir, indexDir)

        val results = FullTextSearch.search(indexDir, "match")
        val searchResults = results.toSearchResults(depDir)
        assertEquals(2, searchResults.size, "Should find 2 matches")

        val lines = searchResults.map { it.line }.sorted()
        assertEquals(listOf(2, 4), lines, "Matches should be on lines 2 and 4")
        assertEquals("TestFile.kt", searchResults[0].relativePath)
    }

    @Test
    fun `search handles word boundaries`() = runTest {
        val depDir = tempDir.resolve("dep").createDirectories()
        depDir.resolve("File.kt").writeText(
            """
            val camelCase = 1
            val snake_case = 2
            val withNumbers123 = 3
        """.trimIndent()
        )

        val indexDir = tempDir.resolve("index")
        FullTextSearch.index(depDir, indexDir)

        // camelCase
        val camel = FullTextSearch.search(indexDir, "camel").toSearchResults(depDir)
        assertEquals(1, camel.size, "Should find 'camel' in 'camelCase'")
        assertEquals(1, camel[0].line)

        val case = FullTextSearch.search(indexDir, "Case").toSearchResults(depDir)
        // 'Case' matches 'camelCase' (line 1) and 'snake_case' (line 2)
        assertEquals(2, case.size, "Should find 'Case' in 'camelCase' and 'snake_case'")
        assertEquals(setOf(1, 2), case.map { it.line }.toSet())

        val camelCase = FullTextSearch.search(indexDir, "\"camelCase\"").toSearchResults(depDir)
        assertEquals(1, camelCase.size, "Should find 'camelCase' exactly with quotes")
        assertEquals(1, camelCase[0].line)

        // snake_case
        val snake = FullTextSearch.search(indexDir, "snake").toSearchResults(depDir)
        assertEquals(1, snake.size, "Should find 'snake' in 'snake_case'")
        assertEquals(2, snake[0].line)

        // withNumbers123
        val withNumbers = FullTextSearch.search(indexDir, "withNumbers").toSearchResults(depDir)
        assertEquals(1, withNumbers.size, "Should find 'withNumbers' in 'withNumbers123'")
        assertEquals(3, withNumbers[0].line)

        val numbers = FullTextSearch.search(indexDir, "123").toSearchResults(depDir)
        assertEquals(1, numbers.size, "Should find '123' in 'withNumbers123'")
        assertEquals(3, numbers[0].line)
    }

    @Test
    fun `search handles multiple matches on same line`() = runTest {
        val depDir = tempDir.resolve("dep").createDirectories()
        depDir.resolve("File.kt").writeText("match match match")

        val indexDir = tempDir.resolve("index")
        FullTextSearch.index(depDir, indexDir)

        val results = FullTextSearch.search(indexDir, "match")
        assertEquals(3, results.size, "Lucene should find 3 raw matches")

        val searchResults = results.toSearchResults(depDir)
        assertEquals(1, searchResults.size, "toSearchResults should group matches on same line")
    }

    @Test
    fun `search handles non-existent terms`() = runTest {
        val depDir = tempDir.resolve("dep").createDirectories()
        depDir.resolve("File.kt").writeText("some content")

        val indexDir = tempDir.resolve("index")
        FullTextSearch.index(depDir, indexDir)

        assertEquals(0, FullTextSearch.search(indexDir, "nonexistent").size)
    }

    @Test
    fun `search handles special characters`() = runTest {
        val depDir = tempDir.resolve("dep").createDirectories()
        depDir.resolve("File.kt").writeText("val x = a + b * c / d")

        val indexDir = tempDir.resolve("index")
        FullTextSearch.index(depDir, indexDir)

        // Note: StandardTokenizer/WordDelimiterGraphFilter might not index operators as tokens
        // Let's see what happens.
        // Actually, Lucene query parser might treat + * / as special characters.
        // We should probably escape them or just test if we can find words around them.
        assertEquals(1, FullTextSearch.search(indexDir, "a").size)
        assertEquals(1, FullTextSearch.search(indexDir, "b").size)
    }

    @Test
    fun `index handles empty files`() = runTest {
        val depDir = tempDir.resolve("dep").createDirectories()
        depDir.resolve("Empty.kt").writeText("")
        depDir.resolve("NotEmpty.kt").writeText("content")

        val indexDir = tempDir.resolve("index")
        FullTextSearch.index(depDir, indexDir)

        // Verify that we can at least find the non-empty one
        assertEquals(1, FullTextSearch.search(indexDir, "content").size)

        // To verify both are indexed, we can check the index reader directly
        val finalIndexDir = indexDir.resolve(FullTextSearch.v2IndexDirName)
        FSDirectory.open(finalIndexDir).use { dir ->
            DirectoryReader.open(dir).use { reader ->
                assertEquals(2, reader.numDocs(), "Should have 2 documents in index")
            }
        }
    }

    @Test
    fun `cache invalidation works`() = runTest {
        val depDir = tempDir.resolve("dep").createDirectories()
        val file = depDir.resolve("File.kt")
        file.writeText("initial")

        val indexDir = tempDir.resolve("index")
        FullTextSearch.index(depDir, indexDir)

        assertEquals(1, FullTextSearch.search(indexDir, "initial").size)

        // Re-index with different content
        file.writeText("updated")
        FullTextSearch.index(depDir, indexDir)

        // FullTextSearch.index calls invalidateCache internally
        assertEquals(0, FullTextSearch.search(indexDir, "initial").size)
        assertEquals(1, FullTextSearch.search(indexDir, "updated").size)
    }

    @Test
    fun `snippet generation`() = runTest {
        val depDir = tempDir.resolve("dep").createDirectories()
        val fileContent = (1..10).joinToString("\n") { "line $it uniqueTerm" }
        depDir.resolve("File.kt").writeText(fileContent)

        val indexDir = tempDir.resolve("index")
        FullTextSearch.index(depDir, indexDir)

        val results = FullTextSearch.search(indexDir, "\"line 5\"").toSearchResults(depDir)
        assertEquals(1, results.size, "Should find exactly one match with phrase query")
        val result = results[0]
        assertEquals(5, result.line)

        val expectedSnippet = """
            line 4 uniqueTerm
            line 5 uniqueTerm
            line 6 uniqueTerm
        """.trimIndent()
        assertEquals(expectedSnippet, result.snippet.trim())
    }
}
