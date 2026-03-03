package dev.rnett.gradle.mcp.tools.dependencies

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
    enum class SearchType {
        @Description("Search for symbols (classes, methods, etc.) using regex.")
        SYMBOLS,

        @Description("Search for text within files using Lucene.")
        FULL_TEXT
    }

    @Serializable
    data class ReadDependencySourcesArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The project path to get dependencies from (e.g. ':', ':app'). If null, all projects are used.")
        val projectPath: String? = null,
        @Description("The configuration path to get dependencies from (e.g. ':app:debugCompileClasspath'). If set, projectPath is ignored.")
        val configurationPath: String? = null,
        @Description("The source set path to get dependencies from (e.g. ':app:main'). If set, projectPath and configurationPath are ignored.")
        val sourceSetPath: String? = null,
        @Description("Specific file path within the source to read. This path is relative to the root of the combined sources for the given scope.")
        val path: String? = null,
        @Description("If true, re-downloads and re-indexes the sources even if they are already cached.")
        val forceDownload: Boolean = false
    )

    val readDependencySources by tool<ReadDependencySourcesArgs, String>(
        ToolNames.READ_DEPENDENCY_SOURCES,
        """
            |Read specific source files or list directories from the combined source code of all external library dependencies within a given scope (project, configuration, or source set).
            |
            |Use this tool to explore the implementation of a library once you have identified the file path.
            |To find specific classes or methods across all dependencies, use the `search_dependency_sources` tool.
        """.trimMargin()
    ) { args ->
        val root = with(server) { args.projectRoot.resolveRoot() }
        val sources = when {
            args.sourceSetPath != null -> sourcesService.downloadSourceSetSources(root, args.sourceSetPath, forceDownload = args.forceDownload)
            args.configurationPath != null -> sourcesService.downloadConfigurationSources(root, args.configurationPath, forceDownload = args.forceDownload)
            args.projectPath != null -> sourcesService.downloadProjectSources(root, args.projectPath, forceDownload = args.forceDownload)
            else -> sourcesService.downloadAllSources(root, forceDownload = args.forceDownload)
        }

        val baseDir = sources.sources.normalize()

        if (args.path != null) {
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
        } else {
            walkDirectory(baseDir, 1)
        }
    }

    @Serializable
    data class SearchDependencySourcesArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The project path to get dependencies from (e.g. ':', ':app'). If null, all projects are used.")
        val projectPath: String? = null,
        @Description("The configuration path to get dependencies from (e.g. ':app:debugCompileClasspath'). If set, projectPath is ignored.")
        val configurationPath: String? = null,
        @Description("The source set path to get dependencies from (e.g. ':app:main'). If set, projectPath and configurationPath are ignored.")
        val sourceSetPath: String? = null,
        @Description("Search query for symbols or file names.")
        val query: String,
        @Description("The type of search to perform. Defaults to SYMBOLS.")
        val searchType: SearchType = SearchType.SYMBOLS,
        @Description("If true, re-downloads and re-indexes the sources even if they are already cached.")
        val forceDownload: Boolean = false
    )

    val searchDependencySources by tool<SearchDependencySourcesArgs, String>(
        ToolNames.SEARCH_DEPENDENCY_SOURCES,
        """
            |Search for symbols or text within the combined source code of all external library dependencies within a given scope (project, configuration, or source set).
            |
            |Use this tool to find specific classes, methods, or text in library source code.
            |Once you have found the file path, you can read the file using the `read_dependency_sources` tool.
        """.trimMargin()
    ) { args ->
        val root = with(server) { args.projectRoot.resolveRoot() }
        val sources = when {
            args.sourceSetPath != null -> sourcesService.downloadSourceSetSources(root, args.sourceSetPath, forceDownload = args.forceDownload)
            args.configurationPath != null -> sourcesService.downloadConfigurationSources(root, args.configurationPath, forceDownload = args.forceDownload)
            args.projectPath != null -> sourcesService.downloadProjectSources(root, args.projectPath, forceDownload = args.forceDownload)
            else -> sourcesService.downloadAllSources(root, forceDownload = args.forceDownload)
        }

        val provider = when (args.searchType) {
            SearchType.SYMBOLS -> SymbolSearch
            SearchType.FULL_TEXT -> FullTextSearch
        }
        val results = sourcesService.search(sources, provider, args.query)
        formatSearchResults(results, args.query)
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
