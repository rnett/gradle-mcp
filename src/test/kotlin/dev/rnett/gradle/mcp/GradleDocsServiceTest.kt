package dev.rnett.gradle.mcp

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class GradleDocsServiceTest {

    @Test
    fun `searchDocs uses indexer`() = runTest {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-docs-service")
        val environment = GradleMcpEnvironment(tempDir)

        val indexer = mockk<GradleDocsIndexService>()
        val httpClient = mockk<io.ktor.client.HttpClient>()

        val version = "9.4.0"
        coEvery { indexer.ensureIndexed(version) } returns Unit
        coEvery { indexer.search("test", version) } returns listOf(
            DocsSearchResult("Title", "path.html", "snippet", "userguide")
        )

        val service = DefaultGradleDocsService(httpClient, indexer, environment)
        val results = service.searchDocs("test", version)

        assertEquals(1, results.size)
        assertEquals("Title", results[0].title)
        assertEquals("userguide", results[0].tag)

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `summarizeSections counts files in converted dirs`() = runTest {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-docs-summary")
        val environment = GradleMcpEnvironment(tempDir)

        val version = "9.4.0"
        val convertedDir = tempDir.resolve("cache/gradle-docs/$version/converted")
        Files.createDirectories(convertedDir.resolve("userguide"))
        Files.createDirectories(convertedDir.resolve("dsl"))
        Files.createDirectories(convertedDir.resolve("kotlin-dsl"))

        convertedDir.resolve("userguide/a.md").writeText("content")
        convertedDir.resolve("userguide/b.md").writeText("content")
        convertedDir.resolve("dsl/c.md").writeText("content")
        convertedDir.resolve("kotlin-dsl/d.md").writeText("content")
        convertedDir.resolve("release-notes.md").writeText("content")

        val indexer = mockk<GradleDocsIndexService>()
        val httpClient = mockk<io.ktor.client.HttpClient>()

        coEvery { indexer.ensureIndexed(version) } returns Unit

        val service = DefaultGradleDocsService(httpClient, indexer, environment)
        val summaries = service.summarizeSections(version)

        // userguide (2), dsl (1+1=2), release-notes (1)
        assertEquals(3, summaries.size)

        val userguide = summaries.find { it.tag == "userguide" }
        assertEquals(2, userguide?.count)

        val dsl = summaries.find { it.tag == "dsl" }
        assertEquals(2, dsl?.count)

        val rn = summaries.find { it.tag == "release-notes" }
        assertEquals(1, rn?.count)

        tempDir.toFile().deleteRecursively()
    }
}
