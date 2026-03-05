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
        @Description("The search query for the documentation. Use `tag:<section>` to filter (e.g., `tag:userguide configuration`). Available tags: `userguide`, `dsl`, `javadoc`, `samples`, `release-notes`.")
        val query: String? = null,
        @Description("The specific documentation page or image path to read (e.g., 'userguide/command_line_interface.md' or 'userguide/img/cli.png'). If omitted and no query is provided, a summary of documentation sections is returned.")
        val path: String? = null,
        @Description("The specific Gradle version documentation to target (e.g., '8.6'). Defaults to the project's detected version or the latest release.")
        val version: String? = null,
        @Description("The absolute path to the project root directory. Used to automatically detect the project's Gradle version for documentation targeting.")
        val projectRoot: GradleProjectRootInput? = null,
        val pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS
    )

    val gradleDocs by tool<QueryGradleDocsArgs, CallToolResult>(
        ToolNames.GRADLE_DOCS,
        """
            |Search and read official Gradle documentation, including the User Guide, DSL Reference, Javadoc, and Release Notes.
            |
            |### Features
            |- **Unified Search**: Search across all documentation or scope to specific sections using tags.
            |- **Scoped Searching**: Use the `tag:` syntax in your query to target specific documentation areas:
            |  - `tag:userguide <query>`: Search the Gradle User Guide.
            |  - `tag:dsl <query>`: Search the DSL Reference (Groovy and Kotlin DSL).
            |  - `tag:javadoc <query>`: Search the Java API Reference.
            |  - `tag:samples <query>`: Search Gradle samples and examples.
            |  - `tag:release-notes <query>`: Search within version release notes.
            |- **Direct Page and Asset Access**: Read specific pages (.md) or view images (.png, .jpg, etc.) by providing their `path`.
            |- **Section Summaries**: Call with no arguments to see available documentation sections and their content counts for the targeted version.
            |- **Standardized Pagination**: Large result sets (search matches or section lists) are paginated. Use `offset` and `limit` to browse large outputs safely.
            |
            |### Common Usage Patterns
            |- **Summary of Docs**: `gradle_docs()`
            |- **Search User Guide**: `gradle_docs(query="tag:userguide working with files")`
            |- **Read Page**: `gradle_docs(path="userguide/command_line_interface.md")`
            |- **Read Image**: `gradle_docs(path="userguide/img/command-line-options.png")`
            |- **Target Specific Version**: `gradle_docs(query="tag:release-notes", version="8.5")`
            |
            |Note: `path` takes precedence over `query`.
            |For detailed navigation strategies and available tags, refer to the `gradle-docs` skill.
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
