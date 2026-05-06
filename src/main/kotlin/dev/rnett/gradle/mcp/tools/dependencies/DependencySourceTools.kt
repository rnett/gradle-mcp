package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.dependencies.GradleSourceService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.normalizeDependencyFilter
import dev.rnett.gradle.mcp.dependencies.model.SessionViewSourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.DeclarationSearch
import dev.rnett.gradle.mcp.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.dependencies.search.GlobSearch
import dev.rnett.gradle.mcp.dependencies.search.NestedPackageContents
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
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Instant

class DependencySourceTools(
    private val sourcesService: SourcesService,
    private val gradleSourceService: GradleSourceService,
    private val indexService: dev.rnett.gradle.mcp.dependencies.SourceIndexService,
    private val searchProviders: List<SearchProvider>
) : McpServerComponent("Project Dependency Source Tools", "Tools for searching and inspecting source code of Gradle dependencies.") {
    @Serializable
    enum class SearchType(val providerClass: KClass<out SearchProvider>) {
        @Description("Finds class, method, or interface declarations. Case-sensitive. Supports 'name:' and 'fqn:' fields (see tool description).")
        DECLARATION(DeclarationSearch::class),

        @Description("Exhaustive full-text search. Case-insensitive Lucene query. Escape special chars like ':' or '='.")
        FULL_TEXT(FullTextSearch::class),

        @Description("Locates files by name/extension (e.g., '**/AndroidManifest.xml'). Case-insensitive Java glob syntax.")
        GLOB(GlobSearch::class)
    }

    @Serializable
    data class ReadDependencySourcesArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("Targeting a specific project path (e.g., ':app').")
        val projectPath: String? = null,
        @Description("Authoritatively targeting a configuration (e.g., ':app:debugCompileClasspath'). Higher precedence.")
        val configurationPath: String? = null,
        @Description("Authoritatively targeting a source set (e.g., ':app:main'). Highest project precedence.")
        val sourceSetPath: String? = null,
        @Description("Full-string regex over group:name:version[:variant], or `jdk`; blank ignored.")
        val dependency: String? = null,
        @Description("Restricts the tool to Gradle Build Tool source code only; HIGHEST overall precedence.")
        val gradleOwnSource: Boolean = false,
        @Description("File, dir, or package path. Strongly recommended to use the `{group}/{artifact}/...` prefix (e.g. 'org.jetbrains.kotlin/kotlin-stdlib/kotlin/collections/List.kt').")
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
            |Reads dependency, plugin, Gradle, or JDK source trees; use instead of shell tools, which cannot locate remote dependency sources.
            |JDK sources appear under `jdk/sources/...` for JVM scopes when local `src.zip` exists; use `dependency: "jdk"` for JDK-only reads.
            |Buildscript (plugin) dependencies are excluded by default to reduce noise. To search plugins, use `sourceSetPath: ":buildscript"` (root project) or `sourceSetPath: ":app:buildscript"` (subproject).
            |Supports dot-separated package paths via the symbol index. Use `${ToolNames.SEARCH_DEPENDENCY_SOURCES}` to find paths first.
            |Strongly recommended: Use the `{group}/{artifact}/...` syntax for `path`. The `dependency` parameter narrows the source view with a full-string Kotlin regex over `group:name:version[:variant]`; unresolved deps use `group:name`, and blank strings are ignored.
            |Sources are CAS-cached (immutable). Filtered calls use distinct session-view cache entries, but the dependency regex never changes CAS identity. Use `fresh=true` for dependency changes; `forceDownload=true` only to recover corrupt/missing files.
            |ALWAYS scope with a project, configuration, or source set (or use `gradleOwnSource: true`) — unscoped access is no longer supported.
            |Returns the absolute path of the sources root. 
            |**NOTE:** Dependency directories are junctions (Windows) or symlinks; standard CLI tools like `rg` or `fd` will NOT follow them by default. ALWAYS pass `--follow` or equivalent (e.g., `rg --follow <pattern> <path>`).
            |
            |### Examples
            |- Browse project deps: `{ projectPath: ":" }`
            |- Browse single dep: `{ projectPath: ":", dependency: "^org\\.jetbrains\\.kotlinx:kotlinx-coroutines-core(:.*)?${'$'}" }`
            |- Read file: `{ projectPath: ":", path: "org.jetbrains.kotlin/kotlin-stdlib/kotlin/collections/List.kt" }`
            |- Read JDK source from a JVM source set: `{ sourceSetPath: ":app:main", dependency: "jdk", path: "jdk/sources/java.base/java/lang/String.java" }`
            |- Read package: `{ projectPath: ":", path: "org.jetbrains.kotlin/kotlin-stdlib/kotlin.collections" }`
            |- Plugins: `{ sourceSetPath: ":buildscript" }`
            |- Gradle Build Tool source: `{ gradleOwnSource: true }`
        """.trimMargin()
    ) { args ->
        val root = with(server) { args.projectRoot.resolveRoot() }
        val dependencyFilter = normalizeDependencyFilter(args.dependency)
        val sources = with(progressReporter) {
            resolveSources(
                root,
                args.gradleOwnSource,
                args.sourceSetPath,
                args.configurationPath,
                args.projectPath,
                dependencyFilter,
                args.forceDownload,
                args.fresh,
                searchProviders.filterIsInstance<DeclarationSearch>().firstOrNull()
            )
        }

        val baseDir = sources.sources.normalize()
        val sourcesHeader = formatSourcesHeader(sources, dependencyFilter)

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
                    val nested = indexService.listNestedPackageContents(sources, args.path)
                    val formatted = if (nested != null) formatNestedPackageContents(nested) else formatPackageContents(packageContents)
                    return@tool "$sourcesHeader\n\nPackage: ${args.path}\n\n" + paginateText(formatted, args.pagination)
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
        @Description("Targeting a specific project path (e.g., ':app').")
        val projectPath: String? = null,
        @Description("Authoritatively targeting a configuration (e.g., ':app:debugCompileClasspath'). Higher precedence.")
        val configurationPath: String? = null,
        @Description("Authoritatively targeting a source set (e.g., ':app:main'). Highest project precedence.")
        val sourceSetPath: String? = null,
        @Description("Full-string regex over group:name:version[:variant], or `jdk`; blank ignored.")
        val dependency: String? = null,
        @Description("Restricts the search to Gradle Build Tool source code only; HIGHEST overall precedence.")
        val gradleOwnSource: Boolean = false,
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
            |Searches symbols or text across dependency, plugin, Gradle, or JDK source trees; use instead of shell grep, which cannot locate remote dependency sources.
            |JDK sources appear under `jdk/sources/...` for JVM scopes when local `src.zip` exists; use `dependency: "jdk"` for JDK-only searches.
            |Buildscript (plugin) dependencies are excluded by default to reduce noise. To search plugins, use `sourceSetPath: ":buildscript"` (root project) or `sourceSetPath: ":app:buildscript"` (subproject).
            |Sources are CAS-cached (immutable). Filtered calls use distinct session-view cache entries, but the dependency regex never changes CAS identity. Use `fresh=true` for dependency changes; `forceDownload=true` only to recover corrupt/missing files.
            |ALWAYS scope with a project, configuration, or source set (or use `gradleOwnSource: true`) — unscoped search is no longer supported.
            |The `dependency` parameter narrows the searched view with a full-string Kotlin regex over `group:name:version[:variant]`; unresolved deps use `group:name`, and blank strings are ignored.
            |Returns the absolute path of the sources root. 
            |**NOTE:** Dependency directories are junctions (Windows) or symlinks; standard CLI tools like `rg` or `fd` will NOT follow them by default. ALWAYS pass `--follow` or equivalent (e.g., `rg --follow <pattern> <path>`).
            |
            |### Search Modes
            |- `DECLARATION`: Finds class, method, or interface definitions. All symbol searches are **case-sensitive**.
            |  - **Fields**: Matches against `name` (simple name, e.g., `MyClass`) and `fqn` (fully qualified name, e.g., `com.example.MyClass`).
            |  - **Unqualified Queries**: A query without a field prefix (e.g., `query: "MyClass"`) searches BOTH `name` and `fqn` fields.
            |  - **Prefix Syntax**: Use `name:X` for simple names or `fqn:x.y.Z` for precision. Supports Lucene wildcards (`*`, `?`).
            |  - **FQN Matching**: `fqn` is **literal** (uses `KeywordAnalyzer`). It preserves dots and case. Use `fqn:*.MyClass` for partial matches or `fqn:com.example.*` for package-level matches.
            |  - **Regex**: Wrap query in `/` for a full regular expression on the `fqn` field (e.g., `query: "/.*\.internal\..*/"`).
            |- `FULL_TEXT` (default): Exhaustive text search using a Lucene query. **Case-insensitive**. Escape special characters like `:`, `=`, `+`.
            |- `GLOB`: Locates files by name or extension using Java glob syntax. **Case-insensitive** (e.g., `query: "**/AndroidManifest.xml"`).
            |
            |### Examples
            |- All deps: `{ projectPath: ":", query: "CoroutineScope", searchType: "DECLARATION" }`
            |- Full-text: `{ projectPath: ":", query: "TIMEOUT_MS" }`
            |- Single dep: `{ projectPath: ":", dependency: "^org\\.jetbrains\\.kotlinx:kotlinx-coroutines-core(:.*)?${'$'}", query: "launch", searchType: "DECLARATION" }`
            |- JDK sources: `{ sourceSetPath: ":app:main", dependency: "jdk", query: "String", searchType: "DECLARATION" }`
            |- Gradle Build Tool source: `{ gradleOwnSource: true, query: "DefaultProject", searchType: "DECLARATION" }`
            |- Plugins: `{ sourceSetPath: ":buildscript", query: "MyPlugin", searchType: "DECLARATION" }`
            |- Files: `{ projectPath: ":", query: "**/plugin.properties", searchType: "GLOB" }`
            |
            |### Result Grouping
            |Results are grouped by proximity: matches within the snippet context range ($DEFAULT_SNIPPET_RANGE) are combined into a single result with a multi-line snippet showing context. Matches that are far apart in a file produce separate results.
            |
            |Once found, read content with `${ToolNames.READ_DEPENDENCY_SOURCES}`.
        """.trimMargin()
    ) { args ->
        val providerClass = args.searchType.providerClass
        val provider = searchProviders.find { providerClass.isInstance(it) }
            ?: error("Search provider '${args.searchType}' (${providerClass.simpleName}) not registered. This usually indicates a dependency injection configuration error.")

        val root = with(server) { args.projectRoot.resolveRoot() }
        val dependencyFilter = normalizeDependencyFilter(args.dependency)
        val sources = with(progressReporter) {
            resolveSources(
                root,
                args.gradleOwnSource,
                args.sourceSetPath,
                args.configurationPath,
                args.projectPath,
                dependencyFilter,
                args.forceDownload,
                args.fresh,
                provider
            )
        }

        val response = indexService.search(sources, provider, args.query, args.pagination)
        val sourcesHeader = formatSourcesHeader(sources, dependencyFilter)
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

    private fun formatNestedPackageContents(contents: NestedPackageContents): String {
        return buildString {
            if (contents.tooManySubPackages) {
                appendLine("Sub-packages (too many sub-packages to expand; use a more specific path):")
                contents.subPackages.forEach { appendLine("- ${it.name}/") }
            } else if (contents.subPackages.isNotEmpty()) {
                appendLine("Sub-packages:")
                for (sub in contents.subPackages) {
                    if (sub.symbols.isEmpty() && sub.subPackages.isEmpty()) {
                        appendLine("- ${sub.name}/")
                    } else if (sub.symbols.isEmpty()) {
                        appendLine("- ${sub.name}/  (${sub.subPackages.size} sub-packages)")
                    } else {
                        appendLine("- ${sub.name}/  (${sub.symbols.size} symbols)")
                    }
                    sub.symbols.forEach { appendLine("    - $it") }
                }
            }
            if (contents.symbols.isNotEmpty()) {
                if (contents.subPackages.isNotEmpty()) appendLine()
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

    private fun formatSourcesHeader(sources: SourcesDir, dependency: String? = null): String =
        "Sources root: ${sources.sources.normalize()}\n" +
                "**NOTE:** Dependency directories are junctions/symlinks; use `--follow` or equivalent with tools like `rg` or `fd`.\n" +
                formatRefreshMessage(sources.lastRefresh()) +
                emptyFilteredScopeNote(sources, dependency)

    private fun emptyFilteredScopeNote(sources: SourcesDir, dependency: String?): String {
        val hasEmptyFilteredSession = dependency != null &&
                sources is SessionViewSourcesDir &&
                sources.manifest.dependencies.isEmpty() &&
                sources.manifest.failedDependencies.isEmpty()
        return if (hasEmptyFilteredSession) {
            "\n\n**NOTE:** The selected scope contains no dependency sources, so the dependency filter was not matched against any candidates."
        } else {
            ""
        }
    }

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
            val atDepthLimit = child.isDirectory() && depth + 1 >= maxDepth
            val countSuffix = if (atDepthLimit) "  (${child.listDirectoryEntries().size} items)" else ""
            appendLine(prefix + marker + child.name + if (child.isDirectory()) "/$countSuffix" else "")
            if (child.isDirectory() && !atDepthLimit) {
                val childPrefix = prefix + if (isLast) "    " else "│   "
                walkDirectoryImpl(root, child, childPrefix, depth + 1, maxDepth)
            }
        }
    }

    context(progress: dev.rnett.gradle.mcp.ProgressReporter)
    private suspend fun resolveSources(
        root: dev.rnett.gradle.mcp.gradle.GradleProjectRoot,
        gradleOwnSource: Boolean,
        sourceSetPath: String?,
        configurationPath: String?,
        projectPath: String?,
        dependency: String?,
        forceDownload: Boolean,
        fresh: Boolean,
        providerToIndex: SearchProvider? = null
    ): SourcesDir {
        return when {
            gradleOwnSource -> gradleSourceService.getGradleSources(root, forceDownload = forceDownload, fresh = fresh, providerToIndex = providerToIndex)
            sourceSetPath != null -> sourcesService.resolveAndProcessSourceSetSources(root, sourceSetPath, dependency = dependency, forceDownload = forceDownload, fresh = fresh, providerToIndex = providerToIndex)
            configurationPath != null -> sourcesService.resolveAndProcessConfigurationSources(root, configurationPath, dependency = dependency, forceDownload = forceDownload, fresh = fresh, providerToIndex = providerToIndex)
            projectPath != null -> sourcesService.resolveAndProcessProjectSources(root, projectPath, dependency = dependency, forceDownload = forceDownload, fresh = fresh, providerToIndex = providerToIndex)
            dependency != null -> throw IllegalArgumentException("Must specify a scope (projectPath, configurationPath, or sourceSetPath) when filtering by dependency.")
            else -> throw IllegalArgumentException("Must specify a scope: projectPath, configurationPath, sourceSetPath, or set gradleOwnSource=true.")
        }
    }
}
