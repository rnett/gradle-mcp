package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.fixtures.dependencies.search.index
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobSearchTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `test indexing and searching`() = runTest {
        val dependencyDir = tempDir.resolve("dependency")
        dependencyDir.createDirectories()
        dependencyDir.resolve("src/main/kotlin/MyClass.kt").apply { createParentDirectories(); createFile(); writeText("class MyClass") }
        dependencyDir.resolve("src/main/resources/config.xml").apply { createParentDirectories(); createFile(); writeText("<config/>") }
        dependencyDir.resolve("LICENSE").apply { createFile(); writeText("Apache 2.0") }

        val outputDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            GlobSearch.index(dependencyDir, outputDir)
        }

        val results = GlobSearch.search(listOf(outputDir), "**/MyClass.kt").results
        assertEquals(1, results.size)
        assertEquals("src/main/kotlin/MyClass.kt", results[0].relativePath)

        val xmlResults = GlobSearch.search(listOf(outputDir), "**/*.xml").results
        assertEquals(1, xmlResults.size)
        assertEquals("src/main/resources/config.xml", xmlResults[0].relativePath)

        val licenseResults = GlobSearch.search(listOf(outputDir), "LICENSE").results
        assertEquals(1, licenseResults.size)
        assertEquals("LICENSE", licenseResults[0].relativePath)

        val noResults = GlobSearch.search(listOf(outputDir), "NON_EXISTENT").results
        assertTrue(noResults.isEmpty())

        val substringResults = GlobSearch.search(listOf(outputDir), "config").results
        assertEquals(1, substringResults.size)
        assertEquals("src/main/resources/config.xml", substringResults[0].relativePath)
    }

    @Test
    fun `test high signal snippet calculation`() {
        val code = """
            /*
             * License Header
             * Line 2
             */
            
            package com.example
            
            import java.util.*
            
            /**
             * Javadoc
             */
            class MyClass {
                fun test() {}
            }
        """.trimIndent()

        val (line, snippet) = findHighSignalSnippet(code, 3)
        assertEquals(13, line)
        assertTrue(snippet.startsWith("class MyClass {"))
        assertEquals(3, snippet.lines().size)
    }

    @Test
    fun `test snippet calculation XML`() {
        val xml = """
            <!-- 
                License Header
            -->
            <root>
                <child>Content</child>
            </root>
        """.trimIndent()

        val (line, snippet) = findHighSignalSnippet(xml, 2)
        assertEquals(4, line)
        assertTrue(snippet.startsWith("<root>"))
    }

    private fun Path.createParentDirectories() {
        parent?.createDirectories()
    }
}
