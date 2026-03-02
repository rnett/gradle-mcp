package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.gradle.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.gradle.model.GradleDependency
import dev.rnett.gradle.mcp.gradle.model.GradleDependencyReport
import dev.rnett.gradle.mcp.gradle.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.gradle.model.GradleRepository
import dev.rnett.gradle.mcp.gradle.model.GradleSourceSetDependencies
import dev.rnett.gradle.mcp.gradle.model.GradleSourceSetDependencyReport

/**
 * Service that orchestrates retrieving dependency and repository information from a Gradle build
 * using the dependencies-report init script + custom report task, and parses the structured
 * stdout produced by that task into strongly-typed models.
 */
interface GradleDependencyService {
    /**
     * Runs the custom dependency report task and returns a parsed [GradleDependencyReport].
     *
     * @param projectRoot the Gradle project root
     * @param configuration optional configuration name to filter (passed as --configuration)
     * @param sourceSet optional source set name to filter. The init script may apply this filter; if not, it is used to post-filter parsed output.
     * @param allProjects whether to include all subprojects
     */
    suspend fun getDependencies(
        projectRoot: GradleProjectRoot,
        configuration: String?,
        sourceSet: String?,
        allProjects: Boolean
    ): GradleDependencyReport

    /**
     * Runs the custom dependency report task and returns a parsed [GradleSourceSetDependencyReport].
     *
     * @param projectRoot the Gradle project root
     * @param sourceSetPath the absolute Gradle path of the source set, e.g. :my:project:foo:main
     */
    suspend fun getSourceSetDependencies(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String
    ): GradleSourceSetDependencyReport

    /**
     * Runs the custom dependency report task and returns a parsed [GradleConfigurationDependencies].
     *
     * @param projectRoot the Gradle project root
     * @param configurationPath the absolute Gradle path of the configuration, e.g. :my:project:foo:main
     */
    suspend fun getConfigurationDependencies(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean = { false }
    ): GradleConfigurationDependencies
}

private class MutableDep(
    val id: String,
    val group: String?,
    val name: String,
    val version: String?,
    val reason: String?,
    val isAlreadyVisited: Boolean,
    val children: MutableList<MutableDep> = mutableListOf()
) {
    fun toImmutable(knownChildren: Map<String, List<GradleDependency>>, visited: Set<String> = emptySet()): GradleDependency {
        val immutableChildren = if (isAlreadyVisited && children.isEmpty()) {
            if (id in visited) emptyList() else knownChildren[id]?.map { it.copy(children = emptyList()) } ?: emptyList()
        } else {
            children.map { it.toImmutable(knownChildren, visited + id) }
        }
        return GradleDependency(id, group, name, version, reason, isAlreadyVisited, immutableChildren)
    }
}

private class MutableConfig(
    val name: String,
    val description: String?,
    val isResolvable: Boolean,
    val topLevelDeps: MutableList<MutableDep> = mutableListOf()
)

private class MutableProject(
    val path: String,
    val sourceSets: MutableList<GradleSourceSetDependencies> = mutableListOf(),
    val repositories: MutableList<GradleRepository> = mutableListOf(),
    val configurations: MutableList<MutableConfig> = mutableListOf()
)

