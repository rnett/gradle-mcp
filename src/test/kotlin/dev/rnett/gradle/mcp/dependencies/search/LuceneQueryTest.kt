package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.fixtures.dependencies.search.SearchIntegrationTestBase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class LuceneQueryTest : SearchIntegrationTestBase() {

    override val searchProvider: SearchProvider = FullTextSearch

    @Test
    override fun `test that search fails if indexing was disabled`() = runTest {
        val zip = createSourceZip(
            "test-sources", mapOf(
                "com/example/MyClass.kt" to "class MyClass"
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

        val sourcesDir = with(ProgressReporter.NONE) {
            sourcesService.resolveAndProcessAllSources(projectRoot, index = false)
        }

        val response = sourceIndexService.search(sourcesDir, searchProvider, "MyClass")
        assertTrue(response.error != null, "Search should have failed with an error when indexing is disabled")
        assertTrue(
            response.error!!.contains("Lucene index directory does not exist") || response.error!!.contains("Index not found") || response.error!!.contains("Index for provider"),
            "Error message should mention missing index: ${response.error}"
        )
    }

    @Test
    fun `test multi-field search logic`() = runTest {
        val zip = createSourceZip(
            "multi-field", mapOf(
                "com/example/FileA.java" to "package com.example;\nimport java.util.List;\npublic class FileA {\n  public void doSomething(List list) {\n    System.out.println(\"apple\");\n  }\n}",
                "com/example/FileB.java" to "package com.example;\nimport java.util.List;\npublic class FileB {\n  public void doSomething(List list) {\n    System.out.println(\"orange\");\n  }\n}"
            )
        )

        mockDependencyReport(
            GradleDependency(
                id = "com.example:multi:1.0",
                group = "com.example",
                name = "multi",
                version = "1.0",
                sourcesFile = zip
            )
        )

        val sourcesDir = with(ProgressReporter.PRINTLN) {
            sourcesService.resolveAndProcessAllSources(projectRoot, index = true, providerToIndex = searchProvider)
        }

        // Standard search should find both
        val response = sourceIndexService.search(sourcesDir, searchProvider, "doSomething")
        assertTrue(response.results.size >= 2, "Standard search should find at least 2 results, found ${response.results.size}")

        // Searching for code content (apple) should favor FileA
        val responseApple = sourceIndexService.search(sourcesDir, searchProvider, "apple")
        assertTrue(responseApple.results.isNotEmpty(), "Search for 'apple' failed")
        assertTrue(responseApple.results.all { it.relativePath.endsWith("FileA.java") })

        // Field-specific search (Lucene syntax)
        if (searchProvider is FullTextSearch) {
            val responseContents = sourceIndexService.search(sourcesDir, searchProvider, "contents:orange")
            assertTrue(responseContents.results.isNotEmpty(), "Field search (contents) failed")
            assertTrue(responseContents.results.all { it.relativePath.endsWith("FileB.java") })
        }
    }
}
