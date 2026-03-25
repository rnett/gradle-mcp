package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.fixtures.dependencies.search.SearchIntegrationTestBase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue


class DeclarationSearchIntegrationTest : SearchIntegrationTestBase() {
    override val searchProvider = DeclarationSearch

    @Test
    fun `test declaration search across mocked dependencies`() = runTest {
        val zip1 = createSourceZip(
            "dep1-sources", mapOf(
                "com/example/MyClass.kt" to """
                package com.example
                
                class MyClass {
                    val myField = 1
                }
            """.trimIndent()
            )
        )

        val zip2 = createSourceZip(
            "dep2-sources", mapOf(
                "com/other/OtherClass.java" to """
                package com.other;
                
                public class OtherClass {
                    public void myMethod() {}
                }
            """.trimIndent()
            )
        )

        mockDependencyReport(
            GradleDependency(
                id = "com.example:dep1:1.0",
                group = "com.example",
                name = "dep1",
                version = "1.0",
                sourcesFile = zip1
            ),
            GradleDependency(
                id = "com.other:dep2:2.0",
                group = "com.other",
                name = "dep2",
                version = "2.0",
                sourcesFile = zip2
            )
        )

        val sourcesDir = with(ProgressReporter.PRINTLN) {
            sourcesService.resolveAndProcessAllSources(projectRoot, providerToIndex = searchProvider)
        }

        // Search for Kotlin declaration
        val results1 = sourceIndexService.search(sourcesDir, searchProvider, "MyClass").results
        assertTrue(results1.isNotEmpty(), "MyClass not found")
        assertTrue(results1.any { it.relativePath == "com/example/dep1-sources/example/MyClass.kt" && it.line == 3 }, "MyClass missing at line 3: ${results1}")

        val resultsField = sourceIndexService.search(sourcesDir, searchProvider, "myField").results
        assertTrue(resultsField.isNotEmpty(), "myField not found")
        assertTrue(resultsField.any { it.relativePath == "com/example/dep1-sources/example/MyClass.kt" && it.line == 4 }, "myField missing at line 4: ${resultsField}")

        // Search for Java declaration
        val results2 = sourceIndexService.search(sourcesDir, searchProvider, "OtherClass").results
        assertTrue(results2.isNotEmpty(), "OtherClass not found")
        assertTrue(results2.any { it.relativePath == "com/other/dep2-sources/other/OtherClass.java" && it.line == 3 }, "OtherClass missing at line 3: ${results2}")

        val resultsMethod = sourceIndexService.search(sourcesDir, searchProvider, "myMethod").results
        assertTrue(resultsMethod.isNotEmpty(), "myMethod not found")
        assertTrue(resultsMethod.any { it.relativePath == "com/other/dep2-sources/other/OtherClass.java" && it.line == 4 }, "myMethod missing at line 4: ${resultsMethod}")
    }

    @Test
    fun `declaration search should not fail with index not found`() = runTest {
        val zip = createSourceZip(
            "test-sources", mapOf(
                "com/example/Test.kt" to """
                package com.example
                class TestClass
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

        val sourcesDir = with(ProgressReporter.PRINTLN) {
            sourcesService.resolveAndProcessAllSources(projectRoot, providerToIndex = searchProvider)
        }

        val searchResults = sourceIndexService.search(sourcesDir, searchProvider, "TestClass").results
        assertTrue(searchResults.isNotEmpty(), "Expected results for TestClass")
    }

    @Test
    fun `invalid regex in declaration search should return graceful error`() = runTest {
        val zip = createSourceZip(
            "test-sources", mapOf(
                "com/example/Test.kt" to "package com.example\nclass Test"
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
        val sourcesDir = with(ProgressReporter.PRINTLN) {
            sourcesService.resolveAndProcessAllSources(projectRoot, providerToIndex = searchProvider)
        }

        val result = sourceIndexService.search(sourcesDir, searchProvider, "[")

        assertTrue(result.error != null, "Expected error for invalid regex")
    }
}