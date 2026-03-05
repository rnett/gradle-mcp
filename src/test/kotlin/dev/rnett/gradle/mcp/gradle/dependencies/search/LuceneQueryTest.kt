package dev.rnett.gradle.mcp.gradle.dependencies.search

import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LuceneQueryTest : SearchIntegrationTestBase() {
    override val searchProvider = FullTextSearch

    @BeforeEach
    fun setup() {
        val zip = createSourceZip(
            "test-sources", mapOf(
                "com/example/FileA.kt" to """
                package com.example
                
                /**
                 * This is a unique phrase for testing.
                 * It contains some interesting keywords like apple and banana.
                 */
                fun doSomething() {
                    val message = "Hello World"
                    println(message)
                }
            """.trimIndent(),
                "com/example/FileB.java" to """
                package com.example;
                
                public class FileB {
                    // Another unique phrase here.
                    // Keywords: orange and grape.
                    public void sayHello() {
                        System.out.println("Hello Universe");
                    }
                }
            """.trimIndent()
            )
        )

        mockDependencyReport(
            GradleDependency(
                id = "com.example:test:1.0",
                group = "com.example",
                name = "test",
                version = "1.0",
                sourcesFile = zip
            )
        )
    }

    @Test
    fun `test phrase search`() = runTest {
        val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)
        val results = sourcesService.search(sourcesDir, searchProvider, "\"unique phrase for testing\"")
        assertTrue(results.isNotEmpty(), "Phrase search failed")
        assertTrue(results.any { it.relativePath.endsWith("FileA.kt") })
    }

    @Test
    fun `test wildcard search`() = runTest {
        val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)

        // Single character wildcard
        val resultsSingle = sourcesService.search(sourcesDir, searchProvider, "app?e")
        assertTrue(resultsSingle.isNotEmpty(), "Single character wildcard search failed")
        assertTrue(resultsSingle.any { it.relativePath.endsWith("FileA.kt") })

        // Multiple character wildcard
        val resultsMulti = sourcesService.search(sourcesDir, searchProvider, "ban*")
        assertTrue(resultsMulti.isNotEmpty(), "Multiple character wildcard search failed")
        assertTrue(resultsMulti.any { it.relativePath.endsWith("FileA.kt") })
    }

    @Test
    fun `test boolean operators`() = runTest {
        val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)

        // AND
        val resultsAnd = sourcesService.search(sourcesDir, searchProvider, "apple AND banana")
        assertTrue(resultsAnd.isNotEmpty(), "AND operator search failed")
        assertTrue(resultsAnd.all { it.relativePath.endsWith("FileA.kt") })

        // OR
        val resultsOr = sourcesService.search(sourcesDir, searchProvider, "apple OR orange")
        assertEquals(2, resultsOr.map { it.relativePath }.distinct().size, "OR operator search failed")

        // NOT
        val resultsNot = sourcesService.search(sourcesDir, searchProvider, "unique NOT testing")
        assertTrue(resultsNot.isNotEmpty(), "NOT operator search failed")
        assertTrue(resultsNot.all { it.relativePath.endsWith("FileB.java") })
    }

    @Test
    fun `test grouping`() = runTest {
        val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)
        val results = sourcesService.search(sourcesDir, searchProvider, "(apple OR banana) AND unique")
        assertTrue(results.isNotEmpty(), "Grouping search failed")
        assertTrue(results.all { it.relativePath.endsWith("FileA.kt") })
    }

    @Test
    fun `test fuzzy search`() = runTest {
        val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)
        val results = sourcesService.search(sourcesDir, searchProvider, "aple~")
        assertTrue(results.isNotEmpty(), "Fuzzy search failed")
        assertTrue(results.any { it.relativePath.endsWith("FileA.kt") })
    }

    @Test
    fun `test proximity search`() = runTest {
        val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)
        // "unique" and "phrase" are close together
        val results = sourcesService.search(sourcesDir, searchProvider, "\"unique phrase\"~5")
        assertTrue(results.isNotEmpty(), "Proximity search failed")
    }

    @Test
    fun `test field search`() = runTest {
        val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)

        // Match a term to see current paths in index
        val resultsPackage = sourcesService.search(sourcesDir, searchProvider, "package")
        val availablePaths = resultsPackage.map { it.relativePath }.distinct()
        assertTrue(availablePaths.isNotEmpty(), "No paths found in index")

        // Exact path search (case-insensitive because of analyzer)
        val path = availablePaths.first { it.contains("FileB.java") }
        val resultsExact = sourcesService.search(sourcesDir, searchProvider, "path:\"${path.lowercase()}\"")
        assertTrue(resultsExact.isNotEmpty(), "Exact path search failed for $path. Available: $availablePaths")

        // Wildcard search in path (requires lowercase for wildcards in Lucene against lowercased fields)
        val resultsWildcard = sourcesService.search(sourcesDir, searchProvider, "path:*fileb.java")
        assertTrue(resultsWildcard.isNotEmpty(), "Wildcard path search failed for *fileb.java. Available: $availablePaths")

        // Search in contents field explicitly
        val resultsContents = sourcesService.search(sourcesDir, searchProvider, "contents:orange")
        assertTrue(resultsContents.isNotEmpty(), "Field search (contents) failed")
        assertTrue(resultsContents.all { it.relativePath.endsWith("FileB.java") })
    }
}
