package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
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
        @Description("Checking project repositories for newer versions of all dependencies authoritatively.")
        val checkUpdates: Boolean = true,
        @Description("Showing only direct dependencies in the summary. Set to false for the full tree.")
        val onlyDirect: Boolean = true,
        @Description("Returning only a summary of dependencies that have available updates.")
        val updatesOnly: Boolean = false,
        @Description("Ignoring pre-release versions (alpha, beta, rc, etc.) when checking for updates.")
        val stableOnly: Boolean = false,
        @Description("Applying a regex pattern for surgical control over considered update versions.")
        val versionFilter: String? = null,
        val pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS
    )

    val inspectDependencies by tool<InspectDependenciesArgs, String>(
        ToolNames.INSPECT_DEPENDENCIES,
        """
            |ALWAYS use this tool to inspect project dependencies, plugins (via `buildscript:` configurations), and check for updates instead of manually parsing build files.
            |Manual parsing is HIGHLY UNRELIABLE as it misses transitive dependencies, version resolution, and dynamic version updates.
            |This tool provides the ONLY authoritative, searchable view of the project's exact resolved dependency graph.
            |
            |### Dependency Intelligence Features
            |
            |1.  **Auditing the Graph**: Get a searchable, paginated view of direct and transitive dependencies.
            |2.  **Checking Updates**: Use `checkUpdates=true` (default) to detect newer versions in all repositories.
            |3.  **Update Summaries**: Use `updatesOnly=true` to return only a summary of dependencies that have available updates.
            |4.  **Plugin Auditing**: Use `configuration="buildscript:classpath"` to audit build script dependencies (plugins).
            |
            |### Discovery Best Practices
            |- **Searching Maven Central**: Use `${ToolNames.SEARCH_MAVEN_CENTRAL}` to find coordinates or version histories.
            |- **Dependency Insight**: Use `${ToolNames.GRADLE}` for built-in Gradle tasks like `dependencyInsight`.
            |- **Stability Filtering**: Use `stableOnly=true` to ignore pre-release versions (alpha, beta, rc) in update checks.
            |- **Precision Slicing**: Use `versionFilter` (regex) for surgical control over considered update versions.
        """.trimMargin()
    ) {
        val root = with(server) { it.projectRoot.resolveRoot() }
        val report = dependencyService.getDependencies(
            projectRoot = root,
            projectPath = it.projectPath.path,
            configuration = it.configuration,
            sourceSet = it.sourceSet,
            checkUpdates = it.checkUpdates || it.updatesOnly,
            versionFilter = when {
                it.stableOnly -> "^(?i).+?(?<![.-](?:alpha|beta|rc|m|milestone|releasecandidate|dev|ea|preview|snapshot|canary)[0-9]*)$"
                it.versionFilter != null -> it.versionFilter
                else -> null
            },
            onlyDirect = it.onlyDirect
        )

        if (it.updatesOnly) {
            formatUpdatesSummary(report, it.pagination)
        } else {
            formatDependencyReport(report, it.pagination)
        }
    }

    fun formatDependencyReport(report: GradleDependencyReport, pagination: PaginationInput): String {
        if (report.projects.isEmpty()) return "No projects found."

        return buildString {
            appendLine("Dependency Report")
            appendLine("Note: (*) indicates a dependency that has already been listed; its transitive dependencies are not shown again.")
            appendLine()

            val pagedProjects = paginate(report.projects, pagination, "projects") { project ->
                buildString {
                    formatProject(project)
                }.trim()
            }
            append(pagedProjects)
        }.trim()
    }

    private fun StringBuilder.formatProject(project: GradleProjectDependencies) {
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
                renderDependencies(filteredDeps, 2) { dep ->
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

    fun formatUpdatesSummary(report: GradleDependencyReport, pagination: PaginationInput): String {
        val updates = mutableListOf<DependencyUpdate>()

        report.projects.forEach { project ->
            val configToSourceSet = mutableMapOf<String, MutableList<String>>()
            project.sourceSets.forEach { sourceSet ->
                sourceSet.configurations.forEach { configName ->
                    configToSourceSet.getOrPut(configName) { mutableListOf() }.add(sourceSet.name)
                }
            }

            project.configurations.forEach { config ->
                config.dependencies.forEach { dep ->
                    findUpdates(dep, config, project.path, configToSourceSet[config.name] ?: emptyList(), updates)
                }
            }
        }

        val distinctUpdates = updates.distinctBy { "${it.projectPath}:${it.configuration}:${it.dependencyId}" }
            .groupBy { it.dependencyId }
            .toList()

        return if (distinctUpdates.isEmpty()) {
            "No dependency updates found."
        } else {
            buildString {
                appendLine("Available Dependency Updates:")
                val paged = paginate(distinctUpdates, pagination, "dependency updates") { (dependencyId, depUpdates) ->
                    buildString {
                        val first = depUpdates.first()
                        appendLine("\n- $dependencyId: ${first.currentVersion} -> ${first.latestVersion}")
                        appendLine("  Found in:")
                        depUpdates.groupBy { it.projectPath }.forEach { (projectPath, projectUpdates) ->
                            val configs = projectUpdates.map { it.configuration }.distinct().sorted()
                            val sourceSets = projectUpdates.flatMap { it.sourceSets }.distinct().sorted()
                            val sourceSetInfo = if (sourceSets.isNotEmpty()) ", Source Sets: ${sourceSets.joinToString(", ")}" else ""
                            appendLine("    - Project: $projectPath, Configurations: ${configs.joinToString(", ")}$sourceSetInfo")
                        }
                    }.trim()
                }
                append(paged)
            }.trim()
        }
    }

    private data class DependencyUpdate(
        val projectPath: String,
        val configuration: String,
        val sourceSets: List<String>,
        val dependencyId: String,
        val currentVersion: String?,
        val latestVersion: String
    )

    private fun findUpdates(
        dep: GradleDependency,
        config: GradleConfigurationDependencies,
        projectPath: String,
        sourceSets: List<String>,
        updates: MutableList<DependencyUpdate>
    ) {
        if (dep.latestVersion != null && dep.latestVersion != dep.version) {
            updates.add(
                DependencyUpdate(
                    projectPath = projectPath,
                    configuration = config.name,
                    sourceSets = sourceSets,
                    dependencyId = dep.id,
                    currentVersion = dep.version,
                    latestVersion = dep.latestVersion
                )
            )
        }
        dep.children.forEach { findUpdates(it, config, projectPath, sourceSets, updates) }
    }

    private fun StringBuilder.renderDependencies(
        deps: List<GradleDependency>,
        indent: Int,
        visited: MutableSet<Triple<String, String?, List<String>>>,
        noteProvider: (GradleDependency) -> String?
    ) {
        deps.forEach { dep ->
            val key = Triple(dep.id, dep.variant, dep.capabilities)
            val alreadyVisited = !visited.add(key)

            append("  ".repeat(indent))
            append("- ${dep.id}")

            noteProvider(dep)?.let { append(it) }

            if (alreadyVisited) {
                append(" (*)")
            }
            if (dep.latestVersion != null && dep.latestVersion != dep.version) {
                append(" [UPDATE AVAILABLE: ${dep.latestVersion}]")
            }
            if (dep.reason != null) {
                append(" (${dep.reason})")
            }
            appendLine()
            if (!alreadyVisited) {
                renderDependencies(dep.children, indent + 1, visited, noteProvider)
            }
        }
    }

    private fun StringBuilder.renderDependencies(
        deps: List<GradleDependency>,
        indent: Int,
        noteProvider: (GradleDependency) -> String? = { null }
    ) {
        renderDependencies(deps, indent, mutableSetOf(), noteProvider)
    }
}
