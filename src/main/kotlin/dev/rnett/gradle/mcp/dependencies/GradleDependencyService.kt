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
import org.slf4j.LoggerFactory

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
     * @param options The options for the dependency request.
     */
    context(progress: ProgressReporter)
    suspend fun getDependencies(
        projectRoot: GradleProjectRoot,
        projectPath: String? = null,
        options: DependencyRequestOptions = DependencyRequestOptions()
    ): GradleDependencyReport

    /**
     * Runs the custom dependency report task and returns a parsed [GradleSourceSetDependencyReport].
     *
     * @param projectRoot the Gradle project root
     * @param sourceSetPath the absolute Gradle path of the source set, e.g. :my:project:foo:main
     * @param dependency optional dependency coordinate filter
     * @param fresh whether to force a fresh resolution
     */
    context(progress: ProgressReporter)
    suspend fun getSourceSetDependencies(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String,
        dependency: String? = null,
        fresh: Boolean = false
    ): GradleSourceSetDependencyReport

    /**
     * Runs the custom dependency report task and returns a parsed [GradleConfigurationDependencies].
     *
     * @param projectRoot the Gradle project root
     * @param configurationPath the absolute Gradle path of the configuration, e.g. :my:project:foo:main
     * @param dependency optional dependency coordinate filter
     * @param fresh whether to force a fresh resolution
     */
    context(progress: ProgressReporter)
    suspend fun getConfigurationDependencies(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        dependency: String? = null,
        fresh: Boolean = false
    ): GradleConfigurationDependencies

    /**
     * Download sources for all dependencies in the project.
     *
     * @param projectRoot the Gradle project root
     * @param dependency optional dependency coordinate filter
     * @param fresh whether to force a fresh resolution
     */
    context(progress: ProgressReporter)
    suspend fun downloadAllSources(projectRoot: GradleProjectRoot, dependency: String? = null, fresh: Boolean = false): GradleDependencyReport

    /**
     * Download sources for all dependencies in a specific project.
     *
     * @param projectRoot the Gradle project root
     * @param projectPath the Gradle path of the project
     * @param dependency optional dependency coordinate filter
     * @param fresh whether to force a fresh resolution
     */
    context(progress: ProgressReporter)
    suspend fun downloadProjectSources(
        projectRoot: GradleProjectRoot,
        projectPath: String,
        dependency: String? = null,
        fresh: Boolean = false,
        includeInternal: Boolean = false
    ): GradleProjectDependencies

    context(progress: ProgressReporter)
    suspend fun downloadConfigurationSources(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        dependency: String? = null,
        fresh: Boolean = false,
        includeInternal: Boolean = false
    ): GradleConfigurationDependencies

    context(progress: ProgressReporter)
    suspend fun downloadSourceSetSources(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String,
        dependency: String? = null,
        fresh: Boolean = false,
        includeInternal: Boolean = false
    ): GradleSourceSetDependencyReport
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
    val commonComponentId: String? = null,
    val sourcesFile: String? = null,
    val updatesChecked: Boolean = false,
    val children: MutableList<MutableDep> = mutableListOf()
) {
    fun toImmutable(
        knownChildren: Map<Triple<String, String?, List<String>>, List<GradleDependency>>,
        visited: Set<Triple<String, String?, List<String>>> = emptySet()
    ): GradleDependency {
        val key = Triple(id, variant, capabilities)
        val immutableChildren = if (key in visited) {
            emptyList()
        } else if (children.isEmpty() && key in knownChildren) {
            knownChildren[key] ?: emptyList()
        } else {
            children.map { it.toImmutable(knownChildren, visited + key) }
        }
        return GradleDependency(
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
            commonComponentId = commonComponentId,
            sourcesFile = sourcesFile?.let { kotlin.io.path.Path(it) },
            updatesChecked = updatesChecked,
            children = immutableChildren
        )
    }
}

private class MutableConfig(
    val name: String,
    val description: String?,
    val isResolvable: Boolean,
    val extendsFrom: List<String> = emptyList(),
    val isInternal: Boolean = false,
    val topLevelDeps: MutableList<MutableDep> = mutableListOf()
)

private class MutableProject(
    val path: String,
    val sourceSets: MutableList<GradleSourceSetDependencies> = mutableListOf(),
    val repositories: MutableList<GradleRepository> = mutableListOf(),
    val configurations: MutableList<MutableConfig> = mutableListOf(),
    var jdkHome: String? = null,
    var jdkVersion: String? = null
)

