package dev.rnett.gradle.mcp.dependencies.gradle.docs

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.lucene.LuceneReaderCache
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals

class GradleDocsIndexServiceTest {

    @Test
    fun `search returns matching results from indexed docs`() = runTest {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-index")
        val environment = GradleMcpEnvironment(tempDir)

        val version = "9.4.0"

        val extractor = mockk<ContentExtractorService>()
        every {
            with(any<ProgressReporter>()) {
                extractor.extractEntries(version)
            }
        } returns flow {
            emit("userguide/test.html" to "<html><body><h1>Userguide Test</h1><p>This is some content about dependencies.</p></body></html>".toByteArray())
            emit("dsl/Project.html" to "<html><body><h1>Project DSL</h1><p>Project is the main interface.</p></body></html>".toByteArray())
            emit("release-notes.html" to "<html><body><h1>Release Notes</h1><p>New features in this version.</p></body></html>".toByteArray())
        }

        val markdownService = DefaultMarkdownService()
        val service = DefaultGradleDocsIndexService(extractor, HtmlConverter(markdownService), environment, LuceneReaderCache())

        with(ProgressReporter.PRINTLN) {
            // First run ensureIndexed so it populates the index using the mock flow
            service.ensureIndexed(version)

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
        }

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `verify multi-tagging for best practices`() = runTest {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-multi-tag")
        val environment = GradleMcpEnvironment(tempDir)

        val version = "9.4.0"

        val extractor = mockk<ContentExtractorService>()
        every {
            with(any<ProgressReporter>()) {
                extractor.extractEntries(version)
            }
        } returns flow {
            emit("userguide/best_practices_performance.html" to "<html><body><h1>Performance Best Practices</h1><p>Use build cache.</p></body></html>".toByteArray())
            emit("userguide/intro.html" to "<html><body><h1>Introduction</h1><p>Welcome to Gradle.</p></body></html>".toByteArray())
        }

        val markdownService = DefaultMarkdownService()
        val service = DefaultGradleDocsIndexService(extractor, HtmlConverter(markdownService), environment, LuceneReaderCache())

        with(ProgressReporter.PRINTLN) {
            // Index the mocked files
            service.ensureIndexed(version)

            // Search by 'userguide' tag - should find both
            val userguideResults = service.search("tag:userguide", version).results
            assertEquals(2, userguideResults.size)

            // Search by 'best-practices' tag - should find only one
            val bpResults = service.search("tag:best-practices", version).results
            assertEquals(1, bpResults.size)
            assertEquals("Performance Best Practices", bpResults[0].title)

            // Ensure it's also reachable by keyword
            val cacheResults = service.search("cache", version).results
            assertEquals(1, cacheResults.size)
            assertEquals("Performance Best Practices", cacheResults[0].title)
        }

        tempDir.toFile().deleteRecursively()
    }
}
