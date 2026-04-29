package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.fixtures.dependencies.search.SearchIntegrationTestBase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FullTextSearchIntegrationTest : SearchIntegrationTestBase() {
    override val searchProvider: SearchProvider get() = getKoin().get<FullTextSearch>()

    @Test
    fun `test full text search across mocked dependencies`() = runTest {
        val zip1 = createSourceZip(
            "depA-sources", mapOf(
                "com/example/FileA.kt" to """
                package com.example
                
                // This is a unique phrase in FileA
                fun doSomething() {
                    println("Hello World")
                }
            """.trimIndent()
            )
        )

        val zip2 = createSourceZip(
            "depB-sources", mapOf(
                "com/other/FileB.java" to """
                package com.other;
                
                public class FileB {
                    // This is a unique phrase in FileB
                    public void sayHello() {
                        System.out.println("Hello Universe");
                    }
                }
            """.trimIndent()
            )
        )

        mockDependencyReport(
            GradleDependency(
                id = "com.example:depA:1.0",
                group = "com.example",
                name = "depA",
                version = "1.0",
                sourcesFile = zip1
            ),
            GradleDependency(
                id = "com.other:depB:2.0",
                group = "com.other",
                name = "depB",
                version = "2.0",
                sourcesFile = zip2
            )
        )

        val sourcesDir = with(ProgressReporter.PRINTLN) {
            sourcesService.resolveAndProcessProjectSources(projectRoot, ":", providerToIndex = searchProvider)
        }

        // Search for phrase in FileA
        val resultsA = sourceIndexService.search(sourcesDir, searchProvider, "\"unique phrase in FileA\"").results
        assertTrue(resultsA.isNotEmpty(), "phrase in FileA not found")
        assertTrue(resultsA.any { it.relativePath == "com.example/depA/com/example/FileA.kt" && it.line == 3 }, "phrase in FileA missing at line 3: ${resultsA}")

        // Search for phrase in FileB
        val resultsB = sourceIndexService.search(sourcesDir, searchProvider, "\"unique phrase in FileB\"").results
        assertTrue(resultsB.isNotEmpty(), "phrase in FileB not found")
        assertTrue(resultsB.any { it.relativePath == "com.other/depB/com/other/FileB.java" && it.line == 4 }, "phrase in FileB missing at line 4: ${resultsB}")

        // Search for common word
        val resultsHello = sourceIndexService.search(sourcesDir, searchProvider, "Hello").results
        assertTrue(resultsHello.isNotEmpty(), "Hello not found")
        val files = resultsHello.map { it.relativePath }.toSet()
        assertEquals(setOf("com.example/depA/com/example/FileA.kt", "com.other/depB/com/other/FileB.java"), files)
    }

    @Test
    fun `test imports and packages are ranked lower than code matches`() = runTest {
        val zip = createSourceZip(
            "depC-sources", mapOf(
                "com/example/Code.kt" to """
                package com.example.target
                
                import com.example.target.SomeType
                
                class OtherType {
                    val target = "This is a target in code"
                }
            """.trimIndent()
            )
        )

        mockDependencyReport(
            GradleDependency(
                id = "com.example:depC:1.0",
                group = "com.example",
                name = "depC",
                version = "1.0",
                sourcesFile = zip
            )
        )

        val sourcesDir = with(ProgressReporter.PRINTLN) {
            sourcesService.resolveAndProcessProjectSources(projectRoot, ":", forceDownload = true, providerToIndex = searchProvider)
        }

        val results = sourceIndexService.search(sourcesDir, searchProvider, "target").results
        assertTrue(results.isNotEmpty(), "target not found")

        // The word 'target' appears in package, import, and code. Code match should be first.
        val firstMatch = results.first()
        assertEquals(6, firstMatch.line, "First match should be the code usage at line 6, got ${firstMatch.line} (snippet: ${firstMatch.snippet})")
    }
}