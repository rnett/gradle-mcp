package dev.rnett.gradle.mcp.gradle.dependencies.search

import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FullTextSearchIntegrationTest : SearchIntegrationTestBase() {
    override val searchProvider = FullTextSearch

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

        val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)

        // Search for phrase in FileA
        val resultsA = sourcesService.search(sourcesDir, searchProvider, "\"unique phrase in FileA\"").results
        assertTrue(resultsA.isNotEmpty(), "phrase in FileA not found")
        assertTrue(resultsA.any { it.relativePath == "com.example/depA-sources/example/FileA.kt" && it.line == 3 }, "phrase in FileA missing at line 3: ${resultsA}")

        // Search for phrase in FileB
        val resultsB = sourcesService.search(sourcesDir, searchProvider, "\"unique phrase in FileB\"").results
        assertTrue(resultsB.isNotEmpty(), "phrase in FileB not found")
        assertTrue(resultsB.any { it.relativePath == "com.other/depB-sources/other/FileB.java" && it.line == 4 }, "phrase in FileB missing at line 4: ${resultsB}")

        // Search for common word
        val resultsHello = sourcesService.search(sourcesDir, searchProvider, "Hello").results
        assertTrue(resultsHello.isNotEmpty(), "Hello not found")
        val files = resultsHello.map { it.relativePath }.toSet()
        assertEquals(setOf("com.example/depA-sources/example/FileA.kt", "com.other/depB-sources/other/FileB.java"), files)
    }
}