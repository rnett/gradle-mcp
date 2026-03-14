package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleRepository
import dev.rnett.gradle.mcp.dependencies.model.GradleSourceSetDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleSourceSetDependencyReport
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.tools.InitScriptNames
import kotlin.io.path.Path

/**
 * Service that orchestrates retrieving dependency and repository information from a Gradle build
 * using the dependencies-report init script + custom report task, and parses the structured
 * stdout produced by that task into strongly-typed models.
 */
interface GradleDependencyService {
    /**
     * Get dependencies for the project.
     *
     * @param projectRoot The root of the Gradle project.
     * @param projectPath The Gradle path of the project to get dependencies for (e.g. ":", ":subproject"). If null, all projects are included.
     * @param configuration The name of the configuration to get dependencies for (e.g. "implementation"). If null, all configurations are included.
     * @param sourceSet The name of the source set to get dependencies for (e.g. "main"). If null, all source sets are included.
     * @param checkUpdates Whether to check for dependency updates.
     * @param onlyDirect Whether to only include direct dependencies.
     */
    context(progress: ProgressReporter)
    suspend fun getDependencies(
        projectRoot: GradleProjectRoot,
        projectPath: String? = null,
        configuration: String? = null,
        sourceSet: String? = null,
        checkUpdates: Boolean = false,
        versionFilter: String? = null,
        stableOnly: Boolean = false,
        onlyDirect: Boolean = false,
        downloadSources: Boolean = false
    ): GradleDependencyReport

    /**
     * Runs the custom dependency report task and returns a parsed [GradleSourceSetDependencyReport].
     *
     * @param projectRoot the Gradle project root
     * @param sourceSetPath the absolute Gradle path of the source set, e.g. :my:project:foo:main
     */
    context(progress: ProgressReporter)
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
    context(progress: ProgressReporter)
    suspend fun getConfigurationDependencies(
        projectRoot: GradleProjectRoot,
        configurationPath: String
    ): GradleConfigurationDependencies

    /**
     * Download sources for all dependencies in the project.
     */
    context(progress: ProgressReporter)
    suspend fun downloadAllSources(projectRoot: GradleProjectRoot): GradleDependencyReport

    /**
     * Download sources for all dependencies in a specific project.
     */
    context(progress: ProgressReporter)
    suspend fun downloadProjectSources(projectRoot: GradleProjectRoot, projectPath: String): GradleProjectDependencies

    /**
     * Download sources for all dependencies in a specific configuration.
     */
    context(progress: ProgressReporter)
    suspend fun downloadConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String): GradleConfigurationDependencies

    /**
     * Download sources for all dependencies in a specific source set.
     */
    context(progress: ProgressReporter)
    suspend fun downloadSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String): GradleSourceSetDependencyReport
}

private class MutableDep(
    val id: String,
    val group: String?,
    val name: String,
    val version: String?,
    val variant: String? = null,
    val capabilities: List<String> = emptyList(),
    val latestVersion: String? = null,
    val isDirect: Boolean = false,
    val fromConfiguration: String? = null,
    val reason: String?,
    val sourcesFile: String? = null,
    val children: MutableList<MutableDep> = mutableListOf()
) {
    fun toImmutable(
        knownChildren: Map<Triple<String, String?, List<String>>, List<GradleDependency>>,
        visited: Set<Triple<String, String?, List<String>>> = emptySet()
    ): GradleDependency {
        val key = Triple(id, variant, capabilities)
        val immutableChildren = if (children.isEmpty() && key in knownChildren && key !in visited) {
            knownChildren[key] ?: emptyList()
        } else {
            children.map { it.toImmutable(knownChildren, visited + key) }
        }
        return GradleDependency(
            id, group, name, version, variant, capabilities, latestVersion, isDirect, fromConfiguration, reason, sourcesFile?.let { Path(it) },
            immutableChildren
        )
    }
}