class DefaultGradleDependencyService(
    private val gradle: GradleProvider
) : GradleDependencyService {

    override suspend fun getDependencies(
        projectRoot: GradleProjectRoot,
        configuration: String?,
        sourceSet: String?,
        allProjects: Boolean
    ): GradleDependencyReport {
        return getDependencies(projectRoot, configuration, sourceSet, allProjects) { false }
    }

    private suspend fun getDependencies(
        projectRoot: GradleProjectRoot,
        configuration: String?,
        sourceSet: String?,
        allProjects: Boolean,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean
    ): GradleDependencyReport {
        // Prepare invocation args: include the init script and arguments
        var args = GradleInvocationArguments.DEFAULT
            .withInitScript("dependencies-report")

        val additional = mutableListOf<String>()
        // Append custom dependency report task
        additional += if (allProjects) {
            ":mcpDependencyReport"
        } else {
            // If only root project, run task only at root
            ":mcpDependencyReport"
        }
        if (!configuration.isNullOrBlank()) {
            additional += listOf("--configuration", configuration)
        }
        if (!sourceSet.isNullOrBlank()) {
            // The init-script may handle this flag; pass through for filtering on Gradle side if supported
            additional += listOf("-Pmcp.sourceSet=$sourceSet")
        }
        args = args.copy(additionalArguments = args.additionalArguments + additional)

        val running: RunningBuild = gradle.runBuild(
            projectRoot = projectRoot,
            args = args,
            tosAccepter = tosAccepter,
            stdoutLineHandler = { /* captured via RunningBuild.consoleOutput; handlers optional */ },
            stderrLineHandler = { /* same */ }
        )

        // Wait for build to complete so console output is finalized
        running.awaitFinished()

        val text = running.consoleOutput.toString()
        val parsed = parseStructuredOutput(text)

        // If client requested sourceSet filtering and init script didn't filter, apply here
        val filtered = if (!sourceSet.isNullOrBlank()) {
            parsed.copy(
                projects = parsed.projects.map { p ->
                    p.copy(
                        sourceSets = p.sourceSets.filter { it.name == sourceSet },
                        configurations = p.configurations // do not filter configurations here; config-to-sources mapping is informational
                    )
                }
            )
        } else parsed

        return filtered
    }

    override suspend fun getSourceSetDependencies(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String
    ): GradleSourceSetDependencyReport {
        val lastColon = sourceSetPath.lastIndexOf(':')
        require(lastColon != -1) { "sourceSetPath must be an absolute Gradle path (e.g. :project:foo:main)" }
        val projectPath = if (lastColon == 0) ":" else sourceSetPath.substring(0, lastColon)
        val sourceSetName = sourceSetPath.substring(lastColon + 1)

        val report = getDependencies(projectRoot, null, sourceSetName, allProjects = true) { false }
        val project = report.projects.find { it.path == projectPath }
            ?: throw IllegalArgumentException("Project not found in report: $projectPath")

        val sourceSet = project.sourceSets.find { it.name == sourceSetName }
            ?: throw IllegalArgumentException("Source set not found in project $projectPath: $sourceSetName")

        val configs = project.configurations.filter { it.name in sourceSet.configurations }
        val repositories = project.repositories

        return GradleSourceSetDependencyReport(
            name = sourceSetName,
            configurations = configs,
            repositories = repositories
        )
    }

    override suspend fun getConfigurationDependencies(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean
    ): GradleConfigurationDependencies {
        val lastColon = configurationPath.lastIndexOf(':')
        require(lastColon != -1) { "configurationPath must be an absolute Gradle path (e.g. :project:foo:implementation)" }
        val projectPath = if (lastColon == 0) ":" else configurationPath.substring(0, lastColon)
        val configurationName = configurationPath.substring(lastColon + 1)

        val report = getDependencies(projectRoot, configurationName, null, allProjects = true, tosAccepter = tosAccepter)
        val project = report.projects.find { it.path == projectPath }
            ?: throw IllegalArgumentException("Project not found in report: $projectPath")

        return project.configurations.find { it.name == configurationName }
            ?: throw IllegalArgumentException("Configuration not found in project $projectPath: $configurationName")
    }

    internal fun parseStructuredOutput(output: String): GradleDependencyReport {
        val projects = mutableListOf<MutableProject>()
        var currentProject: MutableProject? = null
        var currentConfig: MutableConfig? = null
        val depStack = ArrayDeque<MutableDep>()

        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val type = line.substringBefore(":").trim().uppercase()
            val remaining = line.substringAfter(":").trim()
            val parts = remaining.split("|").map { it.trim() }
            val projectPath = parts.getOrNull(0).orEmpty()

            when (type) {
                "PROJECT" -> {
                    currentProject = MutableProject(projectPath)
                    projects += currentProject
                    currentConfig = null
                    depStack.clear()
                }

                "REPOSITORY" -> {
                    if (projectPath == currentProject?.path) {
                        val name = parts.getOrNull(1).orEmpty()
                        val url = parts.getOrNull(2)?.ifBlank { null }
                        currentProject.repositories.add(GradleRepository(name, url))
                    }
                }

                "SOURCESET" -> {
                    if (projectPath == currentProject?.path) {
                        val name = parts.getOrNull(1).orEmpty()
                        val configs = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.split(",")?.map { it.trim() }
                            ?: emptyList()
                        currentProject.sourceSets.add(GradleSourceSetDependencies(name, configs))
                    }
                }

                "CONFIGURATION" -> {
                    if (projectPath == currentProject?.path) {
                        val name = parts.getOrNull(1).orEmpty()
                        val desc = parts.getOrNull(2)?.ifBlank { null }
                        val resolvable = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false
                        currentConfig = MutableConfig(name, desc, resolvable)
                        currentProject.configurations.add(currentConfig)
                        depStack.clear()
                    }
                }

                "DEP" -> {
                    if (currentConfig == null || projectPath != currentProject?.path) return@forEach

                    val depthMarkers = parts.getOrNull(1).orEmpty()
                    val id = parts.getOrNull(2).orEmpty()
                    val group = parts.getOrNull(3)?.ifBlank { null }
                    val name = parts.getOrNull(4).orEmpty()
                    val version = parts.getOrNull(5)?.ifBlank { null }
                    val reason = parts.getOrNull(6)?.ifBlank { null }
                    val alreadyVisited = parts.getOrNull(7)?.toBooleanStrictOrNull() ?: false

                    val level = depthMarkers.count { it == '*' }
                    while (depStack.size >= level) depStack.removeLast()

                    val node = MutableDep(id, group, name, version, reason, alreadyVisited)
                    if (depStack.isEmpty()) {
                        currentConfig.topLevelDeps.add(node)
                    } else {
                        depStack.last().children.add(node)
                    }

                    if (!alreadyVisited) depStack.addLast(node)
                }
            }
        }

        // Collect known children from fully explored nodes
        val knownChildren = mutableMapOf<String, List<GradleDependency>>()
        fun recordKnown(dep: MutableDep) {
            if (!dep.isAlreadyVisited && dep.children.isNotEmpty()) {
                knownChildren[dep.id] = dep.children.map { it.toImmutable(emptyMap()) }
            }
            dep.children.forEach { recordKnown(it) }
        }
        projects.forEach { p -> p.configurations.forEach { c -> c.topLevelDeps.forEach { recordKnown(it) } } }

        return GradleDependencyReport(
            projects.map { p ->
                GradleProjectDependencies(
                    p.path,
                    p.sourceSets.toList(),
                    p.repositories.toList(),
                    p.configurations.map { c ->
                        GradleConfigurationDependencies(
                            c.name,
                            c.description,
                            c.isResolvable,
                            c.topLevelDeps.map { it.toImmutable(knownChildren) }
                        )
                    }
                )
            }
        )
    }
}
