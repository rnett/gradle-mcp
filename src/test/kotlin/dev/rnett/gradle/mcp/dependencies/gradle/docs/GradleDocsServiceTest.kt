package dev.rnett.gradle.mcp.dependencies.gradle.docs

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import io.ktor.client.HttpClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradleDocsServiceTest {

    private fun createVersionService(): GradleVersionService {
        val versionService = mockk<GradleVersionService>()
        coEvery { versionService.resolveVersion(any()) } answers { it.invocation.args[0] as? String ?: "9.4.0" }
        return versionService
    }

    @Test
    fun `getDocsPageContent returns file content`() = runTest {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-docs-content")
        val environment = GradleMcpEnvironment(tempDir)

        val version = "9.4.0"
        val convertedDir = tempDir.resolve("cache/reading_gradle_docs/$version/converted")
        Files.createDirectories(convertedDir.resolve("userguide"))
        convertedDir.resolve("userguide/test.md").writeText("# Test Page")

        val indexer = mockk<GradleDocsIndexService>()
        val httpClient = mockk<HttpClient>()
        coEvery {
            with(any<ProgressReporter>()) {
                indexer.ensureIndexed(version)
            }
        } returns Unit

        val service = DefaultGradleDocsService(httpClient, indexer, environment, createVersionService())
        val content = with(ProgressReporter.PRINTLN) {
            service.getDocsPageContent("userguide/test.md", version)
        }

        assertTrue(content is DocsPageContent.Markdown)
        assertEquals("# Test Page", (content as DocsPageContent.Markdown).content)

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `getDocsPageContent returns directory listing for dot`() = runTest {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-docs-dir")
        val environment = GradleMcpEnvironment(tempDir)

        val version = "9.4.0"
        val convertedDir = tempDir.resolve("cache/reading_gradle_docs/$version/converted")
        Files.createDirectories(convertedDir.resolve("userguide"))
        convertedDir.resolve("release-notes.md").writeText("notes")

        val indexer = mockk<GradleDocsIndexService>()
        val httpClient = mockk<HttpClient>()
        coEvery {
            with(any<ProgressReporter>()) {
                indexer.ensureIndexed(version)
            }
        } returns Unit

        val service = DefaultGradleDocsService(httpClient, indexer, environment, createVersionService())
        val content = with(ProgressReporter.PRINTLN) {
            service.getDocsPageContent(".", version)
        }

        assertTrue(content is DocsPageContent.Markdown)
        val text = (content as DocsPageContent.Markdown).content
        assertTrue(text.contains("# Directory: /"))
        assertTrue(text.contains("- userguide/"))
        assertTrue(text.contains("- release-notes.md"))

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `searchDocs uses indexer`() = runTest {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-docs-service")
        val environment = GradleMcpEnvironment(tempDir)

        val indexer = mockk<GradleDocsIndexService>()
        val httpClient = mockk<HttpClient>()

        val version = "9.4.0"
        coEvery {
            with(any<ProgressReporter>()) {
                indexer.ensureIndexed(version)
            }
        } returns Unit
        coEvery {
            with(any<ProgressReporter>()) {
                indexer.search("test", version)
            }
        } returns DocsSearchResponse(
            listOf(
                DocsSearchResult("Title", "path.html", "snippet", "userguide")
            )
        )

        val service = DefaultGradleDocsService(httpClient, indexer, environment, createVersionService())
        val response = with(ProgressReporter.PRINTLN) {
            service.searchDocs("test", version)
        }
        val results = response.results

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
        val convertedDir = tempDir.resolve("cache/reading_gradle_docs/$version/converted")
        Files.createDirectories(convertedDir.resolve("userguide"))
        Files.createDirectories(convertedDir.resolve("dsl"))
        Files.createDirectories(convertedDir.resolve("kotlin-dsl"))

        convertedDir.resolve("userguide/a.md").writeText("content")
        convertedDir.resolve("userguide/b.md").writeText("content")
        convertedDir.resolve("dsl/c.md").writeText("content")
        convertedDir.resolve("kotlin-dsl/d.md").writeText("content")
        convertedDir.resolve("release-notes.md").writeText("content")

        val indexer = mockk<GradleDocsIndexService>()
        val httpClient = mockk<HttpClient>()

        coEvery {
            with(any<ProgressReporter>()) {
                indexer.ensureIndexed(version)
            }
        } returns Unit

        val service = DefaultGradleDocsService(httpClient, indexer, environment, createVersionService())
        val summaries = with(ProgressReporter.PRINTLN) {
            service.summarizeSections(version)
        }

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

    @Test
    fun `summarizeSections includes best-practices tag if files exist`() = runTest {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-docs-bp-summary")
        val environment = GradleMcpEnvironment(tempDir)

        val version = "9.4.0"
        val convertedDir = tempDir.resolve("cache/reading_gradle_docs/$version/converted")
        Files.createDirectories(convertedDir.resolve("userguide"))

        convertedDir.resolve("userguide/best_practices_perf.md").writeText("content")
        convertedDir.resolve("userguide/best_practices_config.md").writeText("content")
        convertedDir.resolve("userguide/intro.md").writeText("content")

        val indexer = mockk<GradleDocsIndexService>()
        val httpClient = mockk<HttpClient>()
        coEvery {
            with(any<ProgressReporter>()) {
                indexer.ensureIndexed(version)
            }
        } returns Unit

        val service = DefaultGradleDocsService(httpClient, indexer, environment, createVersionService())
        val summaries = with(ProgressReporter.PRINTLN) {
            service.summarizeSections(version)
        }

        // userguide (3), best-practices (2)
        assertEquals(2, summaries.size)

        val userguide = summaries.find { it.tag == "userguide" }
        assertEquals(3, userguide?.count)

        val bp = summaries.find { it.tag == "best-practices" }
        assertEquals(2, bp?.count)

        tempDir.toFile().deleteRecursively()
    }
    @Test
    fun `getDocsPageContent resolves fragments queries and html paths`() = runTest {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-docs-fragments")
        val environment = GradleMcpEnvironment(tempDir)
        val version = "9.4.0"
        val convertedDir = tempDir.resolve("cache/reading_gradle_docs/$version/converted/userguide")
        Files.createDirectories(convertedDir)
        convertedDir.resolve("sections.md").writeText(
            """
            # Page

            ## Excluding transitive dependencies {#sec:exclude-trans-deps}

            Details.

            ### Nested detail

            Nested.

            ## Other section

            Other.
            """.trimIndent(),
        )
        val indexer = mockk<GradleDocsIndexService>()
        coEvery {
            with(any<ProgressReporter>()) { indexer.ensureIndexed(version) }
        } returns Unit
        val service = DefaultGradleDocsService(mockk<HttpClient>(), indexer, environment, createVersionService())

        val byAnchor = with(ProgressReporter.PRINTLN) {
            service.getDocsPageContent("userguide/sections.html#sec:exclude-trans-deps?ignored=true", version)
        } as DocsPageContent.Markdown
        assertTrue(byAnchor.content.contains("# Section: sec:exclude-trans-deps"))
        assertTrue(byAnchor.content.contains("Nested."))
        assertTrue(byAnchor.content.contains("query string \"ignored=true\"; ignored"))
        assertTrue(!byAnchor.content.contains("Other."))

        val bySlug = with(ProgressReporter.PRINTLN) {
            service.getDocsPageContent("userguide/sections.md#other-section", version)
        } as DocsPageContent.Markdown
        assertTrue(bySlug.content.contains("Other."))

        val failure = runCatching {
            with(ProgressReporter.PRINTLN) {
                service.getDocsPageContent("userguide/sections.md#no-such-anchor", version)
            }
        }.exceptionOrNull()
        assertTrue(failure?.message?.contains("userguide/sections.md") == true)
        assertTrue(failure?.message?.contains("#no-such-anchor") == true)
        assertTrue(failure?.message?.contains("Available fragments on this page:") == true)
        assertTrue(failure?.message?.contains("#page") == true)
        assertTrue(failure?.message?.contains("#sec:exclude-trans-deps") == true)
        assertTrue(failure?.message?.contains("#nested-detail") == true)
        assertTrue(failure?.message?.contains("#other-section") == true)
        assertTrue(failure?.message?.contains("Excluding transitive dependencies") == true)
        assertTrue(failure?.message?.contains("Nested detail") == true)
        assertTrue(failure?.message?.contains("Other section") == true)
        assertTrue(failure?.message?.contains("Retry with a fragment from the list above") == true)
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `advertised fragments resolve on the same page`() = runTest {
        val page = """
            # Page {#page}
            ## Explicit section {#sec:explicit}
            ### Slug Only
        """.trimIndent()
        val (service, tempDir) = createFragmentService(page)
        val failure = runCatching {
            with(ProgressReporter.PRINTLN) {
                service.getDocsPageContent("userguide/sections.md#missing", "9.4.0")
            }
        }.exceptionOrNull() ?: error("Expected unresolved fragment to fail")
        val fragments = Regex("`#([^`]+)` —").findAll(failure.message.orEmpty()).map { it.groupValues[1] }.toList()
        assertEquals(listOf("page", "sec:explicit", "slug-only"), fragments)
        fragments.forEach { fragment ->
            with(ProgressReporter.PRINTLN) {
                service.getDocsPageContent("userguide/sections.md#$fragment", "9.4.0")
            }
        }
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `fragment listing de-duplicates presented fragments`() = runTest {
        val (service, tempDir) = createFragmentService("# Same title\n## Same-title")
        val failure = runCatching {
            with(ProgressReporter.PRINTLN) {
                service.getDocsPageContent("userguide/sections.md#missing", "9.4.0")
            }
        }.exceptionOrNull() ?: error("Expected unresolved fragment to fail")
        assertEquals(1, Regex("`#same-title` —").findAll(failure.message.orEmpty()).count())
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `fragment listing reflects heading hierarchy`() = runTest {
        val (service, tempDir) = createFragmentService("## Parent\n### Child")
        val failure = runCatching {
            with(ProgressReporter.PRINTLN) {
                service.getDocsPageContent("userguide/sections.md#missing", "9.4.0")
            }
        }.exceptionOrNull() ?: error("Expected unresolved fragment to fail")
        val message = failure.message.orEmpty()
        assertTrue(message.contains("- `#parent`"))
        assertTrue(message.contains("  - `#child`"))
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `fragment listing truncates after fifty headings`() = runTest {
        val page = (1..55).joinToString("\n") { "# Heading $it" }
        val (service, tempDir) = createFragmentService(page)
        val failure = runCatching {
            with(ProgressReporter.PRINTLN) {
                service.getDocsPageContent("userguide/sections.md#missing", "9.4.0")
            }
        }.exceptionOrNull() ?: error("Expected unresolved fragment to fail")
        val message = failure.message.orEmpty()
        assertTrue(Regex("(?m)^\\s*- `#").findAll(message).count() <= 50)
        assertTrue(message.contains("... and 5 more fragments not shown (55 total on this page)."))
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `fragment listing explains pages without headings`() = runTest {
        val (service, tempDir) = createFragmentService("Paragraph only.")
        val failure = runCatching {
            with(ProgressReporter.PRINTLN) {
                service.getDocsPageContent("userguide/sections.md#missing", "9.4.0")
            }
        }.exceptionOrNull() ?: error("Expected unresolved fragment to fail")
        val message = failure.message.orEmpty()
        assertTrue(message.contains("This page has no recognized heading fragments"))
        assertTrue(!message.contains("Available fragments on this page:"))
        tempDir.toFile().deleteRecursively()
    }

    private fun createFragmentService(page: String): Pair<DefaultGradleDocsService, java.nio.file.Path> {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-fragment-listing")
        val environment = GradleMcpEnvironment(tempDir)
        val version = "9.4.0"
        val convertedDir = tempDir.resolve("cache/reading_gradle_docs/$version/converted/userguide")
        Files.createDirectories(convertedDir)
        convertedDir.resolve("sections.md").writeText(page)
        val indexer = mockk<GradleDocsIndexService>()
        coEvery {
            with(any<ProgressReporter>()) { indexer.ensureIndexed(version) }
        } returns Unit
        return DefaultGradleDocsService(mockk<HttpClient>(), indexer, environment, createVersionService()) to tempDir
    }
}
