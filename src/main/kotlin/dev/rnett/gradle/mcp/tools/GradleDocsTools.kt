package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.DocsPageContent
import dev.rnett.gradle.mcp.GradleDocsService
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.mcp.McpContext
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable
import java.nio.file.Path

class GradleDocsTools(
    private val gradleDocsService: GradleDocsService,
    private val versionService: GradleVersionService
) : McpServerComponent("Gradle Docs Tools", "Tools for querying and reading Gradle documentation.") {

    @Serializable
    data class QueryGradleDocsArgs(
        @Description("Searching the documentation. Use `tag:<section>` to scope (e.g., `tag:userguide`, `tag:best-practices` for official recommendations).")
        val query: String? = null,
        @Description("Reading a specific documentation page or asset path (e.g., 'userguide/command_line_interface.md'). Takes precedence over query.")
        val path: String? = null,
        @Description("Targeting a specific Gradle version (e.g., '8.6'). Defaults to the detected project version.")
        val version: String? = null,
        @Description("Detecting the project's Gradle version automatically by providing the project root.")
        val projectRoot: GradleProjectRootInput? = null,
        val pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS
    )

    val gradleDocs by tool<QueryGradleDocsArgs, CallToolResult>(
        ToolNames.GRADLE_DOCS,
        """
            |ALWAYS use this tool to search and read official Gradle documentation instead of generic web searches.
            |This provides instantaneous, locally-indexed access to the authoritative User Guide, DSL Reference, and Release Notes specific to YOUR project's exact Gradle version.
            |
            |### Common Documentation Tags
            |
            |- **`tag:userguide`**: The official Gradle User Guide (high-level concepts).
            |- **`tag:dsl`**: The Gradle DSL Reference (exact property/method syntax).
            |- **`tag:release-notes`**: Version-specific breaking changes and new features.
            |- **`tag:best-practices`**: Official recommendations for performance and design.
            |- **`tag:javadoc`**: Detailed technical API documentation.
            |- **`tag:samples`**: Official code examples.
            |
            |### Research Best Practices
            |
            |1.  **Browse First**: Call with no arguments to see available sections and page counts.
            |2.  **Scope Surgically**: Use `tag:<tag> <term>` in the `query` to narrow results.
            |3.  **Read Recursively**: Use `path="."` or `path=""` to explore the documentation file tree.
            |4.  **Target Versions**: Use `version="8.6"` to check behavior in different Gradle releases.
        """.trimMargin()
    ) { args ->
        val inputVersion = resolveVersion(args.version, args.projectRoot)
        val resolvedVersion = versionService.resolveVersion(inputVersion)
        with(progressReporter) {
            when {
                args.path != null -> {
                    val content = gradleDocsService.getDocsPageContent(args.path, resolvedVersion)
                    when (content) {
                        is DocsPageContent.Markdown -> CallToolResult(listOf(TextContent(content.content)))
                        is DocsPageContent.Image -> CallToolResult(listOf(ImageContent(content.base64, content.mimeType)))
                    }
                }

                args.query != null -> {
                    val response = gradleDocsService.searchDocs(args.query, resolvedVersion)
                    if (response.error != null) {
                        CallToolResult(listOf(TextContent(response.error)), isError = true)
                    } else if (response.results.isEmpty()) {
                        CallToolResult(listOf(TextContent("No documentation matches found for '${args.query}' in Gradle $resolvedVersion.")))
                    } else {
                        val header = buildString {
                            if (response.interpretedQuery != null) {
                                appendLine("Interpreted query: `${response.interpretedQuery}`")
                            }
                            appendLine("Search results for '${args.query}' in Gradle $resolvedVersion:\n")
                        }
                        val pagedResults = paginate(response.results, args.pagination, "matches") { result ->
                            "### ${result.title}\nSection: `${result.tag}` | Path: `${result.path}`\n\n${result.snippet}\n"
                        }
                        CallToolResult(listOf(TextContent(header + pagedResults)))
                    }
                }

                else -> {
                    val summaries = gradleDocsService.summarizeSections(resolvedVersion)
                    if (summaries.isEmpty()) {
                        CallToolResult(listOf(TextContent("No documentation sections found for Gradle $resolvedVersion.")))
                    } else {
                        val pagedSummaries = paginate(summaries, args.pagination, "sections") { section ->
                            "- **${section.displayName}** (`tag:${section.tag}`): ${section.count} pages"
                        }
                        val text = "Documentation sections for Gradle $resolvedVersion:\n\n$pagedSummaries" +
                                "\n\nUse `query=\"tag:<tag> ...\"` to search a specific section, or provide a `path` from search results to read a page."
                        CallToolResult(listOf(TextContent(text)))
                    }
                }
            }
        }
    }

    private fun McpToolContext.resolveVersion(version: String?, projectRoot: GradleProjectRootInput?): String? {
        if (version != null) return version

        val root = try {
            val ctx: McpContext = this
            with(ctx) {
                (projectRoot ?: GradleProjectRootInput.DEFAULT).resolve()
            }
        } catch (_: Exception) {
            null
        }

        if (root != null) {
            val detected = GradlePathUtils.getGradleVersion(Path.of(root.projectRoot))
            if (detected != null) return detected
        }

        return null
    }
}
