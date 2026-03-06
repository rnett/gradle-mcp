package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.DocsPageContent
import dev.rnett.gradle.mcp.GradleDocsService
import dev.rnett.gradle.mcp.mcp.McpContext
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable
import java.nio.file.Path

class GradleDocsTools(
    private val gradleDocsService: GradleDocsService
) : McpServerComponent("Gradle Docs Tools", "Tools for querying and reading Gradle documentation.") {

    @Serializable
    data class QueryGradleDocsArgs(
        @Description("Searching the documentation. Use `tag:<section>` to scope (e.g., `tag:userguide working with files`).")
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
            |ALWAYS use this tool to search and read official Gradle documentation (User Guide, DSL Reference, Release Notes).
            |It provides instantaneous, locally-indexed access to Gradle documentation specific to the project's version, making it far superior to generic web searches.
            |Call with no arguments to browse available sections and tags.
        """.trimMargin()
    ) { args ->
        val resolvedVersion = resolveVersion(args.version, args.projectRoot)
        when {
            args.path != null -> {
                val content = gradleDocsService.getDocsPageContent(args.path, resolvedVersion)
                when (content) {
                    is DocsPageContent.Markdown -> CallToolResult(listOf(TextContent(content.content)))
                    is DocsPageContent.Image -> CallToolResult(listOf(ImageContent(content.base64, content.mimeType)))
                }
            }

            args.query != null -> {
                val results = gradleDocsService.searchDocs(args.query, resolvedVersion)
                if (results.isEmpty()) {
                    CallToolResult(listOf(TextContent("No documentation matches found for '${args.query}' in version ${resolvedVersion ?: "current"}.")))
                } else {
                    val displayVersion = resolvedVersion ?: "current"
                    val pagedResults = paginate(results, args.pagination, "matches") { result ->
                        "### ${result.title}\nSection: `${result.tag}` | Path: `${result.path}`\n\n${result.snippet}\n"
                    }
                    val text = "Search results for '${args.query}' in Gradle $displayVersion:\n\n$pagedResults"
                    CallToolResult(listOf(TextContent(text)))
                }
            }

            else -> {
                val summaries = gradleDocsService.summarizeSections(resolvedVersion)
                if (summaries.isEmpty()) {
                    CallToolResult(listOf(TextContent("No documentation sections found for version ${resolvedVersion ?: "current"}.")))
                } else {
                    val displayVersion = resolvedVersion ?: "current"
                    val pagedSummaries = paginate(summaries, args.pagination, "sections") { section ->
                        "- **${section.displayName}** (`tag:${section.tag}`): ${section.count} pages"
                    }
                    val text = "Documentation sections for Gradle $displayVersion:\n\n$pagedSummaries" +
                            "\n\nUse `query=\"tag:<tag> ...\"` to search a specific section, or provide a `path` from search results to read a page."
                    CallToolResult(listOf(TextContent(text)))
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
