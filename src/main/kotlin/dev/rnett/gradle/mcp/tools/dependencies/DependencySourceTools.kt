package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.gradle.GradleProjectPath
import dev.rnett.gradle.mcp.gradle.dependencies.SourcesService
import dev.rnett.gradle.mcp.gradle.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.gradle.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.gradle.dependencies.search.SymbolSearch
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.tools.GradleProjectRootInput
import dev.rnett.gradle.mcp.tools.ToolNames
import dev.rnett.gradle.mcp.tools.resolveRoot
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

class DependencySourceTools(
    private val sourcesService: SourcesService
) : McpServerComponent("Project Dependency Source Tools", "Tools for searching and inspecting source code of Gradle dependencies.") {

    @Serializable
    data class SearchDependencySourcesArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The Gradle project path to scope dependencies to, e.g. ':project-a:subproject-b'. If omitted, the entire build's dependencies are used.")
        val projectPath: GradleProjectPath? = null,
        @Description("The name of a specific configuration to scope to (e.g., 'implementation', `commonMainApi`). If omitted, all configurations are included.")
        val configuration: String? = null,
        @Description("The name of a specific source set to scope to (e.g., 'main', `commonMain`). If omitted, all source sets are included.")
        val sourceSet: String? = null,
        @Description("The search query. For symbol search, it's the symbol name. For full text search, it's a regex or exact string based on provider implementation.")
        val query: String,
        @Description("The search type. Must be 'symbol' or 'full_text'. Defaults to 'symbol'.")
        val searchType: String = "symbol",
        @Description("If true, re-downloads and re-indexes the sources even if they are already cached.")
        val forceDownload: Boolean = false
    )

    val searchDependencySources by tool<SearchDependencySourcesArgs, String>(
        ToolNames.SEARCH_DEPENDENCY_SOURCES,
        "Downloads and indexes sources for the specified project/configuration/sourceSet (if not already cached), then searches them for the given query."
    ) { args ->
        val root = with(server) { args.projectRoot.resolveRoot() }
        val sources = when {
            args.configuration != null -> sourcesService.downloadConfigurationSources(root, args.configuration, forceDownload = args.forceDownload)
            args.sourceSet != null -> sourcesService.downloadSourceSetSources(root, args.sourceSet, forceDownload = args.forceDownload)
            args.projectPath != null -> sourcesService.downloadProjectSources(root, args.projectPath.path, forceDownload = args.forceDownload)
            else -> sourcesService.downloadAllSources(root, forceDownload = args.forceDownload)
        }

        val provider = when (args.searchType.lowercase()) {
            "symbol" -> SymbolSearch
            "full_text" -> FullTextSearch
            else -> throw IllegalArgumentException("Unknown search type: ${args.searchType}")
        }

        val results = sourcesService.search(sources, provider, args.query)

        formatSearchResults(results, args.query)
    }

    @Serializable
    data class ReadDependencySourcePathArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The Gradle project path to scope dependencies to, e.g. ':project-a:subproject-b'. If omitted, the entire build's dependencies are used.")
        val projectPath: GradleProjectPath? = null,
        @Description("The name of a specific configuration to scope to (e.g., 'implementation', `commonMainApi`). If omitted, all configurations are included.")
        val configuration: String? = null,
        @Description("The name of a specific source set to scope to (e.g., 'main', `commonMain`). If omitted, all source sets are included.")
        val sourceSet: String? = null,
        @Description("The relative path within the downloaded sources to read (e.g., as returned by the search tool).")
        val path: String,
        @Description("If true, re-downloads and re-indexes the sources even if they are already cached.")
        val forceDownload: Boolean = false
    )

    val readDependencySourcePath by tool<ReadDependencySourcePathArgs, String>(
        ToolNames.READ_DEPENDENCY_SOURCE_PATH,
        "Reads a file or walks a directory (up to 2 levels) in the downloaded dependency sources."
    ) { args ->
        val root = with(server) { args.projectRoot.resolveRoot() }
        val sources = when {
            args.configuration != null -> sourcesService.downloadConfigurationSources(root, args.configuration, forceDownload = args.forceDownload)
            args.sourceSet != null -> sourcesService.downloadSourceSetSources(root, args.sourceSet, forceDownload = args.forceDownload)
            args.projectPath != null -> sourcesService.downloadProjectSources(root, args.projectPath.path, forceDownload = args.forceDownload)
            else -> sourcesService.downloadAllSources(root, forceDownload = args.forceDownload)
        }

        val baseDir = sources.sources.normalize()
        val targetPath = baseDir.resolve(args.path).normalize()

        if (!targetPath.startsWith(baseDir)) {
            return@tool "Invalid path: ${args.path}"
        }

        if (!targetPath.exists()) {
            return@tool "Path not found: ${args.path}"
        }

        if (targetPath.isRegularFile()) {
            return@tool "File: ${args.path}\n```\n${targetPath.readText()}\n```"
        } else if (targetPath.isDirectory()) {
            return@tool walkDirectory(targetPath, 2)
        } else {
            return@tool "Path is neither a file nor a directory: ${args.path}"
        }
    }

    private fun formatSearchResults(results: List<SearchResult>, query: String): String {
        if (results.isEmpty()) {
            return "No results found for '$query'."
        }

        return buildString {
            appendLine("Found ${results.size} result(s) for '$query':\n")
            results.forEach { result ->
                appendLine("File: ${result.relativePath}:${result.line}")
                if (result.score != null) {
                    appendLine("Score: ${result.score}")
                }
                appendLine("```")
                appendLine(result.snippet)
                appendLine("```")
                appendLine()
            }
        }.trim()
    }

    private fun walkDirectory(dir: Path, maxDepth: Int = 2): String {
        return buildString {
            appendLine(dir.name + "/")
            walkDirectoryImpl(dir, dir, "", 0, maxDepth)
        }
    }

    private fun java.lang.StringBuilder.walkDirectoryImpl(root: Path, current: Path, prefix: String, depth: Int, maxDepth: Int) {
        if (depth >= maxDepth) return
        val children = Files.list(current).use { it.toList() }.sortedBy { it.name }
        for ((index, child) in children.withIndex()) {
            val isLast = index == children.lastIndex
            val marker = if (isLast) "└── " else "├── "
            appendLine(prefix + marker + child.name + if (child.isDirectory()) "/" else "")
            if (child.isDirectory()) {
                val childPrefix = prefix + if (isLast) "    " else "│   "
                walkDirectoryImpl(root, child, childPrefix, depth + 1, maxDepth)
            }
        }
    }
}
