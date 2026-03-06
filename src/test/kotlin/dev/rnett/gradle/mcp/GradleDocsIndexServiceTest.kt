package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.lucene.LuceneReaderCache
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class GradleDocsIndexServiceTest {

    @Test
    fun `search returns matching results from indexed docs`() = runTest {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-index")
        val environment = GradleMcpEnvironment(tempDir)

        val version = "9.4.0"
        val convertedDir = tempDir.resolve("cache/reading_gradle_docs/$version/converted")
        Files.createDirectories(convertedDir.resolve("userguide"))
        Files.createDirectories(convertedDir.resolve("dsl"))

        convertedDir.resolve("userguide/test.md").writeText("# Userguide Test\nThis is some content about dependencies.")
        convertedDir.resolve("dsl/Project.md").writeText("# Project DSL\nProject is the main interface.")
        convertedDir.resolve("release-notes.md").writeText("# Release Notes\nNew features in this version.")

        val extractor = mockk<ContentExtractorService>()
        coEvery { extractor.ensureProcessed(version) } returns Unit

        val service = DefaultGradleDocsIndexService(extractor, environment, LuceneReaderCache())

        // Search for 'dependencies'
        val results = service.search("dependencies", version).results
        assertEquals(1, results.size)
        assertEquals("Userguide Test", results[0].title)
        assertEquals("userguide/test.md", results[0].path)
        assertEquals("userguide", results[0].tag)

        // Search by tag
        val dslResults = service.search("tag:dsl", version).results
        assertEquals(1, dslResults.size)
        assertEquals("Project DSL", dslResults[0].title)

        // Search release notes
        val rnResults = service.search("tag:release-notes", version).results
        assertEquals(1, rnResults.size)
        assertEquals("Release Notes", rnResults[0].title)

        tempDir.toFile().deleteRecursively()
    }
}
