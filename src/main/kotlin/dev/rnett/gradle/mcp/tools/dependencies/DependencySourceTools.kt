package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.gradle.dependencies.GradleSourceService
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.milliseconds

class DependencySourceTools(
    private val sourcesService: SourcesService,
    private val gradleSourceService: GradleSourceService
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
        @Description("The project path to get dependencies from (e.g. ':', ':app'). If null, all projects are used. This has the lowest precedence.")
        val projectPath: String? = null,
        @Description("The configuration path to get dependencies from (e.g. ':app:debugCompileClasspath'). If set, projectPath is ignored. This has higher precedence than projectPath.")
        val configurationPath: String? = null,
        @Description("The source set path to get dependencies from (e.g. ':app:main'). If set, projectPath and configurationPath are ignored. This has higher precedence than configurationPath.")
        val sourceSetPath: String? = null,
        @Description("If true, searches/reads Gradle Build Tool's own source code instead of the project's dependencies. If set, projectPath, sourceSetPath, and configurationPath are ignored. This has the highest precedence.")
        val gradleSource: Boolean = false,
        @Description("Specific file path within the source to read. This path is relative to the root of the combined sources for the given scope, i.e. it should start with the group/filename that is present in the search result paths.")
        val path: String? = null,
        @Description("If true, re-downloads and re-indexes the sources even if they are already cached. This is only necessary for snapshots or things that change..")
        val forceDownload: Boolean = false,
        @Description("If true, a fresh list of dependencies and their sources is retrieved from Gradle. If false (default), the cached list for the scope is used if it exists. It is strongly recommended to set this to true if the project dependencies have changed since the last refresh. You can check the currently indexed libraries by reading the root directory of the sources.")
        val fresh: Boolean = false
    )

    val readDependencySources by tool<ReadDependencySourcesArgs, String>(
        ToolNames.READ_DEPENDENCY_SOURCES,
        """
            |Read specific source files or list directories from the combined source code of all external library dependencies or Gradle's internal sources within a given scope.
            |
            |Sources are downloaded and indexed per scope (project, configuration, or source set) and cached for reuse across tool calls.
            |By default, if cached sources exist for the scope, they are used without refreshing the dependency list from Gradle (fresh = false). 
            |It is strongly recommended to set **fresh = true** if the project dependencies have changed since the last refresh. 
            |You can check the currently indexed libraries by reading the root directory of the sources (providing no path).
            |
            |**projectRoot** should be the file system path of the Gradle project's root directory (containing gradlew script and settings.gradle). Providing this ensures the tool executes in the correct project context and avoids ambiguities in multi-root or environment-dependent workspaces. If omitted, the tool will attempt to auto-detect the root from the current MCP roots or the GRADLE_MCP_PROJECT_ROOT environment variable. **It MUST be an absolute path.**
            |
            |Use this tool to explore the implementation of a library or Gradle itself, and to read a source file once you have identified the file path.
            |If the provided path is a directory, its contents will be listed.
            |To find specific classes or methods across all dependencies, use the `search_dependency_sources` tool.
        """.trimMargin()
    ) { args ->
        val root = with(server) { args.projectRoot.resolveRoot() }
        val sources = when {
            args.gradleSource -> gradleSourceService.getGradleSources(root, forceDownload = args.forceDownload)
            args.sourceSetPath != null -> sourcesService.downloadSourceSetSources(root, args.sourceSetPath, forceDownload = args.forceDownload, fresh = args.fresh)
            args.configurationPath != null -> sourcesService.downloadConfigurationSources(root, args.configurationPath, forceDownload = args.forceDownload, fresh = args.fresh)
            args.projectPath != null -> sourcesService.downloadProjectSources(root, args.projectPath, forceDownload = args.forceDownload, fresh = args.fresh)
            else -> sourcesService.downloadAllSources(root, forceDownload = args.forceDownload, fresh = args.fresh)
        }

        val baseDir = sources.sources.normalize()
        val refreshMessage = formatRefreshMessage(sources.lastRefresh())

        if (args.path != null) {
            val targetPath = baseDir.resolve(args.path).normalize()

            if (!targetPath.startsWith(baseDir)) {
                return@tool "Invalid path: ${args.path}"
            }

            if (!targetPath.exists()) {
                return@tool "Path not found: ${args.path}"
            }

            if (targetPath.isRegularFile()) {
                return@tool "$refreshMessage\n\nFile: ${args.path}\n```\n${targetPath.readText()}\n```"
            } else if (targetPath.isDirectory()) {
                return@tool "$refreshMessage\n\n" + walkDirectory(targetPath, 2)
            } else {
                return@tool "Path is neither a file nor a directory: ${args.path}"
            }
        } else {
            "$refreshMessage\n\n" + walkDirectory(baseDir, 2)
        }
    }

    @Serializable
    data class SearchDependencySourcesArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The project path to get dependencies from (e.g. ':', ':app'). If null, all projects are used. This has the lowest precedence.")
        val projectPath: String? = null,
        @Description("The configuration path to get dependencies from (e.g. ':app:debugCompileClasspath'). If set, projectPath is ignored. This has higher precedence than projectPath.")
        val configurationPath: String? = null,
        @Description("The source set path to get dependencies from (e.g. ':app:main'). If set, projectPath and configurationPath are ignored. This has higher precedence than configurationPath.")
        val sourceSetPath: String? = null,
        @Description("If true, searches/reads Gradle's internal source code instead of external dependencies. If set, projectPath, sourceSetPath, and configurationPath are ignored. This has the highest precedence.")
        val gradleSource: Boolean = false,
        @Description("Search query for symbols or file names.")
        val query: String,
        @Description("The type of search to perform. Defaults to SYMBOLS.")
        val searchType: SearchType = SearchType.SYMBOLS,
        @Description("If true, re-downloads and re-indexes the sources even if they are already downloaded. This is only necessary for snapshots or things that change.")
        val forceDownload: Boolean = false,
        @Description("If true, a fresh list of dependencies and their sources is retrieved from Gradle. If false (default), the cached list for the scope is used if it exists. It is strongly recommended to set this to true if the project dependencies have changed since the last refresh. You can check the currently indexed libraries by reading the root directory of the sources.")
        val fresh: Boolean = false
    )

    val searchDependencySources by tool<SearchDependencySourcesArgs, String>(
        ToolNames.SEARCH_DEPENDENCY_SOURCES,
        """
            |Search for symbols or text within the combined source code of all external library dependencies or Gradle's internal sources within a given scope.
            |
            |Sources are downloaded and indexed per scope (project, configuration, or source set) and cached for reuse. Subsequent searches in the same scope will be much faster.
            |By default, if cached sources exist for the scope, they are used without refreshing the dependency list from Gradle (fresh = false). 
            |It is strongly recommended to set **fresh = true** if the project dependencies have changed since the last refresh. 
            |You can check the currently indexed libraries by reading the root directory of the sources with **read_dependency_sources**.
            |
            |**projectRoot** should be the file system path of the Gradle project's root directory (containing gradlew script and settings.gradle). Providing this ensures the tool executes in the correct project context and avoids ambiguities in multi-root or environment-dependent workspaces. If omitted, the tool will attempt to auto-detect the root from the current MCP roots or the GRADLE_MCP_PROJECT_ROOT environment variable. **It MUST be an absolute path.**
            |
            |Use this tool to find specific classes, methods, or text in library source code or Gradle itself.
            |Once you have found the file path, you can read the file using the `${ToolNames.READ_DEPENDENCY_SOURCES}` tool.
            |Note that all paths are relative to the combined source root, and are exactly what you should use with ${ToolNames.READ_DEPENDENCY_SOURCES}.
            |When searching for symbols, the results may have some false-positives - look at the included snippets.
        """.trimMargin()
    ) { args ->
        val root = with(server) { args.projectRoot.resolveRoot() }
        val sources = when {
            args.gradleSource -> gradleSourceService.getGradleSources(root, forceDownload = args.forceDownload)
            args.sourceSetPath != null -> sourcesService.downloadSourceSetSources(root, args.sourceSetPath, forceDownload = args.forceDownload, fresh = args.fresh)
            args.configurationPath != null -> sourcesService.downloadConfigurationSources(root, args.configurationPath, forceDownload = args.forceDownload, fresh = args.fresh)
            args.projectPath != null -> sourcesService.downloadProjectSources(root, args.projectPath, forceDownload = args.forceDownload, fresh = args.fresh)
            else -> sourcesService.downloadAllSources(root, forceDownload = args.forceDownload, fresh = args.fresh)
        }

        val provider = when (args.searchType) {
            SearchType.SYMBOLS -> SymbolSearch
            SearchType.FULL_TEXT -> FullTextSearch
        }
        val results = sourcesService.search(sources, provider, args.query)
        val refreshMessage = formatRefreshMessage(sources.lastRefresh())
        refreshMessage + "\n\n" + formatSearchResults(results, args.query)
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

    private fun formatRefreshMessage(lastRefresh: java.time.Instant?): String {
        if (lastRefresh == null) return "Sources have not been refreshed yet."
        val now = java.time.Instant.now()
        val duration = (now.toEpochMilli() - lastRefresh.toEpochMilli()).milliseconds
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        val timestamp = formatter.format(lastRefresh)

        val ago = when {
            duration.inWholeDays > 0 -> "${duration.inWholeDays} day(s) ago"
            duration.inWholeHours > 0 -> "${duration.inWholeHours} hour(s) ago"
            duration.inWholeMinutes > 0 -> "${duration.inWholeMinutes} minute(s) ago"
            else -> "just now"
        }

        return "Sources last refreshed at $timestamp ($ago)"
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
