package dev.rnett.gradle.mcp

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleDocsServiceTest {

    private val tempDir = kotlin.io.path.createTempDirectory("gradle-docs-test")
    private val env = GradleMcpEnvironment(tempDir)

    @Test
    fun `test getAllDocsPages with real docs`() = runTest {
        val httpClient = HttpClient(CIO)
        val markdownService = DefaultMarkdownService(HttpClient(CIO))

        val service = DefaultGradleDocsService(httpClient, markdownService, env)
        val pages = service.getAllDocsPages("8.12")

        // Strict assertions on specific pages in 8.12
        val installPage = pages.find { it.path == "installation.html" }
        assert(installPage != null) { "Should find installation.html" }
        assertEquals("Releases > Installing Gradle", installPage?.title)

        val cliPage = pages.find { it.path == "command_line_interface.html" }
        assert(cliPage != null) { "Should find command_line_interface.html" }
        assertEquals("Reference > Command-Line Interface", cliPage?.title)

        assertTrue(pages.all { !it.path.startsWith("http") }, "All paths should be relative")

        // Verify titles have hierarchy (contains '>')
        assertTrue(pages.any { it.title.contains(">") }, "Titles should contain hierarchy separators")

        // External links should be filtered out
        assertTrue(pages.none { it.path.startsWith("http") }, "External links should be filtered out")
    }

    @Test
    fun `test getDocsPageAsMarkdown with real docs`() = runTest {
        val httpClient = HttpClient(CIO)
        val markdownService = DefaultMarkdownService(HttpClient(CIO))

        val service = DefaultGradleDocsService(httpClient, markdownService, env)

        // installation.html in 8.12 is stable
        val markdown = service.getDocsPageAsMarkdown("installation.html", "8.12")

        // Strict content assertions
        assertTrue(markdown.contains("Installing Gradle", ignoreCase = true))
        assertContains(markdown, "Prerequisites", ignoreCase = true)
        assertContains(markdown, "Verification", ignoreCase = true)

        assertFalse(markdown.contains("<html>"), "Should be markdown, not HTML")
        assertFalse(markdown.contains("Getting Started"), "Should not contain navigation links like 'Getting Started'")
        assertFalse(markdown.contains("Community"), "Should not contain site header links like 'Community'")
        assertFalse(markdown.contains("Develocity"), "Should not contain site header links like 'Develocity'")
    }

    @Test
    fun `test caching works`() = runTest {
        val httpClient = HttpClient(CIO)
        val markdownService = DefaultMarkdownService(HttpClient(CIO))
        val service = DefaultGradleDocsService(httpClient, markdownService, env)

        // Force a version that we can cache
        val version = "8.12"

        // Clear cache if exists
        val cacheDir = tempDir.resolve("cache/gradle-docs/$version")
        cacheDir.toFile().deleteRecursively()

        // 1. First call - should fetch and cache
        val pages1 = service.getAllDocsPages(version)
        assertTrue(cacheDir.resolve("pages.json").exists(), "Cache file pages.json should be created")

        // 2. Second call - should use cache (we can verify by checking if it still works even if we "break" the client if we had a mock, but for now just check existence)
        val pages2 = service.getAllDocsPages(version)
        kotlin.test.assertEquals(pages1, pages2)

        // 3. Test markdown caching
        val path = "installation.html"
        val md1 = service.getDocsPageAsMarkdown(path, version)
        val mdCacheFile = cacheDir.resolve("installation.html.md")
        assertTrue(mdCacheFile.exists(), "Cache file installation.html.md should be created")

        val md2 = service.getDocsPageAsMarkdown(path, version)
        kotlin.test.assertEquals(md1, md2)
    }

    @Test
    fun `test getDocsPageAsMarkdown with path traversal`() = runTest {
        val httpClient = HttpClient(CIO)
        val markdownService = DefaultMarkdownService(HttpClient(CIO))
        val service = DefaultGradleDocsService(httpClient, markdownService, env)

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            service.getDocsPageAsMarkdown("../release-notes.html", "8.12")
        }
    }

    @Test
    fun `test getReleaseNotes`() = runTest {
        val httpClient = HttpClient(CIO)
        val markdownService = DefaultMarkdownService(HttpClient(CIO))
        val service = DefaultGradleDocsService(httpClient, markdownService, env)

        val markdown = service.getReleaseNotes("8.12")
        assertTrue(markdown.contains("Gradle Release Notes", ignoreCase = true))
    }

    @Test
    fun `test searchDocs with mock docs`() = runTest {
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/8.12/userguide/userguide.html" -> respond(
                    content = """
                        <html>
                        <body>
                            <nav class="docs-navigation">
                                <h3>Getting Started</h3>
                                <ul>
                                    <li><a href="installation.html">Installation</a></li>
                                    <li><a href="tutorial.html">Tutorial</a></li>
                                    <li><a href="../release-notes.html">Release Notes</a></li>
                                </ul>
                            </nav>
                        </body>
                        </html>
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html")
                )

                "/8.12/userguide/installation.html" -> respond(
                    content = """
                        <html><body><h1>Installing Gradle</h1><p>Follow these steps to install Gradle.</p>
                        <p>Another install mention here.</p></body></html>
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html")
                )

                "/8.12/userguide/tutorial.html" -> respond(
                    content = """
                        <html><body><h1>Gradle Tutorial</h1><p>This is a regex test page.</p></body></html>
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html")
                )

                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val httpClient = HttpClient(mockEngine)
        val markdownService = DefaultMarkdownService(httpClient)
        val service = DefaultGradleDocsService(httpClient, markdownService, env)

        // 1. Text search - should find multiple matches in one page
        val results = service.searchDocs("install", isRegex = false, version = "8.12")
        assertEquals(3, results.size) // 1 in h1, 1 in p, 1 in "Another install mention"
        assertEquals("Getting Started > Installation", results[0].title)
        assertContains(results[0].snippet, "install", ignoreCase = true)
        assertEquals("Getting Started > Installation", results[1].title)
        assertEquals("Getting Started > Installation", results[2].title)

        // 2. Regex search
        val regexResults = service.searchDocs("re.ex", isRegex = true, version = "8.12")
        assertEquals(1, regexResults.size)
        assertEquals("Getting Started > Tutorial", regexResults[0].title)
        assertContains(regexResults[0].snippet, "regex", ignoreCase = true)

        // 3. Verify release notes filtered from getAllDocsPages
        val pages = service.getAllDocsPages("8.12")
        assertTrue(pages.none { it.path.contains("..") }, "Paths with '..' should be filtered out")
        assertTrue(pages.none { it.title.contains("Release Notes") }, "Release notes should be filtered out from user guide listing")
    }
}
