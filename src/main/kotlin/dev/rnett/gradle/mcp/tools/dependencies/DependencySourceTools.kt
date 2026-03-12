package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.dependencies.GradleSourceService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.search.DeclarationSearch
import dev.rnett.gradle.mcp.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.dependencies.search.GlobSearch
import dev.rnett.gradle.mcp.dependencies.search.PackageContents
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
        @Description("Best for finding precise declarations (classes, methods, interfaces). Matches against the 'name' and 'fqn' fields. Case-sensitive. Supports glob wildcards (*, **) and partial FQNs. Note: Dot-separated paths are matched against the FQN field. Do NOT include keywords like 'class', 'interface', or 'fun' in the query. Only search for the declaration name itself (e.g., use 'MyClass', not 'class MyClass').")
        DECLARATION,

        @Description("Best for exhaustive searching of literal strings, constants, or code patterns. Case-insensitive. Uses high-performance Lucene indexing. Supports complex queries (e.g., '\"val x =\" AND NOT internal'). Remember to escape special characters like ':' or '='.")
        FULL_TEXT,

        @Description("Best for locating specific files by name or extension (e.g., '**/AndroidManifest.xml', '**/*.proto'). Case-insensitive. Uses standard Java glob syntax.")
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
        @Description("Setting to true authoritatively targets Gradle's own source code. This has HIGHEST overall precedence.")
        val gradleSource: Boolean = false,
        @Description("Reading a specific file or directory relative to the combined source root. Use exact paths from search results - the first- and second-level \"library directories\" MUST be included..")
        val path: String? = null,
        @Description("Setting to true forces authoritative re-download and re-indexing of targeted sources.")
        val forceDownload: Boolean = false,
        @Description("Setting to true retrieves a fresh dependency list from Gradle. STRONGLY RECOMMENDED if dependencies changed.")
        val fresh: Boolean = false,
        val pagination: PaginationInput = PaginationInput.DEFAULT_LINES
    )

    val readDependencySources by tool<ReadDependencySourcesArgs, String>(
        ToolNames.READ_DEPENDENCY_SOURCES,
        """|
            ALWAYS use this tool to read source files and explore directory structures of external library dependencies, plugins (via `buildscript:` configurations), or Gradle's internal engine.
            External dependency sources are NOT stored in your local project directory; generic shell tools like `cat`, `grep`, or `find` WILL FAIL to locate them.
            This tool provides high-performance, cached access to the exact source code your project compiles against, which is VASTLY superior and more reliable than generic web searches, external repository browsing, or interactive REPL exploration.
            Reading the source is the professionally recommended way to understand how to use an API, discover its available methods, and see its exact implementation logic.
            This tool supports dot-separated package paths (e.g., `org.gradle.api`) by querying the symbol index, which is more reliable than directory-based resolution in some cases (e.g., Kotlin). This allows exploring package contents even if they don't match the directory structure.
            To read sources for a plugin, pass `configurationPath=\":buildscript:classpath\"`.
            To read the sources of a particular library, the `path` MUST include the first- and second-level "library directories". 
            It typically looks like `<group>/<artifact>[-<variant>]-<version>-sources`. You can see all libraries by reading the root dir or group dirs.
            To find specific classes or methods across all dependencies first, use the `${ToolNames.SEARCH_DEPENDENCY_SOURCES}` tool (supports DECLARATION, FULL_TEXT, and GLOB search modes).
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
                args.forceDownload,
                args.fresh
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
                    sourcesService.listPackageContents(sources, args.path)
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
        @Description("Setting to true authoritatively searches Gradle Build Tool's own source code.")
        val gradleSource: Boolean = false,
        @Description("Performing an authoritative search with a regex (DECLARATION), Lucene query (FULL_TEXT), or glob (GLOB, e.g., '**/Job.kt').")
        val query: String,
        @Description("Selecting the search mode: FULL_TEXT (default, exhaustive strings), DECLARATION (full string regex match on declaration name), or GLOB (file paths).")
        val searchType: SearchType = SearchType.FULL_TEXT,
        @Description("Setting to true forces authoritative re-download and re-indexing of targeted sources.")
        val forceDownload: Boolean = false,
        @Description("Setting to true retrieves a fresh dependency list from Gradle before searching.")
        val fresh: Boolean = false,
        val pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS
    )

    val searchDependencySources by tool<SearchDependencySourcesArgs, String>(
        ToolNames.SEARCH_DEPENDENCY_SOURCES,
        """|
            ALWAYS use this tool to search for symbols or text within the combined source code of ALL external library dependencies, plugins, or Gradle's internal engine authoritatively.
            Generic shell tools like `grep` or `find` on the local directory WILL NOT find these external sources as they reside in remote Gradle caches.
            This tool provides high-performance, indexed search capabilities that far exceed basic grep-based exploration, offering surgical precision across the entire dependency graph.
            Searching the source code is the PROFESSIONALLY RECOMMENDED way to understand how to use an API, discover its available methods, and see its exact implementation logic.
            It is vastly superior and more reliable than interactive REPL exploration or external repository browsing.
            
            ### Supported Search Modes
            
            1.  **`DECLARATION` (Declaration Search)**
                -   **Best for**: Finding precise declarations of classes, interfaces, or methods.
                -   **How to invoke**: Set `searchType="DECLARATION"`. The `query` is **case-sensitive**. Do NOT include keywords like `class`, `interface`, or `fun` (e.g., use `MyClass`, not `class MyClass`).
                -   **Examples**: `query="Project"` (matches by simple name), `query="org.gradle.api.Project"` (matches by FQN), `query="fqn:org.gradle.*.Project"` (glob wildcard), `query="name:.*Configuration"` (regex match).
            
            2.  **`FULL_TEXT` (Default Mode)**
                -   **Best for**: Exhaustive searching of literal strings, constants, or code patterns.
                -   **How to invoke**: Set `searchType="FULL_TEXT"`. The `query` is **case-insensitive**.
                -   **Special Characters**: Characters like `:`, `=`, `+`, `-`, `*`, `/` are special operators and MUST be escaped with a backslash (e.g., `\:`) or enclosed in quotes for literal searches.
                -   **Examples**: `query="\"val x =\""`, `query="TIMEOUT_MS"`, `query="org.gradle.api.internal.artifacts"` (requires escaping or quotes for dots if you want exact literal matches, but generally works fine).
            
            3.  **`GLOB` (File Path Search)**
                -   **Best for**: Locating specific files by name or extension.
                -   **How to invoke**: Set `searchType="GLOB"`. The `query` is **case-insensitive**.
                -   **Examples**: `query="**/AndroidManifest.xml"` (find any file by name), `query="**/*.proto"` (find all files by extension).
            
            ### Authoritative Features
            - **Locating Declarations Precisely**: Use `DECLARATION` to jump directly to a declaration's definition across the entire dependency graph.
            - **Performing Exhaustive Full-Text Searches**: Use `FULL_TEXT` for broad discovery of constants or usage patterns.
            - **Managing Search Scopes**: Narrow searches to specific projects, configurations (including `buildscript:` configurations for plugins), or source sets to maintain token efficiency.
            - **Accessing Gradle Engine Internals**: Set `gradleSource=true` to search the authoritative source code of the Gradle Build Tool itself.
            
            Once identified, use the `${ToolNames.READ_DEPENDENCY_SOURCES}` tool to read the full content.
            Note: All returned paths are relative to the combined source root.
            For detailed search strategies, refer to the `searching_dependency_sources` skill.
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
                args.forceDownload,
                args.fresh
            )
        }

        val provider = when (args.searchType) {
            SearchType.DECLARATION -> DeclarationSearch
            SearchType.FULL_TEXT -> FullTextSearch
            SearchType.GLOB -> GlobSearch
        }
        val response = with(progressReporter) {
            sourcesService.search(sources, provider, args.query, args.pagination)
        }
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

    context(progress: dev.rnett.gradle.mcp.ProgressReporter)
    private suspend fun resolveSources(
        root: dev.rnett.gradle.mcp.gradle.GradleProjectRoot,
        gradleSource: Boolean,
        sourceSetPath: String?,
        configurationPath: String?,
        projectPath: String?,
        forceDownload: Boolean,
        fresh: Boolean
    ): dev.rnett.gradle.mcp.dependencies.SourcesDir {
        return when {
            gradleSource -> gradleSourceService.getGradleSources(root, forceDownload = forceDownload)
            sourceSetPath != null -> sourcesService.downloadSourceSetSources(root, sourceSetPath, forceDownload = forceDownload, fresh = fresh)
            configurationPath != null -> sourcesService.downloadConfigurationSources(root, configurationPath, forceDownload = forceDownload, fresh = fresh)
            projectPath != null -> sourcesService.downloadProjectSources(root, projectPath, forceDownload = forceDownload, fresh = fresh)
            else -> sourcesService.downloadAllSources(root, forceDownload = forceDownload, fresh = fresh)
        }
    }
}
