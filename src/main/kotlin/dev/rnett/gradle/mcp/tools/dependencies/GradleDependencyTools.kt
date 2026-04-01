package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.DependencyRequestOptions
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.gradle.GradleProjectPath
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.tools.GradleProjectRootInput
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.tools.ToolNames
import dev.rnett.gradle.mcp.tools.paginate
import dev.rnett.gradle.mcp.tools.resolveRoot
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

class GradleDependencyTools(
    private val dependencyService: GradleDependencyService
) : McpServerComponent("Project Dependency Tools", "Tools for querying Gradle dependencies and checking for updates.") {

    @Serializable
    data class InspectDependenciesArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("Specifying the Gradle project path (e.g., ':app'). Defaults to root project (':').")
        val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        @Description("Filtering the report by a specific configuration (e.g., 'runtimeClasspath').")
        val configuration: String? = null,
        @Description("Filtering the report by a specific source set (e.g., 'test').")
        val sourceSet: String? = null,
        @Description("Filtering reported components to those matching a GAV coordinate (`group:name:version:variant`, `group:name:version`, `group:name`, or `group`). Transitive children of matched components are shown when `onlyDirect=false`.")
        val dependency: String? = null,
        @Description("Checking project repositories for newer versions of all dependencies authoritatively. Always `true` when `updatesOnly=true`.")
        val checkUpdates: Boolean = true,
        @Description("Showing only direct dependencies in the summary. Set to false for the full tree. Also controls update-check scope: only direct deps are checked when `true`.")
        val onlyDirect: Boolean = true,
        @Description("Returning a flat list of upgradeable dependencies: `group:artifact: current → latest` with project paths. Forces `checkUpdates=true`. Note: format changed from earlier versions — the dep key no longer includes the version and the separator changed from ASCII `->` to Unicode `→`.")
        val updatesOnly: Boolean = false,
        @Description("Ignoring pre-release versions (alpha, beta, rc, etc.) when checking for updates.")
        val stableOnly: Boolean = false,
        @Description("Regex filter for considered update versions (e.g., '^1\\.' to match versions starting with 1).")
        val versionFilter: String? = null,
        val pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS,
        @Description("Whether to exclude buildscript dependencies from the report. Defaults to false.")
        val excludeBuildscript: Boolean = false
    )

    val inspectDependencies by tool<InspectDependenciesArgs, String>(
        ToolNames.INSPECT_DEPENDENCIES,
        """
            |Inspects the project's resolved dependency graph, checks for updates, and audits plugins; use instead of manually parsing build files which misses transitive deps and dynamic versions.
            |
            |- **Update Check**: `checkUpdates=true` (default) detects newer versions — individual lines show `[UPDATE AVAILABLE: X.Y.Z]`; use `updatesOnly=true` for a flat summary: `group:artifact: current → latest` with the project paths where each dep is used (forces `checkUpdates=true`). Use `stableOnly=true` to exclude pre-release versions.
            |- **[UPDATE CHECK SKIPPED]**: Appears only for dependencies that were in scope for update checking but whose resolution genuinely failed — not for dependencies intentionally excluded from the update-check scope (e.g., transitive deps when `onlyDirect=true`).
            |- **Plugin Auditing**: Use `sourceSet="buildscript"` to audit plugins.
            |- **Targeted**: Use `dependency="org:artifact"` to target a single library — significantly faster.
            |- Use `${ToolNames.LOOKUP_MAVEN_VERSIONS}` to find released versions; `${ToolNames.GRADLE}` for `dependencyInsight`.
        """.trimMargin()
    ) {
        val root = with(server) { it.projectRoot.resolveRoot() }
        // updatesOnly forces checkUpdates regardless of the explicit checkUpdates value.
        val checkUpdatesEnabled = it.checkUpdates || it.updatesOnly
        val report = with(progressReporter) {
            dependencyService.getDependencies(
                projectRoot = root,
                projectPath = it.projectPath.path,
                options = DependencyRequestOptions(
                    configuration = it.configuration,
                    sourceSet = it.sourceSet,
                    dependency = it.dependency,
                    checkUpdates = checkUpdatesEnabled,
                    versionFilter = it.versionFilter,
                    stableOnly = it.stableOnly,
                    onlyDirect = it.onlyDirect,
                    excludeBuildscript = it.excludeBuildscript // Use the arg value (default false)
                )
            )
        }

        if (it.updatesOnly) {
            formatUpdatesSummary(report, it.pagination)
        } else {
            formatDependencyReport(report, it.pagination, checkUpdatesEnabled)
        }
    }

    internal fun formatDependencyReport(
        report: GradleDependencyReport,
        pagination: PaginationInput,
        checkUpdatesEnabled: Boolean = false
    ): String {
        if (report.projects.isEmpty()) return "No projects found."

        return buildString {
            appendLine("Dependency Report")
            appendLine("Note: (*) indicates a dependency that has already been listed; its transitive dependencies are not shown again.")
            appendLine()

            val pagedProjects = paginate(report.projects, pagination, "projects") { project ->
                buildString {
                    formatProject(project, checkUpdatesEnabled)
                }.trim()
            }
            append(pagedProjects)
        }.trim()
    }

    private fun StringBuilder.formatProject(project: GradleProjectDependencies, checkUpdatesEnabled: Boolean = false) {
        appendLine("Project: ${project.path}")
        if (project.repositories.isNotEmpty()) {
            appendLine("  Repositories:")
            project.repositories.forEach { repo ->
                appendLine("    - ${repo.name}${repo.url?.let { " ($it)" } ?: ""}")
            }
        }

        val sortedConfigs = project.configurations.sortedBy { project.configurationDepth(it.name) }
        val includedConfigs = project.configurations.associateBy { it.name }

        sortedConfigs.forEach { config ->
            appendLine("  Configuration: ${config.name}${config.description?.let { " ($it)" } ?: ""}")
            if (config.extendsFrom.isNotEmpty()) {
                appendLine("    Extends from: ${config.extendsFrom.joinToString(", ")}")
            }

            val parents = project.transitiveExtendsFrom(config.name)

            val filteredDeps = config.dependencies.filter { dep ->
                val match = parents.any { parentName ->
                    val parent = includedConfigs[parentName]
                    if (parent != null) {
                        val canSkip = parent.isResolvable || !config.isResolvable
                        if (canSkip) {
                            val parentDep = parent.dependencies.find {
                                (it.group == dep.group && it.name == dep.name) || it.id == dep.id
                            }
                            parentDep != null && parentDep.version == dep.version
                        } else false
                    } else false
                }
                !match
            }

            if (filteredDeps.isEmpty() && config.dependencies.isNotEmpty()) {
                appendLine("    (all dependencies inherited from parents)")
            } else if (config.dependencies.isEmpty()) {
                appendLine("    (no dependencies)")
            } else {
                renderDependencies(filteredDeps, 2, checkUpdatesEnabled = checkUpdatesEnabled) { dep ->
                    if (dep.fromConfiguration != null) {
                        val parent = includedConfigs[dep.fromConfiguration]
                        val parentDep = parent?.dependencies?.find {
                            (it.group == dep.group && it.name == dep.name) || it.id == dep.id
                        }
                        if (parentDep != null && parentDep.version != dep.version) {
                            " (was ${parentDep.version} in ${dep.fromConfiguration})"
                        } else null
                    } else null
                }
            }
        }
    }

    internal fun formatUpdatesSummary(report: GradleDependencyReport, pagination: PaginationInput): String {
        val updates = report.projects.flatMap { project ->
            val configToSourceSets = project.sourceSets.flatMap { ss -> ss.configurations.map { it to ss.name } }
                .groupBy({ it.first }, { it.second })

            // A single visited set per project deduplicates the same (projectPath, group:artifact)
            // across configurations and across diamond-dependency subtrees, avoiding redundant entries
            // before groupBy. The init script controls whether transitive deps appear in the model.
            val visited = mutableMapOf<String, MutableSet<String>>()
            project.configurations.flatMap { config ->
                val sourceSets = configToSourceSets[config.name] ?: emptyList()
                config.dependencies.flatMap { dep ->
                    findUpdates(dep, project.path, sourceSets.toSet(), visited)
                }
            }
        }

        // Group by dep coordinate (group:artifact) across all projects.
        // Note: when the same group:artifact resolves to different versions across projects,
        // the first project's currentVersion is shown. Use inspect_dependencies with a specific
        // dependency filter to see per-project version details.
        val groupedUpdates = updates.groupBy { it.dependencyId }
            .mapValues { (_, depUpdates) ->
                depUpdates.groupBy { it.projectPath }
                    .mapValues { (_, projectUpdates) -> projectUpdates.flatMap { it.sourceSets }.toSet().sorted() }
                    .toList().sortedBy { it.first }
            }
            .toList().sortedBy { it.first }

        return if (groupedUpdates.isEmpty()) {
            "No dependency updates found."
        } else {
            buildString {
                appendLine("Available Dependency Updates:")
                val paged = paginate(groupedUpdates, pagination, "dependency updates") { (dependencyId, projectSourceSets) ->
                    buildString {
                        // Get current/latest version from the first update for this dependencyId
                        val firstUpdate = updates.first { it.dependencyId == dependencyId }
                        appendLine("- $dependencyId: ${firstUpdate.currentVersion} → ${firstUpdate.latestVersion}")
                        appendLine("  Found in:")
                        projectSourceSets.forEach { (projectPath, sourceSets) ->
                            val ssInfo = if (sourceSets.isNotEmpty()) " (${sourceSets.joinToString(", ")})" else ""
                            appendLine("    - $projectPath$ssInfo")
                        }
                    }.trim()
                }
                append(paged)
            }.trim()
        }
    }

    private data class DependencyUpdate(
        val projectPath: String,
        val dependencyId: String,
        val currentVersion: String,
        val latestVersion: String,
        val sourceSets: Set<String>
    )

    /** Key used to track already-rendered nodes in the dependency tree. */
    internal data class DepVisitKey(val id: String, val variant: String?, val capabilities: List<String>)

    /** Returns true when [dep] has a known newer version available. */
    private fun isUpdateAvailable(dep: GradleDependency): Boolean =
        dep.latestVersion != null && dep.version != null && dep.latestVersion != dep.version

    private fun findUpdates(
        dep: GradleDependency,
        projectPath: String,
        sourceSets: Set<String>,
        visited: MutableMap<String, MutableSet<String>>
    ): List<DependencyUpdate> = buildList {
        val key = "$projectPath|${dep.group}:${dep.name}"
        val firstVisit = !visited.containsKey(key)
        val seenSourceSets = visited.getOrPut(key) { mutableSetOf() }
        val newlyAdded = seenSourceSets.addAll(sourceSets)

        if (!firstVisit && !newlyAdded) return@buildList

        if (isUpdateAvailable(dep)) {
            add(
                DependencyUpdate(
                    projectPath = projectPath,
                    dependencyId = "${dep.group}:${dep.name}",
                    currentVersion = dep.version!!,  // non-null: isUpdateAvailable checks dep.version != null
                    latestVersion = dep.latestVersion!!,  // non-null: isUpdateAvailable checks dep.latestVersion != null
                    sourceSets = sourceSets
                )
            )
        }
        dep.children.forEach { addAll(findUpdates(it, projectPath, sourceSets, visited)) }
    }

    private fun StringBuilder.renderDependencies(
        deps: List<GradleDependency>,
        indent: Int,
        checkUpdatesEnabled: Boolean = false,
        visited: MutableSet<DepVisitKey> = mutableSetOf(),
        noteProvider: (GradleDependency) -> String? = { null }
    ) {
        deps.forEach { dep ->
            val key = DepVisitKey(dep.id, dep.variant, dep.capabilities)
            val alreadyVisited = !visited.add(key)

            append("  ".repeat(indent))
            append("- ${dep.id}")

            noteProvider(dep)?.let { append(it) }

            if (alreadyVisited) {
                append(" (*)")
            }
            // isUpdateAvailable checks dep.version != null, so [UPDATE AVAILABLE] never fires for
            // null-version deps — consistent with the findUpdates path.
            if (isUpdateAvailable(dep)) {
                append(" [UPDATE AVAILABLE: ${dep.latestVersion}]")
            } else if (checkUpdatesEnabled && !dep.updatesChecked) {
                append(" [UPDATE CHECK SKIPPED]")
            }
            if (dep.reason != null) {
                append(" (${dep.reason})")
            }
            appendLine()
            if (!alreadyVisited) {
                renderDependencies(dep.children, indent + 1, checkUpdatesEnabled, visited, noteProvider)
            }
        }
    }
}
