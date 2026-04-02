package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.dependencies.GradleSourceService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.DeclarationSearch
import dev.rnett.gradle.mcp.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.dependencies.search.GlobSearch
import dev.rnett.gradle.mcp.dependencies.search.PackageContents
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.SearchResponse
import dev.rnett.gradle.mcp.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.dependencies.search.SearchResult.Companion.DEFAULT_SNIPPET_RANGE
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.tools.GradleProjectRootInput
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.tools.ToolNames
import dev.rnett.gradle.mcp.tools.paginate
import dev.rnett.gradle.mcp.tools.paginateText
import dev.rnett.gradle.mcp.tools.resolveRoot
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.time.Clock
import kotlin.time.Instant

class DependencySourceTools(
    private val sourcesService: SourcesService,
    private val gradleSourceService: GradleSourceService,
    private val indexService: dev.rnett.gradle.mcp.dependencies.SourceIndexService
) : McpServerComponent("Project Dependency Source Tools", "Tools for searching and inspecting source code of Gradle dependencies.") {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DependencySourceTools::class.java)
    }

    @Serializable
    enum class SearchType {
        @Description("Finds class, method, or interface declarations. Case-sensitive. Supports 'name:' and 'fqn:' fields (see tool description).")
        DECLARATION,

        @Description("Exhaustive full-text search. Case-insensitive Lucene query. Escape special chars like ':' or '='.")
        FULL_TEXT,

        @Description("Locates files by name/extension (e.g., '**/AndroidManifest.xml'). Case-insensitive Java glob syntax.")
        GLOB
    }

    @Serializable
    data class ReadDependencySourcesArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("Targeting a specific project path (e.g., ':app'). If null, all dependencies are included.")
        val projectPath: String? = null,
        @Description("Authoritatively targeting a configuration (e.g., ':app:debugCompileClasspath'). Higher precedence.")
        val configurationPath: String? = null,
        @Description("Authoritatively targeting a source set (e.g., ':app:main'). Highest project precedence.")
        val sourceSetPath: String? = null,
        @Description("Single dependency filter by GAV (e.g., 'group:name'). Excludes transitive dependencies.")
        val dependency: String? = null,
        @Description("Targets Gradle's own source code; HIGHEST overall precedence.")
        val gradleSource: Boolean = false,
        @Description("File, dir, or package path. Requires group/artifact prefix unless 'dependency' is set.")
        val path: String? = null,
        @Description("Force re-download and re-indexing. EXPENSIVE — only use when sources are corrupt or missing, not for version changes.")
        val forceDownload: Boolean = false,
        @Description("Retrieve a fresh dependency list from Gradle. Use this (not forceDownload) when dependencies change.")
        val fresh: Boolean = false,
        val pagination: PaginationInput = PaginationInput.DEFAULT_LINES
    )

    val readDependencySources by tool<ReadDependencySourcesArgs, String>(
        ToolNames.READ_DEPENDENCY_SOURCES,
        """
            |Reads source files and explores directory structures of external library dependencies, plugins, or Gradle's internal engine; use instead of shell tools which cannot locate remote dependency sources.
            |Buildscript (plugin) dependencies are excluded by default to reduce noise. To search plugins, use `sourceSetPath: ":buildscript"` (root project) or `sourceSetPath: ":app:buildscript"` (subproject).
            |Supports dot-separated package paths via the symbol index. Use `${ToolNames.SEARCH_DEPENDENCY_SOURCES}` to find paths first.
            |`path` without `dependency`: must include group/artifact prefix. With `dependency`: relative to library root.
            |Sources are CAS-cached (immutable). Use `fresh=true` for dependency changes; `forceDownload=true` only to recover corrupt/missing files.
            |ALWAYS scope with `dependency`, `projectPath`, `configurationPath`, or `sourceSetPath` — unscoped access indexes ALL dependencies and is VERY EXPENSIVE on large projects.
            |Returns the absolute path of the sources root. Dependency directories are symlinked; pass `--follow` to `rg` (e.g., `rg --follow <pattern> <path>`).
            |
            |### Examples
            |- Browse all deps: `{}`
            |- Browse single dep: `{ dependency: "org.jetbrains.kotlin:kotlin-stdlib" }`
            |- Read file: `{ dependency: "org.jetbrains.kotlin:kotlin-stdlib", path: "kotlin/collections/List.kt" }`
            |- Read package: `{ dependency: "org.jetbrains.kotlin:kotlin-stdlib", path: "kotlin.collections" }`
            |- Plugins: `{ sourceSetPath: ":buildscript" }`
            |- Gradle internals: `{ gradleSource: true }`
        """.trimMargin()
    ) { args ->
        val root = with(server) { args.projectRoot.resolveRoot() }
        val sources = with(progressReporter) {
            resolveSources(
                root,
                args.gradleSource,
                args.sourceSetPath,
                args.configurationPath,
                args.projectPath,
                args.dependency,
                args.forceDownload,
                args.fresh || args.forceDownload,
                DeclarationSearch
            )
        }

        val baseDir = sources.sources.normalize()
        val sourcesHeader = formatSourcesHeader(sources)

        if (args.path != null) {
            val targetPath = baseDir.resolve(args.path).normalize()

            if (!targetPath.startsWith(baseDir)) {
                return@tool "$sourcesHeader\n\nInvalid path: ${args.path}"
            }

            if (!targetPath.exists()) {
                val packageContents = try {
                    indexService.listPackageContents(sources, args.path)
                } catch (e: Exception) {
                    return@tool "$sourcesHeader\n\nPath not found: ${args.path} (Error exploring package: ${e.message})"
                }

                if (packageContents != null) {
                    return@tool "$sourcesHeader\n\nPackage: ${args.path}\n\n" + paginateText(formatPackageContents(packageContents), args.pagination)
                }
                return@tool "$sourcesHeader\n\nPath not found: ${args.path}"
            }

            if (targetPath.isRegularFile()) {
                return@tool "$sourcesHeader\n\nFile: ${args.path}\n```\n${targetPath.readText()}\n```"
            } else if (targetPath.isDirectory()) {
                return@tool "$sourcesHeader\n\n" + paginateText(walkDirectory(targetPath, 2), args.pagination)
            } else {
                return@tool "$sourcesHeader\n\nPath is neither a file nor a directory: ${args.path}"
            }
        } else {
            "$sourcesHeader\n\n" + paginateText(walkDirectory(baseDir, 2), args.pagination)
        }

    }

    @Serializable
    data class SearchDependencySourcesArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("Targeting a specific project path (e.g., ':app'). If null, all dependencies are searched.")
        val projectPath: String? = null,
        @Description("Authoritatively targeting a configuration (e.g., ':app:debugCompileClasspath'). Higher precedence.")
        val configurationPath: String? = null,
        @Description("Authoritatively targeting a source set (e.g., ':app:main'). Highest project precedence.")
        val sourceSetPath: String? = null,
        @Description("Single dependency filter by GAV (e.g., 'group:name'). Excludes transitive dependencies.")
        val dependency: String? = null,
        @Description("Search Gradle Build Tool's own source code.")
        val gradleSource: Boolean = false,
        @Description("Search query: name/FQN/glob/regex (DECLARATION), Lucene query (FULL_TEXT), or glob (GLOB, e.g., '**/Job.kt').")
        val query: String,
        @Description("Search mode: FULL_TEXT (default), DECLARATION (symbol names), or GLOB (file paths).")
        val searchType: SearchType = SearchType.FULL_TEXT,
        @Description("Force re-download and re-indexing. EXPENSIVE — only use when sources are corrupt or missing, not for version changes.")
        val forceDownload: Boolean = false,
        @Description("Retrieve a fresh dependency list from Gradle. Use this (not forceDownload) when dependencies change.")
        val fresh: Boolean = false,
        val pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS
    )

    val searchDependencySources by tool<SearchDependencySourcesArgs, String>(
        ToolNames.SEARCH_DEPENDENCY_SOURCES,
        """
            |Searches for symbols or text across the source code of ALL external library dependencies, plugins, or Gradle's internal engine; use instead of shell grep which cannot find remote dependency sources.
            |Buildscript (plugin) dependencies are excluded by default to reduce noise. To search plugins, use `sourceSetPath: ":buildscript"` (root project) or `sourceSetPath: ":app:buildscript"` (subproject).
            |Sources are CAS-cached (immutable). Use `fresh=true` for dependency changes; `forceDownload=true` only to recover corrupt/missing files.
            |ALWAYS scope with `dependency`, `projectPath`, `configurationPath`, or `sourceSetPath` — unscoped search indexes ALL dependencies and is VERY EXPENSIVE on large projects.
            |Returns the absolute path of the sources root. Dependency directories are symlinked; pass `--follow` to `rg` (e.g., `rg --follow <pattern> <path>`).
            |
            |### Search Modes
            |- `DECLARATION`: Finds class, method, or interface definitions. All symbol searches are **case-sensitive**.
            |  - **Fields**: Matches against `name` (simple name, e.g., `MyClass`) and `fqn` (fully qualified name, e.g., `com.example.MyClass`).
            |  - **Unqualified Queries**: A query without a field prefix (e.g., `query: "MyClass"`) searches BOTH `name` and `fqn` fields.
            |  - **Prefix Syntax**: Use `name:X` for simple names or `fqn:x.y.Z` for precision. Supports Lucene wildcards (`*`, `?`).
            |  - **FQN Matching**: `fqn` is NOT tokenized (it matches the full string literal), so use `fqn:*.MyClass` for partial matches.
            |  - **Regex**: Wrap query in `/` for a full regular expression on the `fqn` field (e.g., `query: "/.*\.internal\..*/"`).
            |- `FULL_TEXT` (default): Exhaustive text search using a Lucene query. **Case-insensitive**. Escape special characters like `:`, `=`, `+`.
            |- `GLOB`: Locates files by name or extension using Java glob syntax. **Case-insensitive** (e.g., `query: "**/AndroidManifest.xml"`).
            |
            |### Examples
            |- All deps: `{ query: "CoroutineScope", searchType: "DECLARATION" }`
            |- Full-text: `{ query: "TIMEOUT_MS" }`
            |- Single dep: `{ dependency: "org.jetbrains.kotlinx:kotlinx-coroutines-core", query: "launch", searchType: "DECLARATION" }`
            |- Gradle internals: `{ gradleSource: true, query: "DefaultProject", searchType: "DECLARATION" }`
            |- Plugins: `{ sourceSetPath: ":buildscript", query: "MyPlugin", searchType: "DECLARATION" }`
            |- Files: `{ query: "**/plugin.properties", searchType: "GLOB" }`
            |
            |### Result Grouping
            |Results are grouped by proximity: matches within the snippet context range ($DEFAULT_SNIPPET_RANGE) are combined into a single result with a multi-line snippet showing context. Matches that are far apart in a file produce separate results.
            |
            |Once found, read content with `${ToolNames.READ_DEPENDENCY_SOURCES}`.
        """.trimMargin()
    ) { args ->
        val provider = when (args.searchType) {
            SearchType.DECLARATION -> DeclarationSearch
            SearchType.FULL_TEXT -> FullTextSearch
            SearchType.GLOB -> GlobSearch
        }

        val root = with(server) { args.projectRoot.resolveRoot() }
        val sources = with(progressReporter) {
            resolveSources(
                root,
                args.gradleSource,
                args.sourceSetPath,
                args.configurationPath,
                args.projectPath,
                args.dependency,
                args.forceDownload,
                args.fresh || args.forceDownload,
                provider
            )
        }

        val response = indexService.search(sources, provider, args.query, args.pagination)
        val sourcesHeader = formatSourcesHeader(sources)
        if (response.error != null) {
            isError = true
            return@tool "$sourcesHeader\n\n${response.error}"
        }
        "$sourcesHeader\n\n${formatSearchResults(response, args.query, args.pagination)}"
    }

    private fun formatSearchResults(response: SearchResponse<SearchResult>, query: String, pagination: PaginationInput): String {
        val results = response.results
        if (results.isEmpty() && pagination.offset == 0) {
            return "No results found for '$query'."
        }

        return buildString {
            if (response.interpretedQuery != null) {
                appendLine("Interpreted query: `${response.interpretedQuery}`")
            }
            appendLine("Search results for '$query':\n")
            val paged = paginate(results, pagination, "search results", isAlreadyPaged = true, hasMore = response.hasMore) { result ->
                buildString {
                    val scoreStr = if (result.score != null) " (score: ${"%.2f".format(java.util.Locale.US, result.score)})" else ""
                    appendLine("### File: ${result.relativePath}:${result.line}$scoreStr")
                    appendLine("```")
                    appendLine(result.snippet.trim())
                    appendLine("```")
                }
            }
            append(paged)
        }.trim()
    }

    private fun formatPackageContents(contents: PackageContents): String {
        return buildString {
            if (contents.subPackages.isNotEmpty()) {
                appendLine("Sub-packages:")
                contents.subPackages.forEach { appendLine("- $it/") }
                appendLine()
            }
            if (contents.symbols.isNotEmpty()) {
                appendLine("Symbols:")
                contents.symbols.forEach { appendLine("- $it") }
            }
        }.trim()
    }

    private fun walkDirectory(dir: Path, maxDepth: Int = 2): String {
        return buildString {
            appendLine(dir.name + "/")
            walkDirectoryImpl(dir, dir, "", 0, maxDepth)
        }
    }

    private fun formatSourcesHeader(sources: SourcesDir): String =
        "Sources root: ${sources.sources.normalize()}\n${formatRefreshMessage(sources.lastRefresh())}"

    private fun formatRefreshMessage(lastRefresh: Instant?): String {
        if (lastRefresh == null) return "Sources have not been refreshed yet."
        val now = Clock.System.now()
        val duration = now - lastRefresh
        val timestamp = lastRefresh.toString().substringBefore('.').replace('T', ' ') + " UTC"

        val ago = when {
            duration.inWholeDays > 0 -> "${duration.inWholeDays} ${if (duration.inWholeDays == 1L) "day" else "days"} ago"
            duration.inWholeHours > 0 -> "${duration.inWholeHours} ${if (duration.inWholeHours == 1L) "hour" else "hours"} ago"
            duration.inWholeMinutes > 0 -> "${duration.inWholeMinutes} ${if (duration.inWholeMinutes == 1L) "minute" else "minutes"} ago"
            else -> "just now"
        }

        return "Sources last refreshed at $timestamp ($ago)"
    }

    private fun StringBuilder.walkDirectoryImpl(root: Path, current: Path, prefix: String, depth: Int, maxDepth: Int) {
        if (depth >= maxDepth) return
        val children = current.listDirectoryEntries().sortedBy { it.name }
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

    context(progress: dev.rnett.gradle.mcp.ProgressReporter)
    private suspend fun resolveSources(
        root: dev.rnett.gradle.mcp.gradle.GradleProjectRoot,
        gradleSource: Boolean,
        sourceSetPath: String?,
        configurationPath: String?,
        projectPath: String?,
        dependency: String?,
        forceDownload: Boolean,
        fresh: Boolean,
        providerToIndex: SearchProvider? = null
    ): SourcesDir {
        return when {
            gradleSource -> gradleSourceService.getGradleSources(root, forceDownload = forceDownload, fresh = fresh, providerToIndex = providerToIndex)
            sourceSetPath != null -> sourcesService.resolveAndProcessSourceSetSources(root, sourceSetPath, dependency = dependency, forceDownload = forceDownload, fresh = fresh, providerToIndex = providerToIndex)
            configurationPath != null -> sourcesService.resolveAndProcessConfigurationSources(root, configurationPath, dependency = dependency, forceDownload = forceDownload, fresh = fresh, providerToIndex = providerToIndex)
            projectPath != null -> sourcesService.resolveAndProcessProjectSources(root, projectPath, dependency = dependency, forceDownload = forceDownload, fresh = fresh, providerToIndex = providerToIndex)
            else -> sourcesService.resolveAndProcessAllSources(root, dependency = dependency, forceDownload = forceDownload, fresh = fresh, providerToIndex = providerToIndex)
        }
    }
}
