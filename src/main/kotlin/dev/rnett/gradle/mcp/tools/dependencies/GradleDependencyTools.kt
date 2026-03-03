package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.gradle.GradleProjectPath
import dev.rnett.gradle.mcp.gradle.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.tools.GradleProjectRootInput
import dev.rnett.gradle.mcp.tools.ToolNames
import dev.rnett.gradle.mcp.tools.resolveRoot
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

class GradleDependencyTools(
    private val dependencyService: GradleDependencyService
) : McpServerComponent("Project Dependency Tools", "Tools for querying Gradle dependencies and checking for updates.") {

    @Serializable
    data class GetDependenciesArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The Gradle project path, e.g. ':project-a:subproject-b'. Defaults to the root project (':'). Use this to get dependencies for a specific subproject.")
        val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        @Description("The name of a specific configuration to get dependencies for (e.g., 'implementation', 'runtimeClasspath'). If omitted, all configurations will be included.")
        val configuration: String? = null,
        @Description("The name of a specific source set to get dependencies for (e.g., 'main', 'test'). If omitted, configurations for all source sets will be included.")
        val sourceSet: String? = null,
        @Description("Whether to check for dependency updates against the configured repositories. If true, the latest available version will be shown next to the current version. Defaults to true.")
        val checkUpdates: Boolean = true,
        @Description("Whether to only return direct dependencies explicitly declared in the project. If false, the full dependency tree (including transitive dependencies) will be included. Defaults to true.")
        val onlyDirect: Boolean = true,
        @Description("Whether to only return a consolidated summary of available updates, rather than the full dependency report. If true, 'checkUpdates' is implied. Defaults to false.")
        val updatesOnly: Boolean = false,
        @Description("If true, ignores pre-release versions (e.g., those containing 'beta', 'rc', 'alpha') when checking for updates. Mutually exclusive with versionFilter.")
        val stableVersionsOnly: Boolean = false,
        @Description("A regex pattern to filter candidate update versions. Only versions matching this regex will be considered. Mutually exclusive with stableVersionsOnly.")
        val versionFilter: String? = null
    )

    val getDependencies by tool<GetDependenciesArgs, String>(
        ToolNames.GET_DEPENDENCIES,
        """
            Retrieves a detailed dependency report for a Gradle project. By default, it returns a tree of dependencies. If 'updatesOnly' is true, it instead returns a consolidated summary of all available updates across the specified project and its subprojects.
            Note: 'stableVersionsOnly' and 'versionFilter' are mutually exclusive.
            """.trimIndent()
    ) {
        require(!(it.stableVersionsOnly && it.versionFilter != null)) {
            "stableVersionsOnly and versionFilter are mutually exclusive."
        }
        val root = with(server) { it.projectRoot.resolveRoot() }
        val report = dependencyService.getDependencies(
            projectRoot = root,
            projectPath = it.projectPath.path,
            configuration = it.configuration,
            sourceSet = it.sourceSet,
            checkUpdates = it.checkUpdates || it.updatesOnly,
            versionFilter = when {
                it.stableVersionsOnly -> "^(?i).+?(?<![.-](?:alpha|beta|rc|m|milestone|releasecandidate|dev|ea|preview|snapshot|canary)[0-9]*)$"
                it.versionFilter != null -> it.versionFilter
                else -> null
            },
            onlyDirect = it.onlyDirect
        )

        if (it.updatesOnly) {
            formatUpdatesSummary(report)
        } else {
            formatDependencyReport(report)
        }
    }

    fun formatDependencyReport(report: GradleDependencyReport): String = buildString {
        appendLine("Dependency Report")
        appendLine("Note: (*) indicates a dependency that has already been listed; its transitive dependencies are not shown again.")
        appendLine()
        report.projects.forEach { project ->
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
    }.trim()

    fun formatUpdatesSummary(report: GradleDependencyReport): String {
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

        return if (updates.isEmpty()) {
            "No dependency updates found."
        } else {
            buildString {
                appendLine("Available Dependency Updates:")
                updates.distinctBy { "${it.projectPath}:${it.configuration}:${it.dependencyId}" }
                    .groupBy { it.dependencyId }
                    .forEach { (dependencyId, depUpdates) ->
                        val first = depUpdates.first()
                        appendLine("\n- $dependencyId: ${first.currentVersion} -> ${first.latestVersion}")
                        appendLine("  Found in:")
                        depUpdates.groupBy { it.projectPath }.forEach { (projectPath, projectUpdates) ->
                            val configs = projectUpdates.map { it.configuration }.distinct().sorted()
                            val sourceSets = projectUpdates.flatMap { it.sourceSets }.distinct().sorted()
                            val sourceSetInfo = if (sourceSets.isNotEmpty()) ", Source Sets: ${sourceSets.joinToString(", ")}" else ""
                            appendLine("    - Project: $projectPath, Configurations: ${configs.joinToString(", ")}$sourceSetInfo")
                        }
                    }
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
