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
        @Description("The absolute path to the project root directory. Defaults to the current workspace root. Always provide this if you are working in a multi-root workspace to ensure the correct project is targeted.")
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The Gradle project path (e.g., ':app'). Defaults to the root project (':'). Use the 'projects' task in 'gradle-introspection' to see all valid project paths.")
        val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        @Description("Filter the report by a specific configuration (e.g., 'runtimeClasspath', 'implementation'). This is highly recommended for large projects to reduce noise and context usage.")
        val configuration: String? = null,
        @Description("Filter the report by a specific source set (e.g., 'test', 'main').")
        val sourceSet: String? = null,
        @Description("If true, checks project repositories for newer versions of all dependencies. This is the authoritative way to audit your dependency health.")
        val checkUpdates: Boolean = true,
        @Description("If true (default), only shows direct dependencies in the summary. Set to false to see the full transitive dependency tree.")
        val onlyDirect: Boolean = true,
        @Description("If true, only returns a summary of dependencies that have available updates. This is the most token-efficient way to perform regular update audits.")
        val updatesOnly: Boolean = false,
        @Description("If true, ignores pre-release versions (alpha, beta, rc, etc.) when checking for updates. STRONGLY RECOMMENDED for stable production environments.")
        val stableOnly: Boolean = false,
        @Description("A regex pattern for filtering candidate update versions. Use this for surgical control over which versions are considered.")
        val versionFilter: String? = null
    )

    val inspectDependencies by tool<InspectDependenciesArgs, String>(
        ToolNames.INSPECT_DEPENDENCIES,
        """
            |The authoritative tool for querying project dependencies, performing high-resolution update checks, and viewing repository configurations.
            |It provides a managed, searchable view of your project's dependency graph that is far superior to reading raw build files.
            |
            |### High-Performance Features
            |- **Deep Dependency Intelligence**: View the full dependency tree for any project, configuration, or source set. Understand exactly why a specific version of a library is being included.
            |- **Automated Update Detection**: Instantly identify dependencies with newer versions available in your configured repositories. Support for stable-only filtering and custom version regexes.
            |- **Surgical Precision**: Filter results by configuration or source set to minimize noise. Use `updatesOnly` for highly token-efficient health checks.
            |- **Repository Visibility**: See the authoritative list of repositories (Maven Central, Google, etc.) being used for dependency resolution in each project.
            |
            |### Common Usage Patterns
            |- **Full Audit**: `inspect_dependencies(projectPath=":app")`
            |- **Token-Efficient Update Check**: `inspect_dependencies(updatesOnly=true, stableOnly=true)`
            |- **Configuration Deep Dive**: `inspect_dependencies(configuration="runtimeClasspath")`
            |
            |To discover new libraries or see all versions of a specific artifact, use the `search_maven_central` tool.
            |For built-in Gradle tasks like `dependencyInsight`, use the `gradle` tool with `captureTaskOutput`.
            |For detailed dependency management strategies, refer to the `gradle-dependencies` skill.
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
