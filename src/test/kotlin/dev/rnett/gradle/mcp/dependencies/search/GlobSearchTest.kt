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

        val results = GlobSearch.search(outputDir, "**/MyClass.kt").results
        assertEquals(1, results.size)
        assertEquals("src/main/kotlin/MyClass.kt", results[0].relativePath)

        val xmlResults = GlobSearch.search(outputDir, "**/*.xml").results
        assertEquals(1, xmlResults.size)
        assertEquals("src/main/resources/config.xml", xmlResults[0].relativePath)

        val licenseResults = GlobSearch.search(outputDir, "LICENSE").results
        assertEquals(1, licenseResults.size)
        assertEquals("LICENSE", licenseResults[0].relativePath)

        val noResults = GlobSearch.search(outputDir, "NON_EXISTENT").results
        assertTrue(noResults.isEmpty())

        val substringResults = GlobSearch.search(outputDir, "config").results
        assertEquals(1, substringResults.size)
        assertEquals("src/main/resources/config.xml", substringResults[0].relativePath)
    }

    @Test
    fun `test merging indices`() = runTest {
        val dep1Dir = tempDir.resolve("dep1")
        dep1Dir.createDirectories()
        dep1Dir.resolve("File1.kt").createFile()
        val index1Dir = tempDir.resolve("index1")
        with(ProgressReporter.PRINTLN) {
            GlobSearch.index(dep1Dir, index1Dir, kotlin.io.path.Path("lib1"))

            val dep2Dir = tempDir.resolve("dep2")
            dep2Dir.createDirectories()
            dep2Dir.resolve("File2.kt").createFile()
            val index2Dir = tempDir.resolve("index2")
            GlobSearch.index(dep2Dir, index2Dir, kotlin.io.path.Path("lib2"))

            val mergedDir = tempDir.resolve("merged")
            with(ProgressReporter.NONE) {
                GlobSearch.mergeIndices(
                    mapOf(
                        index1Dir to kotlin.io.path.Path("lib1"),
                        index2Dir to kotlin.io.path.Path("lib2")
                    ),
                    mergedDir
                ) { _, action -> action() }
            }

            val results1 = GlobSearch.search(mergedDir, "lib1/File1.kt").results
            assertEquals(1, results1.size)
            assertEquals("lib1/File1.kt", results1[0].relativePath)

            val results2 = GlobSearch.search(mergedDir, "lib2/File2.kt").results
            assertEquals(1, results2.size)
            assertEquals("lib2/File2.kt", results2[0].relativePath)

            val allResults = GlobSearch.search(mergedDir, "**/*.kt").results
            assertEquals(2, allResults.size)
        }
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