private class MutableConfig(
    val name: String,
    val description: String?,
    val isResolvable: Boolean,
    val extendsFrom: List<String> = emptyList(),
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

    private class ProjectParser(val path: String) {
        val project = MutableProject(path)
        var currentConfig: MutableConfig? = null
        val depStack = ArrayDeque<MutableDep>()

        fun parseLine(type: String, parts: List<String>) {
            when (type) {
                "PROJECT" -> {
                    currentConfig = null
                    depStack.clear()
                }

                "REPOSITORY" -> {
                    val name = parts.getOrNull(1).orEmpty()
                    val url = parts.getOrNull(2)?.ifBlank { null }
                    project.repositories.add(GradleRepository(name, url))
                }

                "SOURCESET" -> {
                    val name = parts.getOrNull(1).orEmpty()
                    val configs = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.split(",")?.map { it.trim() }
                        ?: emptyList()
                    project.sourceSets.add(GradleSourceSetDependencies(name, configs))
                }

                "CONFIGURATION" -> {
                    val name = parts.getOrNull(1).orEmpty()
                    val desc = parts.getOrNull(2)?.ifBlank { null }
                    val resolvable = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false
                    val extendsFrom = parts.getOrNull(4)?.takeIf { it.isNotEmpty() }?.split(",")?.map { it.trim() }
                        ?: emptyList()
                    val config = MutableConfig(name, desc, resolvable, extendsFrom)
                    currentConfig = config
                    project.configurations.add(config)
                    depStack.clear()
                }

                "DEP" -> {
                    val config = currentConfig ?: return

                    val depthMarkers = parts.getOrNull(1).orEmpty()
                    val level = depthMarkers.count { it == '*' }

                    val id = parts.getOrNull(2).orEmpty()
                    val group = parts.getOrNull(3)?.ifBlank { null }
                    val name = parts.getOrNull(4).orEmpty()
                    val version = parts.getOrNull(5)?.ifBlank { null }
                    val reason = parts.getOrNull(6)?.ifBlank { null }

                    val latestVersion = parts.getOrNull(7)?.ifBlank { null }
                    val isDirect = parts.getOrNull(8)?.toBooleanStrictOrNull() ?: false

                    val variant = parts.getOrNull(9)?.ifBlank { null }
                    val capabilities = parts.getOrNull(10)?.takeIf { it.isNotBlank() }?.split(",") ?: emptyList()
                    val fromConfiguration = parts.getOrNull(11)?.ifBlank { null }
                    val sourcesFile = parts.getOrNull(12)?.ifBlank { null }

                    while (depStack.size >= level) depStack.removeLast()

                    val node = MutableDep(
                        id = id,
                        group = group,
                        name = name,
                        version = version,
                        variant = variant,
                        capabilities = capabilities,
                        latestVersion = latestVersion,
                        isDirect = isDirect,
                        fromConfiguration = fromConfiguration,
                        reason = reason,
                        sourcesFile = sourcesFile
                    )
                    if (depStack.isEmpty()) {
                        config.topLevelDeps.add(node)
                    } else {
                        depStack.last().children.add(node)
                    }

                    depStack.addLast(node)
                }
            }
        }
    }

    context(progress: ProgressReporter)
    override suspend fun getDependencies(
        projectRoot: GradleProjectRoot,
        projectPath: String?,
        configuration: String?,
        sourceSet: String?,
        checkUpdates: Boolean,
        versionFilter: String?,
        stableOnly: Boolean,
        onlyDirect: Boolean,
        downloadSources: Boolean
    ): GradleDependencyReport {
        // Prepare invocation args: include the init script and arguments
        var args = GradleInvocationArguments.DEFAULT
            .withInitScript(InitScriptNames.DEPENDENCIES_REPORT)

        val additional = mutableListOf<String>()
        // Append custom dependency report task
        val task = if (projectPath != null) {
            val path = ":" + projectPath.trim(':')
            if (path == ":") ":mcpDependencyReport"
            else "$path:mcpDependencyReport"
        } else {
            "mcpDependencyReport"
        }
        additional += task

        if (!configuration.isNullOrBlank()) {
            additional += "-Pmcp.configuration=$configuration"
        }
        if (!sourceSet.isNullOrBlank()) {
            // The init-script may handle this flag; pass through for filtering on Gradle side if supported
            additional += listOf("-Pmcp.sourceSet=$sourceSet")
        }
        if (checkUpdates) {
            additional += "-Pmcp.checkUpdates=true"
        }
        if (!versionFilter.isNullOrBlank()) {
            additional += "-Pmcp.versionFilter=$versionFilter"
        }
        if (stableOnly) {
            additional += "-Pmcp.stableOnly=true"
        }
        if (onlyDirect) {
            additional += "-Pmcp.onlyDirect=true"
        }
        if (downloadSources) {
            additional += "-Pmcp.downloadSources=true"
        }
        args = args.copy(additionalArguments = args.additionalArguments + additional)

        val running: RunningBuild = gradle.runBuild(
            projectRoot = projectRoot,
            args = args,
            stdoutLineHandler = { /* captured via RunningBuild.consoleOutput; handlers optional */ },
            stderrLineHandler = { /* same */ },
            progress = progress
        )

        // Wait for build to complete so console output is finalized
        val finished = running.awaitFinished()
        if (finished.outcome is BuildOutcome.Failed) {
            val failure = (finished.outcome as BuildOutcome.Failed).failures.firstOrNull()
            val allMessages = failure?.flatten()?.mapNotNull { it.message }?.joinToString("; ") ?: "Unknown error"
            throw IllegalStateException("Gradle build failed: $allMessages")
        }

        val text = running.consoleOutput.toString()
        val parsed = parseStructuredOutput(text)

        // If client requested configuration or sourceSet filtering, apply here
        val filtered = if (!configuration.isNullOrBlank() || !sourceSet.isNullOrBlank()) {
            parsed.copy(
                projects = parsed.projects.mapNotNull { p ->
                    val newConfigs = if (!configuration.isNullOrBlank()) p.configurations.filter { it.name == configuration } else p.configurations
                    val newSourceSets = if (!sourceSet.isNullOrBlank()) p.sourceSets.filter { it.name == sourceSet } else p.sourceSets

                    if (newConfigs.isEmpty() && newSourceSets.isEmpty() && (p.configurations.isNotEmpty() || p.sourceSets.isNotEmpty())) {
                        null
                    } else {
                        p.copy(
                            sourceSets = newSourceSets,
                            configurations = newConfigs
                        )
                    }
                }
            )
        } else parsed

        return filtered
    }

    context(progress: ProgressReporter)
    override suspend fun getSourceSetDependencies(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String
    ): GradleSourceSetDependencyReport {
        return getSourceSetDependencies(projectRoot, sourceSetPath, false)
    }

    context(progress: ProgressReporter)
    private suspend fun getSourceSetDependencies(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String,
        downloadSources: Boolean
    ): GradleSourceSetDependencyReport {
        val lastColon = sourceSetPath.lastIndexOf(':')
        require(lastColon != -1) { "sourceSetPath must be an absolute Gradle path (e.g. :project:foo:main)" }
        val projectPath = if (lastColon == 0) ":" else sourceSetPath.substring(0, lastColon)
        val sourceSetName = sourceSetPath.substring(lastColon + 1)

        val report = getDependencies(
            projectRoot,
            projectPath = projectPath,
            sourceSet = sourceSetName,
            checkUpdates = false,
            versionFilter = null,
            stableOnly = false,
            onlyDirect = false,
            downloadSources = downloadSources
        )
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

    context(progress: ProgressReporter)
    override suspend fun getConfigurationDependencies(
        projectRoot: GradleProjectRoot,
        configurationPath: String
    ): GradleConfigurationDependencies {
        return getConfigurationDependencies(projectRoot, configurationPath, false)
    }

    context(progress: ProgressReporter)
    private suspend fun getConfigurationDependencies(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        downloadSources: Boolean
    ): GradleConfigurationDependencies {
        val isBuildscript = configurationPath.contains(":buildscript:")
        val searchPath = if (isBuildscript) configurationPath.replace(":buildscript:", "::") else configurationPath
        val lastColon = searchPath.lastIndexOf(':')
        require(lastColon != -1) { "configurationPath must be an absolute Gradle path (e.g. :project:foo:implementation)" }

        val actualLastColon = if (isBuildscript) configurationPath.lastIndexOf(":buildscript:") else lastColon
        val projectPath = if (actualLastColon == 0) ":" else configurationPath.substring(0, actualLastColon)
        val configurationName = configurationPath.substring(actualLastColon + 1)

        val report = getDependencies(
            projectRoot,
            projectPath = projectPath,
            configuration = configurationName,
            sourceSet = null,
            checkUpdates = false,
            versionFilter = null,
            stableOnly = false,
            onlyDirect = false,
            downloadSources = downloadSources
        )
        val project = report.projects.find { it.path == projectPath }
            ?: throw IllegalArgumentException("Project not found in report: $projectPath")

        return project.configurations.find { it.name == configurationName }
            ?: throw IllegalArgumentException("Configuration not found in project $projectPath: $configurationName")
    }

    context(progress: ProgressReporter)
    override suspend fun downloadAllSources(projectRoot: GradleProjectRoot): GradleDependencyReport {
        return getDependencies(
            projectRoot = projectRoot,
            downloadSources = true
        )
    }

    context(progress: ProgressReporter)
    override suspend fun downloadProjectSources(
        projectRoot: GradleProjectRoot,
        projectPath: String
    ): GradleProjectDependencies {
        val report = getDependencies(
            projectRoot = projectRoot,
            projectPath = projectPath,
            downloadSources = true
        )
        val path = if (projectPath.startsWith(":")) projectPath else ":$projectPath"
        return report.projects.find { it.path == path }
            ?: throw IllegalArgumentException("Project not found in report: $path")
    }

    context(progress: ProgressReporter)
    override suspend fun downloadConfigurationSources(
        projectRoot: GradleProjectRoot,
        configurationPath: String
    ): GradleConfigurationDependencies {
        return getConfigurationDependencies(projectRoot, configurationPath, true)
    }

    context(progress: ProgressReporter)
    override suspend fun downloadSourceSetSources(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String
    ): GradleSourceSetDependencyReport {
        return getSourceSetDependencies(projectRoot, sourceSetPath, true)
    }

    internal fun parseStructuredOutput(output: String): GradleDependencyReport {
        val projectParsers = mutableMapOf<String, ProjectParser>()
        val projectOrder = mutableListOf<String>()

        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val type = line.substringBefore(":").trim().uppercase()
            val remaining = line.substringAfter(":").trim()
            val parts = remaining.split("|").map { it.trim() }

            if (type !in setOf("PROJECT", "REPOSITORY", "SOURCESET", "CONFIGURATION", "DEP")) return@forEach

            val rawProjectPath = parts.getOrNull(0).orEmpty()
            val projectPath = if (rawProjectPath.isBlank()) ":" else rawProjectPath

            val parser = projectParsers.getOrPut(projectPath) {
                projectOrder.add(projectPath)
                ProjectParser(projectPath)
            }
            parser.parseLine(type, parts)
        }

        val projects = projectOrder.map {
            requireNotNull(projectParsers[it]) { "No parser found for project $it" }.project
        }

        // Collect known children from fully explored nodes using composite key
        val knownChildren = mutableMapOf<Triple<String, String?, List<String>>, List<GradleDependency>>()
        fun recordKnown(dep: MutableDep) {
            val key = Triple(dep.id, dep.variant, dep.capabilities)
            if (dep.children.isNotEmpty() && key !in knownChildren) {
                knownChildren[key] = dep.children.map { it.toImmutable(emptyMap()) }
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
                            c.extendsFrom,
                            c.topLevelDeps.map { it.toImmutable(knownChildren) }
                        )
                    }
                )
            }
        )
    }
}
