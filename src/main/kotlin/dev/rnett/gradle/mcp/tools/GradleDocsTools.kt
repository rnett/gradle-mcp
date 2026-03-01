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
    data class GetAllDocsPagesArgs(
        @Description("The Gradle version to get documentation for (e.g. '8.5', '7.6.3'). Defaults to 'current' if version is not provided and cannot be autodetected from the project root.")
        val version: String? = null,
        @Description("The Gradle project root to detect the version from. If not provided, it will be autodetected from the current working directory or MCP roots if possible. Ignored and not needed if the version is specified.")
        val projectRoot: GradleProjectRootInput? = null
    )

    val getAllDocsPages by tool<GetAllDocsPagesArgs, String>(
        ToolNames.GET_ALL_GRADLE_DOCS_PAGES,
        "Returns a list of all Gradle User Guide documentation pages for a given version. The list includes the page title and its path. " +
                "The path is relative to the base URL: `https://docs.gradle.org/{version}/userguide/`. " +
                "For example, a path of `command_line_interface.html` corresponds to `https://docs.gradle.org/current/userguide/command_line_interface.html`. " +
                "The version will be autodetected from the Gradle project root if not specified. If autodetection fails and no version is provided, it defaults to 'current'."
    ) {
        val resolvedVersion = resolveVersion(it.version, it.projectRoot)
        val pages = gradleDocsService.getAllDocsPages(resolvedVersion)
        if (pages.isEmpty()) {
            "No documentation pages found for version ${resolvedVersion ?: "current"}."
        } else {
            val displayVersion = resolvedVersion ?: "current"
            "Available documentation pages for Gradle $displayVersion:\n\n" +
                    pages.joinToString("\n") { page -> "- ${page.title}: `${page.path}`" }
        }
    }

    @Serializable
    data class GetDocsPageArgs(
        @Description("The relative path to the documentation page (e.g. 'gradle_basics.html') from https://docs.gradle.org/{version}/userguide/.")
        val path: String,
        @Description("The Gradle version to get documentation for (e.g. '8.5', '7.6.3'). Defaults to 'current' if version is not provided and cannot be autodetected from the project root.")
        val version: String? = null,
        @Description("The Gradle project root to detect the version from. If not provided, it will be autodetected from the current working directory or MCP roots if possible. Ignored and not needed if the version is specified.")
        val projectRoot: GradleProjectRootInput? = null
    )

    val getDocsPage by tool<GetDocsPageArgs, String>(
        ToolNames.GET_GRADLE_DOCS_PAGE,
        "Returns the content of a specific Gradle User Guide documentation page as Markdown. " +
                "The path should be relative to the base URL: `https://docs.gradle.org/{version}/userguide/`. " +
                "For example, to get the page for `https://docs.gradle.org/current/userguide/command_line_interface.html`, the path should be `command_line_interface.html`. " +
                "The path must not contain `..`. " +
                "The version will be autodetected from the Gradle project root if not specified. If autodetection fails and no version is provided, it defaults to 'current'."
    ) {
        val resolvedVersion = resolveVersion(it.version, it.projectRoot)
        gradleDocsService.getDocsPageAsMarkdown(it.path, resolvedVersion)
    }

    @Serializable
    data class GetReleaseNotesArgs(
        @Description("The Gradle version to get release notes for (e.g. '8.5', '7.6.3'). Defaults to 'current' if version is not provided and cannot be autodetected from the project root.")
        val version: String? = null,
        @Description("The Gradle project root to detect the version from. If not provided, it will be autodetected from the current working directory or MCP roots if possible. Ignored and not needed if the version is specified.")
        val projectRoot: GradleProjectRootInput? = null
    )

    val getReleaseNotes by tool<GetReleaseNotesArgs, String>(
        ToolNames.GET_GRADLE_RELEASE_NOTES,
        "Returns the release notes for a specific Gradle version as Markdown. " +
                "The version will be autodetected from the Gradle project root if not specified. If autodetection fails and no version is provided, it defaults to 'current'."
    ) {
        val resolvedVersion = resolveVersion(it.version, it.projectRoot)
        gradleDocsService.getReleaseNotes(resolvedVersion)
    }

    @Serializable
    data class SearchDocsArgs(
        @Description("The search query.")
        val query: String,
        @Description("Whether the query is a regex. Defaults to false.")
        val isRegex: Boolean = false,
        @Description("The Gradle version to search documentation for (e.g. '8.5', '7.6.3'). Defaults to 'current' if version is not provided and cannot be autodetected from the project root.")
        val version: String? = null,
        @Description("The Gradle project root to detect the version from. If not provided, it will be autodetected from the current working directory or MCP roots if possible. Ignored and not needed if the version is specified.")
        val projectRoot: GradleProjectRootInput? = null
    )

    val searchDocs by tool<SearchDocsArgs, String>(
        ToolNames.SEARCH_GRADLE_DOCS,
        "Searches Gradle User Guide documentation for a given version. Returns the matching page titles, paths, and snippets. " +
                "The returned path is relative to the base URL: `https://docs.gradle.org/{version}/userguide/`. " +
                "The version will be autodetected from the Gradle project root if not specified. If autodetection fails and no version is provided, it defaults to 'current'."
    ) {
        val resolvedVersion = resolveVersion(it.version, it.projectRoot)
        val results = gradleDocsService.searchDocs(it.query, it.isRegex, resolvedVersion)
        if (results.isEmpty()) {
            "No documentation matches found for '${it.query}' in version ${resolvedVersion ?: "current"}."
        } else {
            val displayVersion = resolvedVersion ?: "current"
            "Search results for '${it.query}' in Gradle $displayVersion:\n\n" +
                    results.joinToString("\n\n") { result ->
                        "### ${result.title}\nPath: `${result.path}`\n\n```markdown\n${result.snippet}\n```"
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
