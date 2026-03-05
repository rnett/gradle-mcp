package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.gradle.dependencies.GradleSourceService
import dev.rnett.gradle.mcp.gradle.dependencies.SourcesService
import dev.rnett.gradle.mcp.gradle.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.gradle.dependencies.search.GlobSearch
import dev.rnett.gradle.mcp.gradle.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.gradle.dependencies.search.SymbolSearch
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.tools.GradleProjectRootInput
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.tools.ToolNames
import dev.rnett.gradle.mcp.tools.paginate
import dev.rnett.gradle.mcp.tools.paginateText
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
        @Description("Perform high-resolution symbol search (classes, methods, etc.) using authoritative regex patterns.")
        SYMBOLS,

        @Description("Perform exhaustive full-text search within files using high-performance Lucene indexing. This supports standard Lucene query syntax (wildcards, phrases, boolean operators, etc.).")
        FULL_TEXT,

        @Description("Search for files by name or path pattern using standard glob syntax (e.g., '**/AndroidManifest.xml').")
        GLOB
    }

    @Serializable
    data class ReadDependencySourcesArgs(
        @Description("The absolute path to the project root directory. Defaults to the current workspace root. Always provide this if you are working in a multi-root workspace to ensure the correct project is targeted.")
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The project path to target (e.g., ':', ':app'). If null, all project dependencies are included. This has the lowest precedence.")
        val projectPath: String? = null,
        @Description("Target a specific configuration (e.g., ':app:debugCompileClasspath'). If set, projectPath is ignored. Higher precedence than projectPath.")
        val configurationPath: String? = null,
        @Description("Target a specific source set (e.g., ':app:main'). If set, projectPath and configurationPath are ignored. Higher precedence than configurationPath.")
        val sourceSetPath: String? = null,
        @Description("If true, targets Gradle Build Tool's own authoritative source code instead of project dependencies. This has the HIGHEST precedence.")
        val gradleSource: Boolean = false,
        @Description("The specific file or directory path to read, relative to the combined source root. Use exact paths from 'search_dependency_sources'. Providing no path will list the top-level source directory.")
        val path: String? = null,
        @Description("If true, forces a re-download and re-indexing of all targeted sources. Use this only if you suspect cached data is corrupt or significantly outdated.")
        val forceDownload: Boolean = false,
        @Description("If true, retrieves a fresh dependency list from Gradle before processing. STRONGLY RECOMMENDED if your project dependencies have changed since the last source retrieval.")
        val fresh: Boolean = false,
        val pagination: PaginationInput = PaginationInput.DEFAULT_LINES
    )

    val readDependencySources by tool<ReadDependencySourcesArgs, String>(
        ToolNames.READ_DEPENDENCY_SOURCES,
        """
            |The authoritative tool for reading source files and exploring directory structures from the combined source code of all external library dependencies or Gradle's internal engine.
            |It provides surgical access to the implementation details of your project's ecosystem, cached for high-performance retrieval.
            |
            |### Authoritative Features
            |- **Deep Source Exploration**: Navigate the entire directory structure of your project's dependencies or Gradle's own source code.
            |- **Surgical File Retrieval**: Read the full content of any identified source file. Ideal for verifying library behavior or researching undocumented APIs.
            |- **Managed Lifecycle**: Sources and indices are cached per scope (project, configuration, or source set). Subsequent reads are nearly instantaneous.
            |- **Contextual Precedence**: Precisely target your search using `gradleSource`, `sourceSetPath`, `configurationPath`, or `projectPath`.
            |- **Standardized Pagination**: Directory listings and extremely large files are paginated. Use `offset` and `limit` to browse large outputs safely.
            |
            |### Common Usage Patterns
            |- **Explore App Dependencies**: `read_dependency_sources(projectPath=":app")`
            |- **Read Gradle Source**: `read_dependency_sources(path="org/gradle/api/Project.java", gradleSource=true)`
            |- **List Library Structure**: `read_dependency_sources(path="org/junit/", configurationPath=":app:testCompileClasspath")`
            |
            |To find specific classes or methods across all dependencies before reading, use the `search_dependency_sources` tool.
            |For detailed source navigation strategies, refer to the `gradle-library-sources` skill.
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
                return@tool "$refreshMessage\n\n" + paginateText(walkDirectory(targetPath, 2), args.pagination)
            } else {
                return@tool "Path is neither a file nor a directory: ${args.path}"
            }
        } else {
            "$refreshMessage\n\n" + paginateText(walkDirectory(baseDir, 2), args.pagination)
        }
    }

    @Serializable
    data class SearchDependencySourcesArgs(
        @Description("The absolute path to the project root directory. Defaults to the current workspace root. Always provide this if you are working in a multi-root workspace to ensure the correct project is targeted.")
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The project path to search (e.g., ':', ':app'). If null, all project dependencies are searched. This has the lowest precedence.")
        val projectPath: String? = null,
        @Description("Target a specific configuration (e.g., ':app:debugCompileClasspath'). If set, projectPath is ignored. Higher precedence than projectPath.")
        val configurationPath: String? = null,
        @Description("Target a specific source set (e.g., ':app:main'). If set, projectPath and configurationPath are ignored. Higher precedence than configurationPath.")
        val sourceSetPath: String? = null,
        @Description("If true, searches Gradle Build Tool's own authoritative source code instead of project dependencies. This has the HIGHEST precedence.")
        val gradleSource: Boolean = false,
        @Description("The search query. For SYMBOLS search (default), use regex for classes or methods. For FULL_TEXT, use Lucene queries (e.g., '\"exact phrase\"', 'a AND b', 'path:**/Job.kt'). For GLOB, use Java glob syntax (e.g., '**/MyClass.kt'). If the query is not a valid glob, it will fall back to a case-insensitive substring match on file paths.")
        val query: String,
        @Description("The type of search to perform. SYMBOLS (default) is ideal for class/method lookup; FULL_TEXT is best for finding specific strings; GLOB is for finding files by path using standard glob patterns (*, **, ?, etc.).")
        val searchType: SearchType = SearchType.SYMBOLS,
        @Description("If true, re-downloads and re-indexes the targeted sources. Use this only if you suspect cached data is corrupt or significantly outdated.")
        val forceDownload: Boolean = false,
        @Description("If true, retrieves a fresh dependency list from Gradle before searching. STRONGLY RECOMMENDED if your project dependencies have changed since the last search.")
        val fresh: Boolean = false,
        val pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS
    )

    val searchDependencySources by tool<SearchDependencySourcesArgs, String>(
        ToolNames.SEARCH_DEPENDENCY_SOURCES,
        """
            |The authoritative tool for searching for symbols or text within the combined source code of all external library dependencies or Gradle's internal engine.
            |It provides high-performance, indexed search capabilities that far exceed basic grep-based exploration.
            |
            |### High-Performance Features
            |- **Precision Symbol Lookup**: Use authoritative regex patterns to find classes, methods, or interfaces across your entire dependency graph.
            |- **Exhaustive Full-Text Indexing**: Perform surgical text searches using high-performance Lucene indexing. It supports standard Lucene query syntax:
            |  - **Phrases**: `"exact phrase"` matches multiple words in sequence.
            |  - **Wildcards**: `*` (zero or more characters), `?` (exactly one character).
            |  - **Boolean Operators**: `AND`, `OR`, `NOT`, `+`, `-`.
            |  - **Grouping**: `( )` for complex logical expressions.
            |  - **Fuzzy Search**: `~` for similar spellings (e.g., `test~`).
            |  - **Proximity Search**: `"word1 word2"~10` to find words within a specific distance.
            |  - **Field Search**: Search within specific fields: `path` (file path, e.g., `path:**/Job.kt`) or `contents` (file content, default field).
            |  Ideal for finding constants, strings, or specific implementation patterns across your entire dependency graph.
            |- **Managed Search Scopes**: Narrow your search to specific projects, configurations, or source sets to maintain token efficiency and reduce noise.
            |- **Flexible File Search (GLOB)**: Locate specific files by name or path pattern using standard Java glob syntax.
            |  - `*`: Matches zero or more characters within a directory level.
            |  - `**`: Matches zero or more characters across directory levels.
            |  - `?`: Matches exactly one character.
            |  - `{a,b}`: Matches any of the comma-separated strings.
            |  - Fallback: If the pattern is not a valid glob, it performs a case-insensitive substring search on file paths.
            |- **Deep Engine Access**: Search the authoritative source code of the Gradle Build Tool itself to understand core system behavior.
            |- **Standardized Pagination**: Large result sets are paginated. Use `offset` and `limit` to browse large outputs safely.
            |
            |### Common Usage Patterns
            |- **Find Class**: `search_dependency_sources(query="Assert", projectPath=":")`
            |- **Search Constants**: `search_dependency_sources(query="THREAD_POOL_SIZE", searchType="FULL_TEXT")`
            |- **Find XML File**: `search_dependency_sources(query="**/AndroidManifest.xml", searchType="GLOB")`
            |- **Find Java File**: `search_dependency_sources(query="**/*.java", searchType="GLOB")`
            |- **Find File with Substring**: `search_dependency_sources(query="LICENSE", searchType="GLOB")`
            |- **Find Gradle Interface**: `search_dependency_sources(query="interface Project", gradleSource=true)`
            |
            |Once you have identified a file path from the search results, use the `${ToolNames.READ_DEPENDENCY_SOURCES}` tool to read the full content.
            |Note: All returned paths are relative to the combined source root.
            |For detailed search strategies, refer to the `gradle-library-sources` skill.
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
            SearchType.GLOB -> GlobSearch
        }
        val results = sourcesService.search(sources, provider, args.query, args.pagination)
        val refreshMessage = formatRefreshMessage(sources.lastRefresh())
        refreshMessage + "\n\n" + formatSearchResults(results, args.query, args.pagination)
    }

    private fun formatSearchResults(results: List<SearchResult>, query: String, pagination: PaginationInput): String {
        if (results.isEmpty() && pagination.offset == 0) {
            return "No results found for '$query'."
        }

        return buildString {
            appendLine("Search results for '$query':\n")
            val paged = paginate(results, pagination, "search results", isAlreadyPaged = false) { result ->
                buildString {
                    appendLine("File: ${result.relativePath}:${result.line}")
                    if (result.score != null) {
                        appendLine("Score: ${result.score}")
                    }
                    appendLine("```")
                    appendLine(result.snippet)
                    appendLine("```")
                }.trim()
            }
            append(paged)
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
