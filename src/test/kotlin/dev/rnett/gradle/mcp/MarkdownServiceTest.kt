package dev.rnett.gradle.mcp

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

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
        kotlin.test.assertFalse(result.contains("Site Title"), "Should strip header")
        kotlin.test.assertFalse(result.contains("Other"), "Should strip navigation")
        kotlin.test.assertFalse(result.contains("Copyright"), "Should strip footer")
        kotlin.test.assertFalse(result.contains("Top Nav Content"), "Should strip top-nav")
    }

    @Test
    fun `downloadAsMarkdown handles Javadoc-like structure`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """
                    <html>
                    <body>
                        <header role="banner" class="flex-header">
                            <nav role="navigation" class="top-nav">Javadoc Nav</nav>
                        </header>
                        <main role="main">
                            <div class="header">
                                <div class="sub-title">org.gradle.api</div>
                                <h1 title="Interface Project" class="title">Interface Project</h1>
                            </div>
                            <div class="summary">
                                <p>Project summary content.</p>
                            </div>
                        </main>
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

        assertContains(result, "Interface Project")
        assertContains(result, "org.gradle.api")
        assertContains(result, "Project summary content.")
        kotlin.test.assertFalse(result.contains("Javadoc Nav"), "Should strip javadoc nav")
    }
}
