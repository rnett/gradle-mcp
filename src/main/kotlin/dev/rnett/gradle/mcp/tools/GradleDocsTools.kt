package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.GradleDocsService
import dev.rnett.gradle.mcp.mcp.McpContext
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import java.nio.file.Path

class GradleDocsTools(
    private val gradleDocsService: GradleDocsService
) : McpServerComponent("Gradle Docs Tools", "Tools for querying and reading Gradle documentation.") {

    @Serializable
    data class QueryGradleDocsArgs(
        @Description("Search query for the documentation.")
        val query: String? = null,
        @Description("Specific documentation page path to read. If not set, a list of all pages will be returned.")
        val path: String? = null,
        @Description("Specific Gradle version documentation to target. Defaults to project version. Uses the latest if no project root is available.")
        val version: String? = null,
        @Description("If true, fetch the release notes for the version")
        val releaseNotes: Boolean = false,
        @Description("The Gradle project root to detect the version from. If not provided, it will be autodetected from the current working directory or MCP roots if possible. Ignored and not needed if the version is specified.")
        val projectRoot: GradleProjectRootInput? = null
    )

    val gradleDocs by tool<QueryGradleDocsArgs, String>(
        ToolNames.GRADLE_DOCS,
        "Search and read the Gradle User Guide, release notes, and version documentation. `releaseNotes` takes precedence over `path`, which takes precedence over `query` - only one will be used."
    ) { args ->
        val resolvedVersion = resolveVersion(args.version, args.projectRoot)
        when {
            args.releaseNotes -> gradleDocsService.getReleaseNotes(resolvedVersion)
            args.path != null -> gradleDocsService.getDocsPageAsMarkdown(args.path, resolvedVersion)
            args.query != null -> {
                val results = gradleDocsService.searchDocs(args.query, false, resolvedVersion)
                if (results.isEmpty()) {
                    "No documentation matches found for '${args.query}' in version ${resolvedVersion ?: "current"}."
                } else {
                    val displayVersion = resolvedVersion ?: "current"
                    "Search results for '${args.query}' in Gradle $displayVersion:\n\n" +
                            results.joinToString("\n\n") { result ->
                                "### ${result.title}\nPath: `${result.path}`\n\n```markdown\n${result.snippet}\n```"
                            }
                }
            }

            else -> {
                val pages = gradleDocsService.getAllDocsPages(resolvedVersion)
                if (pages.isEmpty()) {
                    "No documentation pages found for version ${resolvedVersion ?: "current"}."
                } else {
                    val displayVersion = resolvedVersion ?: "current"
                    "Available documentation pages for Gradle $displayVersion:\n\n" +
                            pages.joinToString("\n") { page -> "- ${page.title}: `${page.path}`" }
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