class DefaultGradleDependencyService(
    private val gradle: GradleProvider
) : GradleDependencyService {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultGradleDependencyService::class.java)
        const val BUILDSCRIPT_SOURCE_SET = "__mcp_buildscript__"

        fun normalizeProjectPath(path: String?): String {
            if (path.isNullOrEmpty() || path == ":") return ":"
            return if (path.startsWith(':')) path else ":$path"
        }
    }

    private class ProjectParser(val path: String) {
        companion object {
            private const val PROJECT_PATH_INDEX = 1
            private const val PROJECT_DISPLAY_NAME_INDEX = 2

            private const val REPOSITORY_NAME_INDEX = 1
            private const val REPOSITORY_URL_INDEX = 2

            private const val SOURCESET_NAME_INDEX = 1
            private const val SOURCESET_CONFIGS_INDEX = 2
            private const val SOURCESET_IS_JVM_INDEX = 3

            private const val JDK_HOME_INDEX = 1
            private const val JDK_VERSION_INDEX = 2

            private const val CONFIGURATION_NAME_INDEX = 1
            private const val CONFIGURATION_RESOLVABLE_INDEX = 2
            private const val CONFIGURATION_EXTENDS_FROM_INDEX = 3
            private const val CONFIGURATION_DESCRIPTION_INDEX = 4
            private const val CONFIGURATION_IS_INTERNAL_INDEX = 5

            private const val DEP_DEPTH_INDEX = 1
            private const val DEP_ID_INDEX = 2
            private const val DEP_GROUP_INDEX = 3
            private const val DEP_NAME_INDEX = 4
            private const val DEP_VERSION_INDEX = 5
            private const val DEP_REASON_INDEX = 6
            private const val DEP_LATEST_VERSION_INDEX = 7
            private const val DEP_IS_DIRECT_INDEX = 8
            private const val DEP_VARIANT_INDEX = 9
            private const val DEP_CAPABILITIES_INDEX = 10
            private const val DEP_FROM_CONFIGURATION_INDEX = 11
            private const val DEP_SOURCES_FILE_INDEX = 12
            private const val DEP_UPDATES_CHECKED_INDEX = 13
            private const val DEP_COMMON_ID_INDEX = 14
        }
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
                    val name = parts.getOrNull(REPOSITORY_NAME_INDEX).orEmpty()
                    val url = parts.getOrNull(REPOSITORY_URL_INDEX)?.ifBlank { null }
                    project.repositories.add(GradleRepository(name, url))
                }

                "SOURCESET" -> {
                    val name = parts.getOrNull(SOURCESET_NAME_INDEX).orEmpty()
                    val mappedName = when {
                        name == BUILDSCRIPT_SOURCE_SET -> "buildscript"
                        name.startsWith("kotlin:") -> name.substringAfter("kotlin:")
                        else -> name
                    }
                    val configs = parts.getOrNull(SOURCESET_CONFIGS_INDEX)?.takeIf { it.isNotEmpty() }?.split(",")?.map { it.trim() }
                        ?: emptyList()
                    val isJvm = parts.getOrNull(SOURCESET_IS_JVM_INDEX)?.toBooleanStrictOrNull() ?: (mappedName == "buildscript")
                    val existingIndex = project.sourceSets.indexOfFirst { it.name == mappedName }
                    if (existingIndex == -1) {
                        project.sourceSets.add(GradleSourceSetDependencies(mappedName, configs, isJvm))
                    } else {
                        val existing = project.sourceSets[existingIndex]
                        project.sourceSets[existingIndex] = existing.copy(
                            configurations = (existing.configurations + configs).distinct(),
                            isJvm = existing.isJvm || isJvm
                        )
                    }
                }

                "CONFIGURATION" -> {
                    val name = parts.getOrNull(CONFIGURATION_NAME_INDEX).orEmpty()
                    val resolvable = parts.getOrNull(CONFIGURATION_RESOLVABLE_INDEX)?.toBooleanStrictOrNull() ?: false
                    val extendsFrom = parts.getOrNull(CONFIGURATION_EXTENDS_FROM_INDEX)?.takeIf { it.isNotEmpty() }?.split(",")?.map { it.trim() }
                        ?: emptyList()
                    val desc = parts.getOrNull(CONFIGURATION_DESCRIPTION_INDEX)?.ifBlank { null }
                    val isInternal = parts.getOrNull(CONFIGURATION_IS_INTERNAL_INDEX)?.toBooleanStrictOrNull() ?: false
                    val config = MutableConfig(name, desc, resolvable, extendsFrom, isInternal)
                    currentConfig = config
                    project.configurations.add(config)
                    depStack.clear()
                }

                "JDK" -> {
                    val jdkHome = parts.getOrNull(JDK_HOME_INDEX)?.ifBlank { null }
                    val jdkVersion = parts.getOrNull(JDK_VERSION_INDEX)?.ifBlank { null }
                    project.jdkHome = jdkHome
                    project.jdkVersion = jdkVersion
                }

                "DEP" -> {
                    val config = currentConfig ?: return

                    val depthMarkers = parts.getOrNull(DEP_DEPTH_INDEX).orEmpty()
                    val level = depthMarkers.count { it == '*' }

                    val id = parts.getOrNull(DEP_ID_INDEX).orEmpty()
                    val group = parts.getOrNull(DEP_GROUP_INDEX)?.ifBlank { null }
                    val name = parts.getOrNull(DEP_NAME_INDEX).orEmpty()
                    val version = parts.getOrNull(DEP_VERSION_INDEX)?.ifBlank { null }
                    val reason = parts.getOrNull(DEP_REASON_INDEX)?.ifBlank { null }

                    val latestVersion = parts.getOrNull(DEP_LATEST_VERSION_INDEX)?.ifBlank { null }
                    val isDirect = parts.getOrNull(DEP_IS_DIRECT_INDEX)?.toBooleanStrictOrNull() ?: false

                    val variant = parts.getOrNull(DEP_VARIANT_INDEX)?.ifBlank { null }
                    val capabilities = parts.getOrNull(DEP_CAPABILITIES_INDEX)?.takeIf { it.isNotBlank() }?.split(",") ?: emptyList()
                    val fromConfiguration = parts.getOrNull(DEP_FROM_CONFIGURATION_INDEX)?.ifBlank { null }
                    val sourcesFile = parts.getOrNull(DEP_SOURCES_FILE_INDEX)?.ifBlank { null }
                    val updatesChecked = parts.getOrNull(DEP_UPDATES_CHECKED_INDEX)?.toBooleanStrictOrNull() ?: false
                    val commonComponentId = parts.getOrNull(DEP_COMMON_ID_INDEX)?.ifBlank { null }

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
                        commonComponentId = commonComponentId,
                        sourcesFile = sourcesFile,
                        updatesChecked = updatesChecked
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
        options: DependencyRequestOptions
    ): GradleDependencyReport {
        progress.report(0.0, 1.0, "Preparing dependency report...")
        // Prepare invocation args: include the init script and arguments
        var args = GradleInvocationArguments.DEFAULT
            .withInitScript(InitScriptNames.DEPENDENCIES_REPORT)

        val additional = mutableListOf<String>()
        if (options.fresh) {
            additional += "--refresh-dependencies"
        }

        // Append custom dependency report task
        val task = if (projectPath != null) {
            val normalizedPath = normalizeProjectPath(projectPath)
            if (normalizedPath == ":") ":mcpDependencyReport"
            else "$normalizedPath:mcpDependencyReport"
        } else {
            "mcpDependencyReport"
        }
        additional += task

        fun addProp(name: String, value: Any?) {
            if (value is Boolean && value) {
                additional += "-Pmcp.$name=true"
            } else if (value is String && value.isNotBlank()) {
                val targetValue = if (name == "sourceSet" && value == "buildscript") BUILDSCRIPT_SOURCE_SET else value
                additional += "-Pmcp.$name=$targetValue"
            }
        }

        addProp("configuration", options.configuration)
        addProp("sourceSet", options.sourceSet)
        addProp("dependencyFilter", options.dependency)
        addProp("checkUpdates", options.checkUpdates)
        addProp("versionFilter", options.versionFilter)
        addProp("stableOnly", options.stableOnly)
        addProp("onlyDirect", options.onlyDirect)
        addProp("downloadSources", options.downloadSources)
        addProp("excludeBuildscript", options.excludeBuildscript)

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
            LOGGER.info("RAW OUTPUT ON FAILURE:\n${running.consoleOutput}")
            val failure = (finished.outcome as BuildOutcome.Failed).failures.firstOrNull()
            val allMessages = failure?.flatten()?.mapNotNull { it.message }?.joinToString("; ") ?: "Unknown error"
            throw IllegalStateException("Gradle build failed: $allMessages")
        }

        val text = running.consoleOutput.toString()
        val parsed = parseStructuredOutput(text)

        // If client requested configuration or sourceSet filtering, or internal configuration filtering, apply here
        val filtered = if (!options.configuration.isNullOrBlank() || !options.sourceSet.isNullOrBlank() || !options.includeInternal) {
            val result = parsed.copy(
                projects = parsed.projects.mapNotNull { p ->
                    var newConfigs = if (!options.configuration.isNullOrBlank()) p.configurations.filter { it.name == options.configuration } else p.configurations
                    if (!options.includeInternal) {
                        newConfigs = newConfigs.filter { !it.isInternal }
                    }

                    var newSourceSets = if (!options.sourceSet.isNullOrBlank()) {
                        val sourceSetFilter = if (options.sourceSet == BUILDSCRIPT_SOURCE_SET) "buildscript" else options.sourceSet
                        p.sourceSets.filter { it.name == sourceSetFilter }
                    } else p.sourceSets

                    if (!options.includeInternal) {
                        val validConfigNames = newConfigs.map { it.name }.toSet()
                        newSourceSets = newSourceSets.map { ss ->
                            ss.copy(configurations = ss.configurations.filter { it in validConfigNames })
                        }.filter { it.configurations.isNotEmpty() }
                    }

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
            if (result.projects.isEmpty() && parsed.projects.isNotEmpty()) {
                val msg = listOfNotNull(
                    options.configuration?.let { "Configuration '$it'" },
                    options.sourceSet?.let { "SourceSet '$it'" }
                ).joinToString(" and ")
                throw IllegalArgumentException("$msg not found in project")
            }
            result
        } else parsed

        return filtered
    }

    context(progress: ProgressReporter)
    override suspend fun getSourceSetDependencies(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String,
        dependency: String?,
        fresh: Boolean
    ): GradleSourceSetDependencyReport {
        return getSourceSetDependencies(projectRoot, sourceSetPath, DependencyRequestOptions(dependency = dependency, fresh = fresh))
    }

    context(progress: ProgressReporter)
    private suspend fun getSourceSetDependencies(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String,
        options: DependencyRequestOptions
    ): GradleSourceSetDependencyReport {
        val lastColon = sourceSetPath.lastIndexOf(':')
        require(lastColon != -1) { "sourceSetPath must be an absolute Gradle path (e.g. :project:foo:main)" }
        val projectPath = normalizeProjectPath(if (lastColon == 0) ":" else sourceSetPath.substring(0, lastColon))
        val sourceSetName = sourceSetPath.substring(lastColon + 1)
        val internalSourceSetName = if (sourceSetName == "buildscript") BUILDSCRIPT_SOURCE_SET else sourceSetName

        val report = getDependencies(
            projectRoot,
            projectPath = projectPath,
            options = options.copy(
                sourceSet = internalSourceSetName
            )
        )
        val project = report.projects.find { it.path == projectPath }
            ?: throw IllegalArgumentException("Project not found in report: $projectPath")

        val sourceSet = project.sourceSets.find { it.name == sourceSetName }
            ?: throw IllegalArgumentException("Source set not found in project $projectPath: $sourceSetName. Available: ${project.sourceSets.map { it.name }}")

        val configs = project.configurations.filter { it.name in sourceSet.configurations }
        val repositories = project.repositories

        return GradleSourceSetDependencyReport(
            name = sourceSetName,
            configurations = configs,
            repositories = repositories,
            isJvm = sourceSet.isJvm
        )
    }

    context(progress: ProgressReporter)
    override suspend fun getConfigurationDependencies(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        dependency: String?,
        fresh: Boolean
    ): GradleConfigurationDependencies {
        return getConfigurationDependencies(projectRoot, configurationPath, DependencyRequestOptions(dependency = dependency, fresh = fresh))
    }

    context(progress: ProgressReporter)
    private suspend fun getConfigurationDependencies(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        options: DependencyRequestOptions
    ): GradleConfigurationDependencies {
        val lastColon = configurationPath.lastIndexOf(':')
        require(lastColon != -1) { "configurationPath must be an absolute Gradle path (e.g. :project:foo:implementation)" }

        val parsedProject = normalizeProjectPath(if (lastColon == 0) ":" else configurationPath.substring(0, lastColon))
        val parsedName = configurationPath.substring(lastColon + 1)

        // Disambiguate the virtual buildscript configuration namespace from ordinary project paths.
        val (projectPath, configurationName) = if (parsedProject.endsWith(":buildscript")) {
            val p = if (parsedProject == ":buildscript") ":" else parsedProject.substring(0, parsedProject.length - ":buildscript".length)
            p to "buildscript:$parsedName"
        } else {
            parsedProject to parsedName
        }

        val report = getDependencies(
            projectRoot,
            projectPath = projectPath,
            options = options.copy(
                configuration = configurationName
            )
        )

        val project = report.projects.find { it.path == projectPath }
        val config = project?.configurations?.find { it.name == configurationName }

        if (config != null) return config

        // If the path belongs to a real project named buildscript, prefer the real project/configuration.
        val realProject = report.projects.find { it.path == parsedProject }
        val realConfig = realProject?.configurations?.find { it.name == parsedName }

        if (realConfig != null) return realConfig

        throw IllegalArgumentException("Configuration not found in project ${projectPath}: $configurationName (also checked $parsedProject:$parsedName)")
    }

    context(progress: ProgressReporter)
    override suspend fun downloadAllSources(projectRoot: GradleProjectRoot, dependency: String?, fresh: Boolean): GradleDependencyReport {
        return getDependencies(
            projectRoot = projectRoot,
            options = DependencyRequestOptions(
                dependency = dependency,
                downloadSources = true,
                excludeBuildscript = true,
                fresh = fresh
            )
        )
    }

    context(progress: ProgressReporter)
    override suspend fun downloadProjectSources(
        projectRoot: GradleProjectRoot,
        projectPath: String,
        dependency: String?,
        fresh: Boolean,
        includeInternal: Boolean
    ): GradleProjectDependencies {
        val normalizedPath = normalizeProjectPath(projectPath)
        val report = getDependencies(
            projectRoot = projectRoot,
            projectPath = normalizedPath,
            options = DependencyRequestOptions(
                dependency = dependency,
                downloadSources = true,
                excludeBuildscript = true,
                fresh = fresh,
                includeInternal = includeInternal
            )
        )
        return report.projects.find { it.path == normalizedPath }
            ?: throw IllegalArgumentException("Project not found in report: $normalizedPath")
    }

    context(progress: ProgressReporter)
    override suspend fun downloadConfigurationSources(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        dependency: String?,
        fresh: Boolean,
        includeInternal: Boolean
    ): GradleConfigurationDependencies {
        return getConfigurationDependencies(
            projectRoot = projectRoot,
            configurationPath = configurationPath,
            options = DependencyRequestOptions(
                dependency = dependency,
                downloadSources = true,
                excludeBuildscript = false, // Design Decision 3: Inclusion is handled by configuration target
                fresh = fresh,
                includeInternal = includeInternal
            )
        )
    }

    context(progress: ProgressReporter)
    override suspend fun downloadSourceSetSources(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String,
        dependency: String?,
        fresh: Boolean,
        includeInternal: Boolean
    ): GradleSourceSetDependencyReport {
        return getSourceSetDependencies(
            projectRoot = projectRoot,
            sourceSetPath = sourceSetPath,
            options = DependencyRequestOptions(
                dependency = dependency,
                downloadSources = true,
                excludeBuildscript = false, // Design Decision 3: Inclusion is handled by virtual source set target
                fresh = fresh,
                includeInternal = includeInternal
            )
        )
    }

    internal fun parseStructuredOutput(output: String): GradleDependencyReport {
        val projectParsers = mutableMapOf<String, ProjectParser>()
        val projectOrder = mutableListOf<String>()

        val marker = "[gradle-mcp] [DEPENDENCIES]"

        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith(marker)) return@forEach

            val data = line.substringAfter(marker).trim()
            if (data.isEmpty()) return@forEach

            val type = data.substringBefore("|").trim().uppercase()
            val remaining = data.substringAfter("|").trim()
            val parts = remaining.split(Regex("(?<!\\\\)\\|")).map { it.trim().replace("\\|", "|") }

            if (type !in setOf("PROJECT", "REPOSITORY", "SOURCESET", "CONFIGURATION", "DEP", "JDK")) return@forEach

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

        val knownChildren = mutableMapOf<Triple<String, String?, List<String>>, List<GradleDependency>>()
        val visited = mutableSetOf<Triple<String, String?, List<String>>>()
        fun recordKnown(dep: MutableDep) {
            val key = Triple(dep.id, dep.variant, dep.capabilities)
            if (!visited.add(key)) return

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
                            c.topLevelDeps.map { it.toImmutable(knownChildren) },
                            c.isInternal
                        )
                    },
                    jdkHome = p.jdkHome,
                    jdkVersion = p.jdkVersion
                )
            }
        )
    }
}
