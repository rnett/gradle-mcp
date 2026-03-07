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
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)
            val response = sourcesService.search(sourcesDir, searchProvider, "\"unique phrase for testing\"")
            assertTrue(response.results.isNotEmpty(), "Phrase search failed")
            assertTrue(response.results.any { it.relativePath.endsWith("FileA.kt") })
        }
    }

    @Test
    fun `test wildcard search`() = runTest {
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)

            // Single character wildcard
            val responseSingle = sourcesService.search(sourcesDir, searchProvider, "app?e")
            assertTrue(responseSingle.results.isNotEmpty(), "Single character wildcard search failed")
            assertTrue(responseSingle.results.any { it.relativePath.endsWith("FileA.kt") })

            // Multiple character wildcard
            val responseMulti = sourcesService.search(sourcesDir, searchProvider, "ban*")
            assertTrue(responseMulti.results.isNotEmpty(), "Multiple character wildcard search failed")
            assertTrue(responseMulti.results.any { it.relativePath.endsWith("FileA.kt") })
        }
    }

    @Test
    fun `test boolean operators`() = runTest {
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)

            // AND
            val responseAnd = sourcesService.search(sourcesDir, searchProvider, "apple AND banana")
            assertTrue(responseAnd.results.isNotEmpty(), "AND operator search failed")
            assertTrue(responseAnd.results.all { it.relativePath.endsWith("FileA.kt") })

            // OR
            val responseOr = sourcesService.search(sourcesDir, searchProvider, "apple OR orange")
            assertEquals(2, responseOr.results.map { it.relativePath }.distinct().size, "OR operator search failed")

            // NOT
            val responseNot = sourcesService.search(sourcesDir, searchProvider, "unique NOT testing")
            assertTrue(responseNot.results.isNotEmpty(), "NOT operator search failed")
            assertTrue(responseNot.results.all { it.relativePath.endsWith("FileB.java") })
        }
    }

    @Test
    fun `test grouping`() = runTest {
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)
            val response = sourcesService.search(sourcesDir, searchProvider, "(apple OR banana) AND unique")
            assertTrue(response.results.isNotEmpty(), "Grouping search failed")
            assertTrue(response.results.all { it.relativePath.endsWith("FileA.kt") })
        }
    }

    @Test
    fun `test fuzzy search`() = runTest {
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)
            val response = sourcesService.search(sourcesDir, searchProvider, "aple~")
            assertTrue(response.results.isNotEmpty(), "Fuzzy search failed")
            assertTrue(response.results.any { it.relativePath.endsWith("FileA.kt") })
        }
    }

    @Test
    fun `test proximity search`() = runTest {
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)
            // "unique" and "phrase" are close together
            val response = sourcesService.search(sourcesDir, searchProvider, "\"unique phrase\"~5")
            assertTrue(response.results.isNotEmpty(), "Proximity search failed")
        }
    }

    @Test
    fun `test field search`() = runTest {
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)

            // Match a term to see current paths in index
            val responsePackage = sourcesService.search(sourcesDir, searchProvider, "package")
            val availablePaths = responsePackage.results.map { it.relativePath }.distinct()
            assertTrue(availablePaths.isNotEmpty(), "No paths found in index")

            // Exact path search (case-insensitive because of analyzer)
            val path = availablePaths.first { it.contains("FileB.java") }
            val responseExact = sourcesService.search(sourcesDir, searchProvider, "path:\"${path.lowercase()}\"")
            assertTrue(responseExact.results.isNotEmpty(), "Exact path search failed for $path. Available: $availablePaths")

            // Wildcard search in path (requires lowercase for wildcards in Lucene against lowercased fields)
            val responseWildcard = sourcesService.search(sourcesDir, searchProvider, "path:*fileb.java")
            assertTrue(responseWildcard.results.isNotEmpty(), "Wildcard path search failed for *fileb.java. Available: $availablePaths")

            // Search in contents field explicitly
            val responseContents = sourcesService.search(sourcesDir, searchProvider, "contents:orange")
            assertTrue(responseContents.results.isNotEmpty(), "Field search (contents) failed")
            assertTrue(responseContents.results.all { it.relativePath.endsWith("FileB.java") })
        }
    }
}
