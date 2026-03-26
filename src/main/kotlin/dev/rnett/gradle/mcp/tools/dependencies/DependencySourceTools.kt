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
        @Description("Finds declarations (class/method/interface). Case-sensitive; use 'name:X' or 'fqn:x.y.*'.")
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
            |Supports dot-separated package paths via the symbol index. Use `${ToolNames.SEARCH_DEPENDENCY_SOURCES}` to find paths first.
            |`path` without `dependency`: must include group/artifact prefix. With `dependency`: relative to library root.
            |Sources are CAS-cached (immutable). Use `fresh=true` for dependency changes; `forceDownload=true` only to recover corrupt/missing files.
            |ALWAYS scope with `dependency`, `projectPath`, `configurationPath`, or `sourceSetPath` — unscoped access indexes ALL dependencies and is VERY EXPENSIVE on large projects.
            |
            |### Examples
            |- Browse all deps: `{}`
            |- Browse single dep: `{ dependency: "org.jetbrains.kotlin:kotlin-stdlib" }`
            |- Read file: `{ dependency: "org.jetbrains.kotlin:kotlin-stdlib", path: "kotlin/collections/List.kt" }`
            |- Read package: `{ dependency: "org.jetbrains.kotlin:kotlin-stdlib", path: "kotlin.collections" }`
            |- Plugin sources: `{ configurationPath: ":buildscript:classpath" }`
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
                args.fresh,
                DeclarationSearch
            )
        }

        val baseDir = sources.sources.normalize()
        val refreshMessage = formatRefreshMessage(sources.lastRefresh())

        if (args.path != null) {
            val targetPath = baseDir.resolve(args.path).normalize()

            if (!targetPath.startsWith(baseDir)) {
                return@tool "Invalid path: ${args.path}"
            }

            if (!targetPath.exists()) {
                val packageContents = try {
                    indexService.listPackageContents(sources, args.path)
                } catch (e: Exception) {
                    return@tool "Path not found: ${args.path} (Error exploring package: ${e.message})"
                }

                if (packageContents != null) {
                    return@tool "${refreshMessage}\n\nPackage: ${args.path}\n\n" + paginateText(formatPackageContents(packageContents), args.pagination)
                }
                return@tool "Path not found: ${args.path}"
            }

            if (targetPath.isRegularFile()) {
                return@tool "${refreshMessage}\n\nFile: ${args.path}\n```\n${targetPath.readText()}\n```"
            } else if (targetPath.isDirectory()) {
                return@tool "${refreshMessage}\n\n" + paginateText(walkDirectory(targetPath, 2), args.pagination)
            } else {
                return@tool "Path is neither a file nor a directory: ${args.path}"
            }
        } else {
            "${refreshMessage}\n\n" + paginateText(walkDirectory(baseDir, 2), args.pagination)
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
        @Description("Search query: regex (DECLARATION), Lucene query (FULL_TEXT), or glob (GLOB, e.g., '**/Job.kt').")
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
            |Sources are CAS-cached (immutable). Use `fresh=true` for dependency changes; `forceDownload=true` only to recover corrupt/missing files.
            |ALWAYS scope with `dependency`, `projectPath`, `configurationPath`, or `sourceSetPath` — unscoped search indexes ALL dependencies and is VERY EXPENSIVE on large projects.
            |
            |### Search Modes
            |- `DECLARATION`: class/method/interface names. Case-sensitive; use `name:X` or `fqn:x.y.*`. No keywords like `class`.
            |- `FULL_TEXT` (default): Lucene query, case-insensitive. Escape special chars like `:` `=` `+`.
            |- `GLOB`: file paths, case-insensitive (e.g., `**/AndroidManifest.xml`).
            |
            |### Examples
            |- All deps: `{ query: "CoroutineScope", searchType: "DECLARATION" }`
            |- Full-text: `{ query: "TIMEOUT_MS" }`
            |- Single dep: `{ dependency: "org.jetbrains.kotlinx:kotlinx-coroutines-core", query: "launch", searchType: "DECLARATION" }`
            |- Gradle internals: `{ gradleSource: true, query: "DefaultProject", searchType: "DECLARATION" }`
            |- Plugins: `{ configurationPath: ":buildscript:classpath", query: "MyPlugin", searchType: "DECLARATION" }`
            |- Files: `{ query: "**/plugin.properties", searchType: "GLOB" }`
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
                args.fresh,
                provider
            )
        }

        val response = indexService.search(sources, provider, args.query, args.pagination)
        val refreshMessage = formatRefreshMessage(sources.lastRefresh())
        if (response.error != null) {
            isError = true
            return@tool response.error
        }
        refreshMessage + "\n\n" + formatSearchResults(response, args.query, args.pagination)
    }

    private fun formatSearchResults(response: SearchResponse<SearchResult>, query: String, pagination: PaginationInput): String? {
        if (response.error != null) {
            return response.error
        }
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

    private fun formatRefreshMessage(lastRefresh: kotlin.time.Instant?): String {
        if (lastRefresh == null) return "Sources have not been refreshed yet."
        val now = kotlin.time.Clock.System.now()
        val duration = now - lastRefresh
        val timestamp = lastRefresh.toString().substringBefore('.').replace('T', ' ') + " UTC"

        val ago = when {
            duration.inWholeDays > 0 -> "${duration.inWholeDays} day(s) ago"
            duration.inWholeHours > 0 -> "${duration.inWholeHours} hour(s) ago"
            duration.inWholeMinutes > 0 -> "${duration.inWholeMinutes} minute(s) ago"
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
            gradleSource -> gradleSourceService.getGradleSources(root, forceDownload = forceDownload)
            sourceSetPath != null -> sourcesService.resolveAndProcessSourceSetSources(root, sourceSetPath, dependency = dependency, forceDownload = forceDownload, fresh = fresh, providerToIndex = providerToIndex)
            configurationPath != null -> sourcesService.resolveAndProcessConfigurationSources(root, configurationPath, dependency = dependency, forceDownload = forceDownload, fresh = fresh, providerToIndex = providerToIndex)
            projectPath != null -> sourcesService.resolveAndProcessProjectSources(root, projectPath, dependency = dependency, forceDownload = forceDownload, fresh = fresh, providerToIndex = providerToIndex)
            else -> sourcesService.resolveAndProcessAllSources(root, dependency = dependency, forceDownload = forceDownload, fresh = fresh, providerToIndex = providerToIndex)
        }
    }
}
