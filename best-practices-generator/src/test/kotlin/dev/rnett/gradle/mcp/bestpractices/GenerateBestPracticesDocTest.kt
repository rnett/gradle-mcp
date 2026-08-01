package dev.rnett.gradle.mcp.bestpractices

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerateBestPracticesDocTest {
    @Test
    fun `recognizes only best practices user guide HTML pages`() {
        assertFalse(isBestPracticesPage("docs/userguide/best_practices.html"))
        assertFalse(isBestPracticesPage("docs/userguide/best_practices_index.html"))
        assertTrue(isBestPracticesPage("docs/userguide/best_practices_dependency_management.html"))
        assertFalse(isBestPracticesPage("docs/dsl/best_practices.html"))
        assertFalse(isBestPracticesPage("docs/userguide/performance.html"))
        assertFalse(isBestPracticesPage("docs/userguide/best_practices.txt"))
    }

    @Test
    fun `extracts substantive content and removes page chrome`() {
        val html = requireNotNull(javaClass.getResource("/fixtures/basic.html")).readText()
        val content = assertNotNull(extractContent(html))
        val markdown = normalizeInternalLinks(
            convertToMarkdown(content),
            "docs/userguide/best_practices.html",
        )

        assertContains(markdown, "Use lazy task registration")
        assertContains(markdown, "Use `gradle_docs(path=\"userguide/performance.md\")`.")
        assertFalse(markdown.contains("Edit this page"))
        assertFalse(markdown.contains("Sidebar navigation"))
        assertFalse(markdown.contains("console.log"))
    }

    @Test
    fun `falls back to the first content heading for an untitled page`() {
        val html = requireNotNull(javaClass.getResource("/fixtures/title-fallback.html")).readText()
        val content = assertNotNull(extractContent(html))

        assertTrue(extractTitle(html, content, "docs/userguide/best_practices_tasks.html") == "Task Configuration")
    }

    @Test
    fun `cleans converted headings navigation badges and internal anchors`() {
        val markdown = """
            Best Practices for Dependencies {#header}
            =========================================

            version 9.6.1  
            On this Page

            * [Introduction](#sec:best_practices_introduction)
            * [References](#references)

            [Introduction](#sec:best_practices_introduction) {#sec:best_practices_introduction}
            -----------------------------------------------------------------------------------

            [![Download](https://img.shields.io/badge/download-blue)]

            See [Explanation](#explanation) and [Gradle](https://gradle.org/).

            {#best-practices-table}
        """.trimIndent()

        val cleaned = cleanContent(markdown)

        assertEquals(
            """
                # Best Practices for Dependencies

                ## Introduction

                See Explanation and [Gradle](https://gradle.org/).
            """.trimIndent(),
            cleaned,
        )
    }

    @Test
    fun `extracts one-line summary from section markdown`() {
        val markdown = """
            # Avoid DependsOn

            Wire task inputs and outputs instead of using `dependsOn` so that up-to-date checking can skip work.
            See [the docs](https://docs.gradle.org/current/userguide/more_about_tasks.html) for details.
            (Use `gradle_docs(path="userguide/more_about_tasks.html")`.)

            ## Explanation
            More details here...
        """.trimIndent()

        val summary = extractSummary(markdown)

        assertContains(summary, "Wire task inputs and outputs")
        assertFalse(summary.contains("https://"))
        assertFalse(summary.contains("gradle_docs"))
        assertTrue(summary.length <= 161)
    }

    @Test
    fun `removes normalized Gradle doc annotations with nested URL parentheses`() {
        val markdown = """
            # Avoid DependsOn

            Use [dependsOn](https://docs.gradle.org/current/javadoc/Task.html#dependsOn(java.lang.Object) (Use `gradle_docs(path="javadoc/Task.html#dependsOn(java.lang.Object")`.)) only when needed.
        """.trimIndent()

        assertEquals("Use dependsOn only when needed.", extractSummary(markdown))
    }

    @Test
    fun `generates categorized index grouped by area with summaries and tags`() {
        val sections = listOf(
            BestPracticesSection(
                title = "Avoid DependsOn",
                markdown = "# Avoid DependsOn\n\nWire task inputs instead of dependsOn.\n\n## Explanation\n...",
                tags = "#tasks #inputs-and-outputs",
                sourcePage = "Best Practices for Tasks",
            ),
            BestPracticesSection(
                title = "Use Version Catalogs",
                markdown = "# Use Version Catalogs\n\nCentralize dependency versions.\n\n## Explanation\n...",
                tags = "#dependencies",
                sourcePage = "Best Practices for Dependencies",
            ),
            BestPracticesSection(
                title = "Another Task Practice",
                markdown = "# Another Task Practice\n\nDo something else with tasks.\n\n## Explanation\n...",
                tags = "#tasks",
                sourcePage = "Best Practices for Tasks",
            ),
        )

        val index = generateIndex(sections, "9.6.1")
        val tasksSection = index.substringAfter("## Best Practices for Tasks")
            .substringBefore("## Best Practices for Dependencies")

        assertContains(index, "## Best Practices for Tasks")
        assertContains(index, "## Best Practices for Dependencies")
        assertContains(tasksSection, "Avoid DependsOn")
        assertContains(tasksSection, "Another Task Practice")
        assertContains(index, "Wire task inputs instead of dependsOn")
        assertContains(index, "`#tasks`")
        assertContains(index, "## Browse by Tag")
        assertContains(index, "- `#tasks` — avoid-dependson, another-task-practice")
    }

    @Test
    fun `splits large pages promotes headings and moves tags into section metadata`() {
        val page = BestPracticesPage(
            sourcePath = "docs/userguide/best_practices_example.html",
            title = "Example Best Practices",
            markdown = """
                # Example Best Practices {#page}

                Introductory text.

                ## First Topic

                ### Explanation

                First explanation.

                Console Output
                --------------

                This Setext-looking content remains inside the first topic.

                ### References

                * First reference

                ### Tags

                [#first](https://example.com/first), [#shared](https://example.com/shared)

                ## Second Topic

                ### Explanation

                Second explanation.

                ## Third Topic

                #### Detailed Example

                Third example.

                ## Fourth Topic

                Fourth explanation.
            """.trimIndent(),
        )

        val sections = splitPageIntoSections(page)

        assertEquals(listOf("First Topic", "Second Topic", "Third Topic", "Fourth Topic"), sections.map { it.title })
        assertEquals("#first, #shared", sections.first().tags)
        assertTrue(sections.first().markdown.startsWith("# First Topic"))
        assertContains(sections.first().markdown, "## References")
        assertContains(sections.first().markdown, "# Console Output")
        assertFalse(sections.first().markdown.contains("Tags"))
        assertContains(sections[2].markdown, "### Detailed Example")
        assertTrue(sections.all { it.sourcePage == page.title })
    }

    @Test
    fun `writes per-section files for multi-section pages and generates index`() {
        val outputDirectory = Files.createTempDirectory("best-practices-test-")
        try {
            Files.writeString(outputDirectory.resolve("best_practices.md"), "obsolete monolithic output")
            val existingDirectory = Files.createDirectories(outputDirectory.resolve("best-practices"))
            Files.writeString(existingDirectory.resolve("README.md"), "obsolete index")
            Files.writeString(existingDirectory.resolve("obsolete-topic.md"), "obsolete topic")

            val html = requireNotNull(javaClass.getResource("/fixtures/multi-section.html")).readText()
            val content = assertNotNull(extractContent(html))
            val multiSectionPage = BestPracticesPage(
                sourcePath = "docs/userguide/best_practices_multi_section.html",
                title = extractTitle(html, content, "docs/userguide/best_practices_multi_section.html"),
                markdown = normalizeInternalLinks(
                    convertToMarkdown(content),
                    "docs/userguide/best_practices_multi_section.html",
                ),
            )
            val smallPage = BestPracticesPage(
                sourcePath = "docs/userguide/best_practices_security.html",
                title = "Best Practices for Security",
                markdown = """
                    # Best Practices for Security

                    ## Validate Checksums

                    Keep builds secure.

                    ## Validate the Wrapper

                    Keep the wrapper secure.

                    ## Protect Credentials

                    Keep credentials secure.
                """.trimIndent(),
            )

            val generatedDirectory = writePages(
                outputDir = outputDirectory,
                version = "9.6.1",
                pages = listOf(multiSectionPage, smallPage),
            )
            val firstTopic = generatedDirectory.resolve("first-topic.md").readText()
            val index = generatedDirectory.resolve("_index.md").readText()

            assertFalse(Files.exists(generatedDirectory.resolve("README.md")))
            assertFalse(Files.exists(generatedDirectory.resolve("obsolete-topic.md")))
            assertTrue(Files.exists(generatedDirectory.resolve("second-topic.md")))
            assertTrue(Files.exists(generatedDirectory.resolve("third-topic.md")))
            assertTrue(Files.exists(generatedDirectory.resolve("fourth-topic.md")))
            assertTrue(Files.exists(generatedDirectory.resolve("best-practices-for-security.md")))

            // Generated provenance headers with self-verifying content hashes
            assertContains(firstTopic, "class: generated")
            assertContains(firstTopic, "generator: best-practices")
            assertContains(firstTopic, "gradle-version: 9.6.1")
            assertContains(firstTopic, "hash: ${sha256Hex(firstTopic.substringAfter("-->\n"))}")
            assertContains(index, "hash: ${sha256Hex(index.substringAfter("-->\n"))}")

            val firstTopicBody = firstTopic.substringAfter("-->\n")
            assertTrue(firstTopicBody.startsWith("# First Topic"))
            assertContains(firstTopicBody, "## References")
            assertFalse(firstTopicBody.contains("## Tags"))
            assertContains(firstTopicBody, "gradle_docs` with `tag:best-practices`")
            assertContains(index, "## Multi-section Best Practices")
            assertContains(index, "- [First Topic](first-topic.md) `#first` `#shared`")
            assertContains(index, "## Best Practices for Security")
            assertContains(index, "- [Best Practices for Security](best-practices-for-security.md)")
            assertContains(index, "## Browse by Tag")
            assertContains(index, "- `#shared` — first-topic")
            assertFalse(Files.exists(outputDirectory.resolve("best_practices.md")))
        } finally {
            outputDirectory.toFile().deleteRecursively()
        }
    }
}
