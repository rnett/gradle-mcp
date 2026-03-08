package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.dependencies.gradle.DefaultDistributionDownloaderService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistributionDownloaderServiceTest {

    @Test
    fun `downloadDocs downloads zip and moves to cache`() = runTest {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-cache")
        val environment = GradleMcpEnvironment(tempDir)

        val mockEngine = MockEngine { request ->
            assertEquals("https://example.com/gradle-9.4.0-docs.zip", request.url.toString())
            respond(
                content = "dummy-zip-content",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/zip")
            )
        }
        val client = HttpClient(mockEngine)
        val service = DefaultDistributionDownloaderService(client, environment, "https://example.com/")

        val path = with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            service.downloadDocs("9.4.0")
        }

        assertTrue(path.exists(), "Cache file should exist")
        assertEquals("dummy-zip-content", Files.readString(path))

        // Verify relative path
        val expectedRelative = "reading_gradle_docs/9.4.0/gradle-9.4.0-docs.zip"
        assertTrue(path.toString().replace("\\", "/").endsWith(expectedRelative), "Should follow expected cache structure")

        tempDir.toFile().deleteRecursively()
    }
}
