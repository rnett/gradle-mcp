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
    data class InspectDependenciesArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The Gradle project path (e.g., :app). Defaults to root.")
        val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        @Description("Filter by specific configuration (e.g., runtimeClasspath).")
        val configuration: String? = null,
        @Description("Filter by source set (e.g., test).")
        val sourceSet: String? = null,
        @Description("Check against repositories for newer versions.")
        val checkUpdates: Boolean = true,
        @Description("Only show direct dependencies. Defaults to true.")
        val onlyDirect: Boolean = true,
        @Description("Only show a summary of available updates.")
        val updatesOnly: Boolean = false,
        @Description("Ignore pre-release versions (alpha, beta, rc, etc.).")
        val stableOnly: Boolean = false,
        @Description("Regex for filtering candidate update versions.")
        val versionFilter: String? = null
    )

    val inspectDependencies by tool<InspectDependenciesArgs, String>(
        ToolNames.INSPECT_DEPENDENCIES,
        """
            |Query project dependencies, check for available updates, and view repository configurations.
            |
            |**projectRoot** should be the file system path of the Gradle project's root directory (containing gradlew and settings.gradle). Providing this ensures the tool executes in the correct project context and avoids ambiguities in multi-root or environment-dependent workspaces. If omitted, the tool will attempt to auto-detect the root from the current MCP roots or the GRADLE_MCP_PROJECT_ROOT environment variable. **It MUST be an absolute path.**
            |
            |Use this tool for:
            |- Viewing the dependency tree for a specific project or configuration.
            |- Checking for available library updates across the project.
            |- Filtering dependencies by source set or configuration.
            |- Identifying repository URLs used for dependency resolution.
            |
            |To search for new libraries or see all versions of a library on Maven Central, use the `search_maven_central` tool.
            |For built-in Gradle dependency tasks, use the `gradlew` tool with `captureTaskOutput`.
            |For detailed workflows on dependency management, refer to the `gradle-dependencies` skill.
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
