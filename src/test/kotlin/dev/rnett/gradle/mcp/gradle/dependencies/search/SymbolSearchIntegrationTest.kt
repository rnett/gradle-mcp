package dev.rnett.gradle.mcp.gradle.dependencies.search

import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class SymbolSearchIntegrationTest : SearchIntegrationTestBase() {
    override val searchProvider = SymbolSearch

    @Test
    fun `test symbol search across mocked dependencies`() = runTest {
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

        val sourcesDir = sourcesService.downloadAllSources(projectRoot, index = true)

        // Search for Kotlin symbol
        val results1 = sourcesService.search(sourcesDir, searchProvider, "MyClass")
        assertTrue(results1.isNotEmpty(), "MyClass not found")
        assertTrue(results1.any { it.relativePath == "com.example/dep1-sources/example/MyClass.kt" && it.line == 3 }, "MyClass missing at line 3: ${results1}")

        val resultsField = sourcesService.search(sourcesDir, searchProvider, "myField")
        assertTrue(resultsField.isNotEmpty(), "myField not found")
        assertTrue(resultsField.any { it.relativePath == "com.example/dep1-sources/example/MyClass.kt" && it.line == 4 }, "myField missing at line 4: ${resultsField}")

        // Search for Java symbol
        val results2 = sourcesService.search(sourcesDir, searchProvider, "OtherClass")
        assertTrue(results2.isNotEmpty(), "OtherClass not found")
        assertTrue(results2.any { it.relativePath == "com.other/dep2-sources/other/OtherClass.java" && it.line == 3 }, "OtherClass missing at line 3: ${results2}")

        val resultsMethod = sourcesService.search(sourcesDir, searchProvider, "myMethod")
        assertTrue(resultsMethod.isNotEmpty(), "myMethod not found")
        assertTrue(resultsMethod.any { it.relativePath == "com.other/dep2-sources/other/OtherClass.java" && it.line == 4 }, "myMethod missing at line 4: ${resultsMethod}")
    }
}