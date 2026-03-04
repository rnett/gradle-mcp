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
        @Description("The authoritative search query for the documentation. Supports keywords and phrases to find relevant User Guide sections.")
        val query: String? = null,
        @Description("The specific documentation page path to read (e.g., 'command_line_interface.html'). If omitted, a searchable list of all pages is returned.")
        val path: String? = null,
        @Description("The specific Gradle version documentation to target (e.g., '8.6'). Defaults to the project's detected version or the latest release.")
        val version: String? = null,
        @Description("If true, fetches the authoritative release notes for the specified version. Ideal for researching breaking changes and new features.")
        val releaseNotes: Boolean = false,
        @Description("The absolute path to the project root directory. Used to automatically detect the project's Gradle version for high-resolution documentation targeting.")
        val projectRoot: GradleProjectRootInput? = null
    )

    val gradleDocs by tool<QueryGradleDocsArgs, String>(
        ToolNames.GRADLE_DOCS,
        """
            |The authoritative tool for searching and reading official Gradle documentation, release notes, and version-specific guides.
            |It provides high-performance access to the entire Gradle User Guide, rendered as markdown for seamless agent consumption.
            |
            |### Authoritative Features
            |- **Precision Search**: Use `query` to find specific sections, DSL elements, or plugin documentation across the entire authoritative guide.
            |- **Exhaustive Content Retrieval**: Read full documentation pages directly in your context using the `path` argument.
            |- **Authoritative Release Insights**: Set `releaseNotes=true` to retrieve the definitive list of changes, improvements, and deprecations for any Gradle version.
            |- **Version-Specific Targeting**: Automatically targets your project's Gradle version or allows for surgical manual version selection.
            |
            |### Common Usage Patterns
            |- **Search User Guide**: `gradle_docs(query="kotlin dsl configuration")`
            |- **Read Guide Section**: `gradle_docs(path="working_with_files.html")`
            |- **Check Version Changes**: `gradle_docs(releaseNotes=true, version="8.5")`
            |- **List Available Pages**: `gradle_docs()`
            |
            |Note: `releaseNotes` takes precedence over `path`, which takes precedence over `query`.
            |For detailed documentation navigation strategies, refer to the `gradle-docs` skill.
        """.trimMargin()
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
