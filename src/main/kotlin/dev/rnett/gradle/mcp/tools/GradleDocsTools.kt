package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DocsPageContent
import dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsService
import dev.rnett.gradle.mcp.mcp.McpContext
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable

class GradleDocsTools(
    private val gradleDocsService: GradleDocsService,
    private val versionService: GradleVersionService
) : McpServerComponent("Gradle Docs Tools", "Tools for querying and reading Gradle documentation.") {

    @Serializable
    data class QueryGradleDocsArgs(
        @Description("Search query. Use `tag:<section>` to scope (e.g., `tag:userguide`, `tag:dsl`).")
        val query: String? = null,
        @Description("Read a specific doc page path (e.g., 'userguide/command_line_interface.md'). Overrides query.")
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
            |Searches and reads official Gradle documentation (User Guide, DSL Reference, Release Notes) for the project's exact Gradle version; use instead of generic web searches.
            |
            |### Documentation Tags
            |- `tag:userguide` — high-level concepts
            |- `tag:dsl` — property/method syntax
            |- `tag:release-notes` — breaking changes and new features
            |- `tag:best-practices` — performance and design recommendations
            |- `tag:javadoc` — API docs
            |- `tag:samples` — code examples
            |
            |Call with no arguments to browse available sections. Use `tag:<tag> <term>` to scope searches. Use `path="."` to explore the file tree.
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
            val detected = GradlePathUtils.getGradleVersion(kotlin.io.path.Path(root.projectRoot))
            if (detected != null) return detected
        }

        return null
    }
}
