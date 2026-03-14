package dev.rnett.gradle.mcp.dependencies.gradle.docs

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MarkdownServiceTest {

    @Test
    fun `downloadAsMarkdown converts real Gradle docs to Markdown`() = runTest {
        val client = HttpClient(CIO)
        val service = DefaultMarkdownService(client)

        val url = "https://docs.gradle.org/current/userguide/installation.html"
        val markdown = try {
            service.downloadAsMarkdown(url)
        } catch (e: Exception) {
            println("Failed to fetch real Gradle docs: ${e.message}")
            return@runTest
        }

        assertContains(markdown, "Installing Gradle", ignoreCase = true)
        assertContains(markdown, "#", false, "Markdown should contain headings")
    }

    @Test
    fun `downloadAsMarkdown uses markdown if returned by server`() = runTest {
        val mockEngine = MockEngine { request ->
            assertContains(request.headers[HttpHeaders.Accept] ?: "", "text/markdown")
            respond(
                content = "# Markdown Content",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/markdown")
            )
        }
        val client = HttpClient(mockEngine)
        val service = DefaultMarkdownService(client)

        val result = service.downloadAsMarkdown("https://example.com/test.md")
        assertEquals("# Markdown Content", result)
    }

    @Test
    fun `downloadAsMarkdown strips navigation and header-footer`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """
                    <html>
                    <head><title>Test</title></head>
                    <body>
                        <header><h1>Site Title</h1></header>
                        <nav class="docs-navigation">
                            <ul><li><a href="other.html">Other</a></li></ul>
                        </nav>
                        <div class="top-nav">Top Nav Content</div>
                        <main>
                            <article>
                                <h1>Main Content</h1>
                                <p>This is the important stuff.</p>
                            </article>
                        </main>
                        <footer class="site-footer">
                            <p>Copyright 2024</p>
                        </footer>
                    </body>
                    </html>
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html")
            )
        }
        val client = HttpClient(mockEngine)
        val service = DefaultMarkdownService(client)

        val result = service.downloadAsMarkdown("https://example.com/test.html")

        assertContains(result, "Main Content")
        assertContains(result, "This is the important stuff.")

        // Should NOT contain header/nav/footer content
        assertFalse(result.contains("Site Title"), "Should strip header")
        assertFalse(result.contains("Other"), "Should strip navigation")
        assertFalse(result.contains("Copyright"), "Should strip footer")
        assertFalse(result.contains("Top Nav Content"), "Should strip top-nav")
    }

    @Test
    fun `convertHtml only runs flexmark`() {
        val service = DefaultMarkdownService()
        val html = "<h1>Title</h1><p>Content</p>"
        val result = service.convertHtml(html)
        assertEquals("# Title\n\nContent\n", result)
    }
}
