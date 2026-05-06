import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

class McpRepositoryData(
    val name: String,
    val url: String
) : java.io.Serializable

class McpConfigurationMetadata(
    val name: String,
    val description: String?,
    val isCanBeResolved: Boolean,
    val extendsFrom: ArrayList<String>,
    val declaredDependencies: HashSet<Pair<String?, String>>,
    val isInternal: Boolean
) : java.io.Serializable

class McpSourceSetMetadata(
    val configurations: ArrayList<String>,
    val isJvm: Boolean
) : java.io.Serializable

class McpResolvedComponent(
    val id: String,
    val group: String,
    val name: String,
    val version: String,
    val selectionReason: String,
    val variants: HashMap<String, McpResolvedVariant>,
    val commonComponentId: String? = null
) : java.io.Serializable

class McpResolvedVariant(
    val name: String,
    val capabilities: ArrayList<String>,
    val dependencies: ArrayList<McpResolvedDependency>
) : java.io.Serializable

class McpResolvedDependency(
    val requested: String,
    val selectedId: String?,
    val variantName: String?,
    val failure: String?
) : java.io.Serializable

class McpResolvedGraph(
    val rootId: String,
    val rootVariantName: String?,
    val rootDependencies: ArrayList<McpResolvedDependency>,
    val components: HashMap<String, McpResolvedComponent>
) : java.io.Serializable

@UntrackedTask(because = "Reporting task")
abstract class McpDependencyReportTask : DefaultTask() {
    companion object {
        const val BUILDSCRIPT_PREFIX = "buildscript:"
        const val BUILDSCRIPT_SOURCE_SET = "__mcp_buildscript__"
        const val KMP_METADATA_CONFIGURATION_SUFFIX = "DependenciesMetadata"

        fun normalizeDependencyFilter(filter: String?): String? = filter?.takeIf { it.isNotBlank() }

        fun matchesAnyDependencyCoordinate(regex: Regex, coordinates: Iterable<String>): Boolean =
            coordinates.any { regex.matches(it) }

        fun canonicalDependencyCoordinate(group: String?, name: String, version: String?, variant: String? = null): String = buildString {
            group?.takeIf { it.isNotBlank() }?.let {
                append(it)
                append(':')
            }
            append(name)
            version?.takeIf { it.isNotBlank() }?.let {
                append(':')
                append(it)
            }
            variant?.takeIf { it.isNotBlank() }?.let {
                append(':')
                append(it)
            }
        }

        fun dependencyCoordinateCandidates(group: String?, name: String, version: String?, variant: String? = null, unresolved: Boolean = false): List<String> {
            if (unresolved) {
                return listOf(canonicalDependencyCoordinate(group, name, null))
            }

            val coordinates = ArrayList<String>(2)
            if (!version.isNullOrBlank() && !variant.isNullOrBlank()) {
                coordinates.add(canonicalDependencyCoordinate(group, name, version, variant))
            }
            coordinates.add(canonicalDependencyCoordinate(group, name, version))
            return coordinates
        }

        data class TargetSourceSetSelection(
            val name: String,
            val isKotlin: Boolean
        )

        fun targetSourceSetSelection(targetSourceSetName: String?): TargetSourceSetSelection? {
            if (targetSourceSetName == null || targetSourceSetName == BUILDSCRIPT_SOURCE_SET) return null
            val isKotlin = targetSourceSetName.startsWith("kotlin:")
            val name = if (isKotlin) targetSourceSetName.substringAfter("kotlin:") else targetSourceSetName
            return TargetSourceSetSelection(name, isKotlin)
        }
    }

    @get:Input
    abstract val checkUpdates: Property<Boolean>

    @get:Input
    abstract val onlyDirect: Property<Boolean>

    @get:Input
    abstract val downloadSources: Property<Boolean>

    @get:Input
    abstract val excludeBuildscript: Property<Boolean>

    @get:Input
    abstract val stableOnly: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val versionFilter: Property<String>

    @get:Input
    @get:Optional
    abstract val targetConfig: Property<String>

    @get:Input
    @get:Optional
    abstract val targetSourceSet: Property<String>

    @get:Input
    @get:Optional
    abstract val dependencyFilter: Property<String>

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val projectDisplayName: Property<String>

    @get:Input
    abstract val configurationsMetadata: MapProperty<String, McpConfigurationMetadata>

    @get:Input
    abstract val buildscriptConfigurationsMetadata: MapProperty<String, McpConfigurationMetadata>

    @get:Input
    abstract val configurationsGraphs: MapProperty<String, McpResolvedGraph>

    @get:Input
    abstract val buildscriptConfigurationsGraphs: MapProperty<String, McpResolvedGraph>

    @get:Input
    abstract val latestVersions: MapProperty<String, String>

    @get:Input
    abstract val updateCheckCandidates: ListProperty<String>

    @get:Input
    abstract val sourcesFiles: MapProperty<String, String>

    @get:Input
    abstract val repositories: ListProperty<McpRepositoryData>

    @get:Input
    abstract val buildscriptRepositories: ListProperty<McpRepositoryData>

    @get:Input
    abstract val sourceSets: MapProperty<String, McpSourceSetMetadata>

    @get:Input
    abstract val kotlinSourceSets: MapProperty<String, McpSourceSetMetadata>

    @get:Input
    @get:Optional
    abstract val jdkHome: Property<String>

    @get:Input
    @get:Optional
    abstract val jdkVersion: Property<String>

    init {
        group = "help"
        description = "Generates a structured dependency report for MCP."
        outputs.upToDateWhen { false }
    }

    fun extractConfigurationsMetadata(configs: Iterable<org.gradle.api.artifacts.Configuration>): Map<String, McpConfigurationMetadata> = buildMap {
        configs.forEach {
            val isInternal = it.name.endsWith(KMP_METADATA_CONFIGURATION_SUFFIX)
            val extendsFrom = ArrayList<String>()
            it.extendsFrom.forEach { extendsFrom.add(it.name) }
            val declaredDependencies = HashSet<Pair<String?, String>>()
            it.dependencies.forEach {
                if (it is org.gradle.api.artifacts.ProjectDependency) declaredDependencies.add("project" to it.path)
                else declaredDependencies.add((it.group ?: "") to it.name)
            }

            put(
                it.name, McpConfigurationMetadata(
                    it.name, it.description, it.isCanBeResolved, extendsFrom,
                    declaredDependencies,
                    isInternal
                )
            )
        }
    }

    fun filterRelevantConfigs(
        configs: Iterable<org.gradle.api.artifacts.Configuration>,
        targetConfigName: String?,
        targetSourceSetConfigurationNames: Set<String>? = null
    ): List<org.gradle.api.artifacts.Configuration> {
        return configs.filter {
            it.isCanBeResolved &&
                    (targetConfigName == it.name || (targetConfigName == null && !it.name.endsWith(KMP_METADATA_CONFIGURATION_SUFFIX))) &&
                    (targetSourceSetConfigurationNames == null || it.name in targetSourceSetConfigurationNames)
        }
    }

    fun projectConfigurationsForScope(
        configs: Iterable<org.gradle.api.artifacts.Configuration>,
        targetConfigName: String?,
        targetSourceSetName: String?,
        targetSourceSetConfigurationNames: Set<String>? = null
    ): List<org.gradle.api.artifacts.Configuration> {
        if (targetSourceSetName == BUILDSCRIPT_SOURCE_SET || targetConfigName?.startsWith(BUILDSCRIPT_PREFIX) == true) {
            return emptyList()
        }
        return filterRelevantConfigs(configs, targetConfigName, targetSourceSetConfigurationNames)
    }

    fun buildscriptConfigurationsForScope(
        configs: Iterable<org.gradle.api.artifacts.Configuration>,
        targetConfigName: String?,
        targetSourceSetName: String?,
        excludeBuildscript: Boolean
    ): List<org.gradle.api.artifacts.Configuration> {
        if (targetSourceSetName != null && targetSourceSetName != BUILDSCRIPT_SOURCE_SET) return emptyList()
        if (targetConfigName != null && !targetConfigName.startsWith(BUILDSCRIPT_PREFIX)) return emptyList()

        val targetBuildscriptConfig = targetConfigName?.removePrefix(BUILDSCRIPT_PREFIX)
        if (targetBuildscriptConfig != null) {
            return configs.filter { it.name == targetBuildscriptConfig }
        }

        return if (!excludeBuildscript || targetSourceSetName == BUILDSCRIPT_SOURCE_SET) configs.toList() else emptyList()
    }

    data class ModuleDependencyInfo(
        val id: org.gradle.api.artifacts.component.ModuleComponentIdentifier,
        val coordinateCandidates: Set<String>
    ) {
        fun matchesRequestedScope(
            dependencyFilterRegex: Regex?,
            onlyDirect: Boolean,
            directDependencies: Set<String>
        ): Boolean {
            val selectedCoordinate = "${id.group}:${id.module}"
            val matchesDependency = dependencyFilterRegex == null ||
                    McpDependencyReportTask.matchesAnyDependencyCoordinate(dependencyFilterRegex, coordinateCandidates)
            val matchesDepth = !onlyDirect || selectedCoordinate in directDependencies
            return matchesDependency && matchesDepth
        }
    }

    data class DependencyInfo(
        val directDependencies: Set<String>,
        val allUniqueModuleComponents: Set<ModuleDependencyInfo>
    )

    fun collectDependencyInfo(configs: List<org.gradle.api.artifacts.Configuration>, dependencyFilterRegex: Regex?): DependencyInfo {
        val directDependencies = mutableSetOf<String>()
        val moduleCandidates = linkedMapOf<org.gradle.api.artifacts.component.ModuleComponentIdentifier, MutableSet<String>>()

        configs.forEach { config ->
            config.dependencies.forEach {
                if (it is org.gradle.api.artifacts.ProjectDependency) {
                    directDependencies.add("project:${it.path}")
                } else if (it.group != null) {
                    directDependencies.add("${it.group}:${it.name}")
                }
            }
            try {
                config.incoming.resolutionResult.root.dependencies.forEach { dependency ->
                    val selected = (dependency as? org.gradle.api.artifacts.result.ResolvedDependencyResult)?.selected?.id
                    if (selected is org.gradle.api.artifacts.component.ModuleComponentIdentifier) {
                        directDependencies.add("${selected.group}:${selected.module}")
                    }
                }
                config.incoming.resolutionResult.allComponents.forEach { component ->
                    val id = component.id
                    if (id is org.gradle.api.artifacts.component.ModuleComponentIdentifier) {
                        val candidates = moduleCandidates.getOrPut(id) { linkedSetOf() }
                        if (dependencyFilterRegex != null) {
                            candidates.addAll(McpDependencyReportTask.dependencyCoordinateCandidates(id.group, id.module, id.version))
                            component.variants.forEach { variant ->
                                candidates.addAll(McpDependencyReportTask.dependencyCoordinateCandidates(id.group, id.module, id.version, variant.displayName))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("[gradle-mcp] [ERROR] Failed to collect components from ${config.name}: ${e.message}")
            }
        }
        return DependencyInfo(directDependencies, moduleCandidates.map { ModuleDependencyInfo(it.key, it.value) }.toSet())
    }

    fun sourceSetConfigurationNames(
        targetSourceSetName: String?,
        sourceSetsData: Map<String, McpSourceSetMetadata>,
        kotlinSourceSetsData: Map<String, McpSourceSetMetadata>
    ): Set<String>? {
        val selection = targetSourceSetSelection(targetSourceSetName) ?: return null
        val metadata = when {
            selection.isKotlin -> kotlinSourceSetsData[selection.name]
            else -> sourceSetsData[selection.name] ?: kotlinSourceSetsData[selection.name]
        }
        return metadata?.configurations?.toSet()
    }

    fun collectTargetSourceSetConfigurationNames(proj: org.gradle.api.Project, targetSourceSetName: String?): Set<String>? {
        val selection = targetSourceSetSelection(targetSourceSetName) ?: return null
        val baseNames = linkedSetOf<String>()

        if (!selection.isKotlin) {
            val sourceSetsContainer = proj.extensions.findByType(SourceSetContainer::class.java)
            val sourceSet = sourceSetsContainer?.findByName(selection.name)
            if (sourceSet != null) {
                sourceSetConfigurationNamesFor(proj, sourceSet).forEach { baseNames.add(it) }
            }
        }

        if (baseNames.isEmpty()) {
            try {
                val kotlinExtension = proj.extensions.findByName("kotlin")
                if (kotlinExtension != null) {
                    val getSourceSets = kotlinExtension.javaClass.getMethod("getSourceSets")
                    val sourceSets = getSourceSets.invoke(kotlinExtension) as Iterable<*>
                    sourceSets.firstOrNull { ss ->
                        ss != null && ss.javaClass.getMethod("getName").invoke(ss) == selection.name
                    }?.let { ss ->
                        kotlinSourceSetConfigurationNamesFor(proj, ss).forEach { baseNames.add(it) }
                    }
                }
            } catch (e: Exception) {
                // Ignore. Validation in task execution reports unknown source sets with the serialized metadata.
            }
        }

        return baseNames
    }

    fun configurationNameOrNull(sourceSet: Any, propertyName: String): String? {
        return try {
            val methodName = "get" + propertyName.replaceFirstChar { it.uppercaseChar() }
            sourceSet.javaClass.getMethod(methodName).invoke(sourceSet) as? String
        } catch (e: Exception) {
            null
        }
    }

    fun extendsFromAny(
        config: org.gradle.api.artifacts.Configuration,
        targetNames: Set<String>,
        visited: MutableSet<String> = mutableSetOf()
    ): Boolean {
        if (!visited.add(config.name)) return false
        if (config.extendsFrom.any { it.name in targetNames }) return true
        return config.extendsFrom.any { extendsFromAny(it, targetNames, visited) }
    }

    fun sourceSetConfigurationNamesFor(proj: org.gradle.api.Project, sourceSet: org.gradle.api.tasks.SourceSet): Set<String> {
        val configs = linkedSetOf<String>()
        listOf(
            sourceSet.apiConfigurationName,
            sourceSet.implementationConfigurationName,
            sourceSet.compileOnlyConfigurationName,
            sourceSet.runtimeOnlyConfigurationName,
            sourceSet.annotationProcessorConfigurationName,
            sourceSet.compileClasspathConfigurationName,
            sourceSet.runtimeClasspathConfigurationName
        ).filter { proj.configurations.findByName(it) != null }.forEach { configs.add(it) }
        val declarationConfigs = configs.toSet()
        val sourceSetName = sourceSet.name
        val isMainSourceSet = sourceSetName.equals("main", ignoreCase = true)
        val isTestSourceSet = sourceSetName.equals("test", ignoreCase = true)
        proj.configurations
            .filter { it.name !in configs && it.name.contains(sourceSetName, ignoreCase = true) && extendsFromAny(it, declarationConfigs) }
            .filter {
                when {
                    isMainSourceSet -> !it.name.contains("Test", ignoreCase = true)
                    isTestSourceSet -> it.name.contains("Test", ignoreCase = true)
                    else -> true
                }
            }
            .forEach { configs.add(it.name) }
        return configs
    }

    fun kotlinSourceSetConfigurationNamesFor(proj: org.gradle.api.Project, sourceSet: Any): Set<String> {
        val configs = linkedSetOf<String>()
        val propNames = listOf("apiConfigurationName", "implementationConfigurationName", "compileOnlyConfigurationName", "runtimeOnlyConfigurationName")
        propNames.forEach {
            val configName = configurationNameOrNull(sourceSet, it)
            if (configName != null && proj.configurations.findByName(configName) != null) {
                configs.add(configName)
            }
        }
        val declarationConfigs = configs.toSet()
        val sourceSetName = sourceSet.javaClass.getMethod("getName").invoke(sourceSet) as String
        val capitalizedSourceSetName = sourceSetName.replaceFirstChar { it.uppercaseChar() }
        val isMainSourceSet = sourceSetName.endsWith("Main", ignoreCase = true)
        val isTestSourceSet = sourceSetName.endsWith("Test", ignoreCase = true)
        listOf(
            "kapt$capitalizedSourceSetName",
            "ksp$capitalizedSourceSetName",
            "${sourceSetName}AnnotationProcessor"
        ).forEach { configName ->
            if (proj.configurations.findByName(configName) != null) {
                configs.add(configName)
            }
        }
        proj.configurations
            .filter {
                !it.name.endsWith(KMP_METADATA_CONFIGURATION_SUFFIX) &&
                    (extendsFromAny(it, declarationConfigs) || it.name in configs)
            }
            .filter {
                when {
                    isMainSourceSet -> !it.name.contains("Test", ignoreCase = true)
                    isTestSourceSet -> it.name.contains("Test", ignoreCase = true) || it.name.contains(sourceSetName, ignoreCase = true)
                    else -> it.name.contains(sourceSetName, ignoreCase = true)
                }
            }
            .forEach { configs.add(it.name) }
        return configs
    }

    @TaskAction
    fun generate() {
        val mcpRenderer = McpDependencyReportRenderer()
        mcpRenderer.configurationsMetadata = configurationsMetadata.get()
        mcpRenderer.buildscriptConfigurationsMetadata = buildscriptConfigurationsMetadata.get()

        val checkUpdates = this.checkUpdates.get()
        val onlyDirect = this.onlyDirect.get()
        val targetConfig = this.targetConfig.orNull
        val targetSourceSet = this.targetSourceSet.orNull?.trim(' ', '"', '\'')
        val dependencyFilterValue = normalizeDependencyFilter(this.dependencyFilter.orNull)
        val dependencyFilterRegex = dependencyFilterValue?.let(::Regex)

        val projectPath = this.projectPath.get()
        val projectDisplayName = this.projectDisplayName.get()
        val projectMetadata: Map<String, McpConfigurationMetadata> = configurationsMetadata.get()
        val buildscriptMetadata: Map<String, McpConfigurationMetadata> = buildscriptConfigurationsMetadata.get()
        val sourceSetsData: Map<String, McpSourceSetMetadata> = sourceSets.get()
        val kotlinSourceSetsData: Map<String, McpSourceSetMetadata> = kotlinSourceSets.get()
        val targetSourceSetConfigNames = sourceSetConfigurationNames(targetSourceSet, sourceSetsData, kotlinSourceSetsData)

        if (targetConfig != null) {
            val isBuildscript = targetConfig.startsWith(BUILDSCRIPT_PREFIX)
            val configName = if (isBuildscript) targetConfig.substringAfter(BUILDSCRIPT_PREFIX) else targetConfig
            val meta = if (isBuildscript) buildscriptMetadata else projectMetadata
            if (!meta.containsKey(configName)) {
                throw IllegalArgumentException("Configuration '$targetConfig' not found in project '$projectPath'")
            }
        }

        if (targetSourceSet != null && targetSourceSet != BUILDSCRIPT_SOURCE_SET) {
            if (targetSourceSetConfigNames == null) {
                throw IllegalArgumentException("SourceSet '$targetSourceSet' not found in project '$projectPath'")
            }
        }

        val projectConfigsToRender = projectMetadata.filter { (name, _) ->
            targetSourceSet != BUILDSCRIPT_SOURCE_SET &&
                    targetConfig?.startsWith(BUILDSCRIPT_PREFIX) != true &&
                    (targetConfig == null || targetConfig == name) &&
                    (targetSourceSetConfigNames == null || name in targetSourceSetConfigNames)
        }
        val buildscriptConfigsToRender: Map<String, McpConfigurationMetadata> = if (targetSourceSet == null || targetSourceSet == BUILDSCRIPT_SOURCE_SET) {
            buildscriptMetadata.filter { (name, _) ->
                when {
                    targetConfig == null -> !excludeBuildscript.get() || targetSourceSet == BUILDSCRIPT_SOURCE_SET
                    targetConfig.startsWith(BUILDSCRIPT_PREFIX) -> targetConfig == "${BUILDSCRIPT_PREFIX}$name"
                    else -> false
                }
            }
        } else emptyMap<String, McpConfigurationMetadata>()
        val allConfigs = projectConfigsToRender.keys + buildscriptConfigsToRender.keys.map { "${BUILDSCRIPT_PREFIX}$it" }

        var totalItems = allConfigs.size.toLong() * 2
        var completedItems = 0L

        fun reportProgress(category: String, detail: String) {
            completedItems++
            println("[gradle-mcp] [PROGRESS] [$category]: $completedItems/$totalItems: $detail")
        }

        fun updateTotal(newTotal: Long) {
            totalItems = newTotal
            println("[gradle-mcp] [PROGRESS] [Dependency Report]: TOTAL: $totalItems")
        }

        updateTotal(totalItems)

        val latestVersionsData: Map<String, String> = if (checkUpdates) latestVersions.get() else emptyMap()
        val sourcesFilesData: Map<String, String> = if (downloadSources.get()) sourcesFiles.get() else emptyMap()
        val projectRepositories = repositories.get()
        val buildscriptRepositoriesData = buildscriptRepositories.get()

        mcpRenderer.latestVersions = latestVersionsData
        mcpRenderer.sourcesFiles = sourcesFilesData
        mcpRenderer.checksEnabled = checkUpdates
        mcpRenderer.onlyDirect = onlyDirect
        mcpRenderer.dependencyFilterRegex = dependencyFilterRegex
        mcpRenderer.updatesCheckedDeps = if (checkUpdates) updateCheckCandidates.get().toSet() else emptySet()

        mcpRenderer.outputProject(projectPath, projectDisplayName)
        jdkHome.orNull?.takeIf { it.isNotBlank() }?.let {
            mcpRenderer.outputJdk(projectPath, it, jdkVersion.orNull?.takeIf { version -> version.isNotBlank() })
        }

        projectRepositories.forEach {
            mcpRenderer.outputRepository(projectPath, it.name, it.url)
        }
        buildscriptRepositoriesData.forEach {
            mcpRenderer.outputRepository(projectPath, "${BUILDSCRIPT_PREFIX}${it.name}", it.url)
        }

        val emittedSourceSets = linkedMapOf<String, McpSourceSetMetadata>()
        fun addSourceSetMetadata(name: String, metadata: McpSourceSetMetadata) {
            val existing = emittedSourceSets[name]
            emittedSourceSets[name] = if (existing == null) {
                metadata
            } else {
                McpSourceSetMetadata(
                    ArrayList((existing.configurations + metadata.configurations).distinct()),
                    existing.isJvm || metadata.isJvm
                )
            }
        }
        sourceSetsData.forEach { (name, metadata) -> addSourceSetMetadata(name, metadata) }
        val shouldOutputBuildscriptSourceSet = (targetSourceSet == null && !excludeBuildscript.get()) ||
                targetSourceSet == BUILDSCRIPT_SOURCE_SET ||
                targetConfig?.startsWith(BUILDSCRIPT_PREFIX) == true
        if (shouldOutputBuildscriptSourceSet) {
            addSourceSetMetadata(BUILDSCRIPT_SOURCE_SET, McpSourceSetMetadata(ArrayList(buildscriptMetadata.keys.map { "${BUILDSCRIPT_PREFIX}$it" }), true))
        }
        kotlinSourceSetsData.forEach { (name, metadata) -> addSourceSetMetadata("kotlin:$name", metadata) }
        emittedSourceSets.forEach { (name, metadata) ->
            mcpRenderer.outputSourceSet(projectPath, name, metadata.configurations, metadata.isJvm)
        }

        mcpRenderer.startProject(projectPath, projectDisplayName)

        val graphs: Map<String, McpResolvedGraph> = configurationsGraphs.get()
        for (entry in projectConfigsToRender.entries) {
            val name = entry.key
            val metadata = entry.value
            reportProgress("Resolving", name)
            reportProgress("Collecting", "[$projectPath] $name")
            mcpRenderer.startConfiguration(projectPath, name, metadata)
            val graph = graphs[name]
            if (graph != null) {
                mcpRenderer.render(projectPath, graph, name)
            } else if (!metadata.isCanBeResolved) {
                mcpRenderer.renderUnresolvable(projectPath, name, metadata)
            }
            mcpRenderer.completeConfiguration()
        }

        mcpRenderer.inBuildscript = true
        val bGraphs: Map<String, McpResolvedGraph> = buildscriptConfigurationsGraphs.get()
        for (entry in buildscriptConfigsToRender.entries) {
            val name = entry.key
            val metadata = entry.value
            val fullName = "${BUILDSCRIPT_PREFIX}$name"
            reportProgress("Resolving", fullName)
            reportProgress("Collecting", "[$projectPath] $fullName")
            mcpRenderer.startConfiguration(projectPath, name, metadata)
            val graph = bGraphs[name]
            if (graph != null) {
                mcpRenderer.render(projectPath, graph, fullName)
            } else if (!metadata.isCanBeResolved) {
                mcpRenderer.renderUnresolvable(projectPath, name, metadata)
            }
            mcpRenderer.completeConfiguration()
        }
        mcpRenderer.inBuildscript = false
        mcpRenderer.completeProject()

        if (dependencyFilterRegex != null && mcpRenderer.filterableDependencyCount == 0) {
            val scopeDescription = when {
                targetConfig != null -> "configuration '$targetConfig' in project '$projectPath'"
                targetSourceSet != null -> "source set '$targetSourceSet' in project '$projectPath'"
                else -> "project '$projectPath'"
            }
            mcpRenderer.outputNote(projectPath, "Dependency filter '$dependencyFilterValue' was applied, but $scopeDescription contains no dependency candidates.")
        }

        if (dependencyFilterRegex != null && mcpRenderer.filterableDependencyCount > 0 && mcpRenderer.emittedDependencyCount == 0) {
            val scopeDescription = when {
                targetConfig != null -> "configuration '$targetConfig' in project '$projectPath'"
                targetSourceSet != null -> "source set '$targetSourceSet' in project '$projectPath'"
                else -> "project '$projectPath'"
            }
            throw IllegalArgumentException("Dependency filter '$dependencyFilterValue' matched zero dependencies in $scopeDescription.")
        }
    }
}

class McpDependencyReportRenderer {
    var latestVersions: Map<String, String> = emptyMap()
    var sourcesFiles: Map<String, String> = emptyMap()
    var updatesCheckedDeps: Set<String> = emptySet()
    var checksEnabled: Boolean = false
    var onlyDirect: Boolean = false
    var dependencyFilterRegex: Regex? = null
    var inBuildscript: Boolean = false
    var configurationsMetadata: Map<String, McpConfigurationMetadata> = emptyMap()
    var buildscriptConfigurationsMetadata: Map<String, McpConfigurationMetadata> = emptyMap()
    var filterableDependencyCount: Int = 0
    var emittedDependencyCount: Int = 0

    private val hierarchyCache = mutableMapOf<String, List<String>>()
    private val declaringConfigCache = mutableMapOf<Pair<String, String>, String?>()

    private fun String.escape() = replace("|", "\\|")

    fun outputProject(path: String, displayName: String) {
        println("[gradle-mcp] [DEPENDENCIES] PROJECT | ${path.escape()} | ${displayName.escape()}")
    }

    fun outputRepository(path: String, name: String, url: String) {
        println("[gradle-mcp] [DEPENDENCIES] REPOSITORY | ${path.escape()} | ${name.escape()} | ${url.escape()}")
    }

    fun outputSourceSet(path: String, name: String, configurations: List<String>, isJvm: Boolean) {
        println("[gradle-mcp] [DEPENDENCIES] SOURCESET | ${path.escape()} | ${name.escape()} | ${configurations.joinToString(",").escape()} | $isJvm")
    }

    fun outputJdk(path: String, jdkHome: String, version: String?) {
        println("[gradle-mcp] [DEPENDENCIES] JDK | ${path.escape()} | ${jdkHome.escape()} | ${(version ?: "").escape()}")
    }

    fun outputNote(path: String, message: String) {
        println("[gradle-mcp] [DEPENDENCIES] NOTE | ${path.escape()} | ${message.escape()}")
    }

    fun startProject(path: String, displayName: String) {}

    fun completeProject() {}

    fun startConfiguration(projectPath: String, name: String, metadata: McpConfigurationMetadata) {
        val prefix = if (inBuildscript) McpDependencyReportTask.BUILDSCRIPT_PREFIX else ""
        val extendsFrom = metadata.extendsFrom.map { if (inBuildscript) "${McpDependencyReportTask.BUILDSCRIPT_PREFIX}$it" else it }.joinToString(",")
        println("[gradle-mcp] [DEPENDENCIES] CONFIGURATION | ${projectPath.escape()} | $prefix${name.escape()} | ${metadata.isCanBeResolved} | ${extendsFrom.escape()} | ${metadata.description?.escape() ?: ""} | ${metadata.isInternal}")
    }

    fun render(projectPath: String, graph: McpResolvedGraph, configName: String) {
        val root = graph.components[graph.rootId] ?: return
        val visited = mutableSetOf<String>()
        val rootVariant = graph.rootVariantName?.let { root.variants[it] } ?: root.variants.values.firstOrNull()
        if (rootVariant != null) {
            visited.add("${root.id} | ${rootVariant.name} | ${rootVariant.capabilities.joinToString(",")}")
        } else {
            visited.add(root.id)
        }
        graph.rootDependencies.forEach {
            val declaringName = findDeclaringConfiguration(configName, it.requested)
            val fullName = if (declaringName != null && inBuildscript && !declaringName.startsWith(McpDependencyReportTask.BUILDSCRIPT_PREFIX)) {
                "${McpDependencyReportTask.BUILDSCRIPT_PREFIX}$declaringName"
            } else declaringName
            if (declaringName == configName || (declaringName == null && !inBuildscript)) {
                renderDependency(projectPath, graph, it, 1, visited, null)
            } else {
                renderDependency(projectPath, graph, it, 1, visited, fullName)
            }
        }
    }

    fun renderUnresolvable(projectPath: String, configName: String, metadata: McpConfigurationMetadata) {
        metadata.declaredDependencies.forEach { (group, name) ->
            val filter = dependencyFilterRegex
            filterableDependencyCount++
            val coordinates = if (filter == null) emptyList() else McpDependencyReportTask.dependencyCoordinateCandidates(group, name, null, unresolved = true)
            if (filter != null && !McpDependencyReportTask.matchesAnyDependencyCoordinate(filter, coordinates)) return@forEach
            emittedDependencyCount++
            val markers = "*"
            val requested = if (group == "project") "project $name" else if (group != null) "$group:$name" else name
            println("[gradle-mcp] [DEPENDENCIES] DEP | ${projectPath.escape()} | $markers | UNRESOLVED:${requested.escape()} | ${(group ?: "").escape()} | ${name.escape()} | | UNRESOLVABLE CONFIGURATION | | true | | | | | false")
        }
    }

    private fun findDeclaringConfiguration(currentConfigName: String, requested: String): String? {
        val key = currentConfigName to requested
        return declaringConfigCache.getOrPut(key) {
            val metadataMap = if (inBuildscript) buildscriptConfigurationsMetadata else configurationsMetadata
            val unprefixedCurrent = currentConfigName.removePrefix(McpDependencyReportTask.BUILDSCRIPT_PREFIX)
            val hierarchy = hierarchyCache.getOrPut(currentConfigName) {
                val result = mutableListOf<String>()
                fun collect(name: String) {
                    if (name in result) return
                    result.add(name)
                    metadataMap[name]?.extendsFrom?.forEach { collect(it) }
                }
                collect(unprefixedCurrent)
                result
            }
            for (confName in hierarchy) {
                val metadata = metadataMap[confName] ?: continue
                val declaredDeps = metadata.declaredDependencies
                val match = declaredDeps.any { (group, name) ->
                    if (group == "project") {
                        val reqPath = requested.substringAfter("project ").trim()
                        val declPath = name.trim()
                        declPath == reqPath || (declPath == ":" && reqPath == ":") || declPath == reqPath.removePrefix(":") || (declPath.startsWith(":") && declPath.removePrefix(":") == reqPath)
                    } else if (group != null && group.isNotEmpty()) {
                        requested.startsWith("$group:$name")
                    } else {
                        requested.startsWith(name)
                    }
                }
                if (match) return@getOrPut if (inBuildscript) "${McpDependencyReportTask.BUILDSCRIPT_PREFIX}$confName" else confName
            }
            null
        }
    }

    private fun renderDependency(projectPath: String, graph: McpResolvedGraph, dep: McpResolvedDependency, depth: Int, visited: MutableSet<String>, fromConfiguration: String?) {
        val markers = "*".repeat(depth)
        if (dep.selectedId != null) {
            val selected = graph.components[dep.selectedId] ?: return
            val variant = dep.variantName?.let { selected.variants[it] } ?: selected.variants.values.firstOrNull()
            if (onlyDirect && depth > 1) return
            val visitKey = if (variant != null) "${selected.id} | ${variant.name} | ${variant.capabilities.joinToString(",")}" else selected.id
            val alreadyVisited = !visited.add(visitKey)
            filterableDependencyCount++
            val filter = dependencyFilterRegex
            val coordinateMatches = filter == null ||
                    McpDependencyReportTask.matchesAnyDependencyCoordinate(
                        filter,
                        McpDependencyReportTask.dependencyCoordinateCandidates(selected.group, selected.name, selected.version, variant?.name)
                    )

            if (!coordinateMatches) {
                if (!alreadyVisited) variant?.dependencies?.forEach { renderDependency(projectPath, graph, it, depth + 1, visited, null) }
                return
            }

            emittedDependencyCount++
            val latestVersion = latestVersions["${selected.group}:${selected.name}"] ?: ""
            val sourcesFile = sourcesFiles[selected.id] ?: ""
            val updatesChecked = isUpdateCheckComplete(selected.group, selected.name)
            val reason = selected.selectionReason.let { if (it.startsWith("requested: ")) it.removePrefix("requested: ") else it }
            if (variant != null) {
                println("[gradle-mcp] [DEPENDENCIES] DEP | ${projectPath.escape()} | $markers | ${selected.id.escape()} | ${selected.group.escape()} | ${selected.name.escape()} | ${selected.version.escape()} | ${reason.escape()} | ${latestVersion.escape()} | ${depth == 1} | ${variant.name.escape()} | ${variant.capabilities.joinToString(",").escape()} | ${(fromConfiguration ?: "").escape()} | ${sourcesFile.escape()} | $updatesChecked | ${(selected.commonComponentId ?: "").escape()}")
                if (!alreadyVisited) variant.dependencies.forEach { renderDependency(projectPath, graph, it, depth + 1, visited, null) }
            } else {
                println("[gradle-mcp] [DEPENDENCIES] DEP | ${projectPath.escape()} | $markers | ${selected.id.escape()} | ${selected.group.escape()} | ${selected.name.escape()} | ${selected.version.escape()} | ${reason.escape()} | ${latestVersion.escape()} | ${depth == 1} | | | ${(fromConfiguration ?: "").escape()} | ${sourcesFile.escape()} | $updatesChecked | ${(selected.commonComponentId ?: "").escape()}")
            }
        } else {
            if (onlyDirect && depth > 1) return
            val requested = dep.requested
            val parts = requested.split(":")
            val group = if (parts.size >= 2) parts[0] else null
            val name = if (parts.size >= 2) parts[1] else parts[0]
            val version = if (parts.size >= 3) parts[2] else ""
            filterableDependencyCount++
            val filter = dependencyFilterRegex
            val coordinates = if (filter == null) emptyList() else McpDependencyReportTask.dependencyCoordinateCandidates(group, name, null, unresolved = true)
            if (filter != null && !McpDependencyReportTask.matchesAnyDependencyCoordinate(filter, coordinates)) return
            emittedDependencyCount++
            println("[gradle-mcp] [DEPENDENCIES] DEP | ${projectPath.escape()} | $markers | UNRESOLVED:${requested.escape()} | ${(group ?: "").escape()} | ${name.escape()} | ${version.escape()} | ${(dep.failure ?: "unknown").escape()} | | ${depth == 1} | | | ${(fromConfiguration ?: "").escape()} | | false |")
        }
    }

    private fun isUpdateCheckComplete(group: String, name: String): Boolean {
        if (!checksEnabled) return false
        val coordinateKey = "$group:$name"
        return !updatesCheckedDeps.contains(coordinateKey) || latestVersions.containsKey(coordinateKey)
    }

    fun completeConfiguration() {}

    fun complete() {}
}

class McpGraphConverter(
    private val components: HashMap<String, McpResolvedComponent>,
    private val commonIdByPlatformId: Map<String, String>
) {
    companion object {
        fun convertGraph(configName: String, root: org.gradle.api.artifacts.result.ResolvedComponentResult, allComponents: Set<org.gradle.api.artifacts.result.ResolvedComponentResult>): McpResolvedGraph {
            val commonIdByPlatformId = buildMap {
                allComponents.forEach { comp ->
                    comp.variants.forEach { variant ->
                        // Variant points to platform artifact via "available-at" metadata
                        val external = (variant as? org.gradle.api.artifacts.result.ResolvedVariantResult)?.externalVariant?.orElse(null)
                        if (external != null) {
                            put(external.owner.toString(), comp.id.toString())
                        }
                    }
                }
            }

            val components = HashMap<String, McpResolvedComponent>()
            val converter = McpGraphConverter(components, commonIdByPlatformId)
            val rootId = converter.convert(root)
            val rootDependencies = ArrayList<McpResolvedDependency>()
            val seen = mutableSetOf<String>()
            root.dependencies.forEach {
                val requested = it.requested.toString()
                val variant = (it as? org.gradle.api.artifacts.result.ResolvedDependencyResult)?.resolvedVariant
                val key = "$requested | ${variant?.displayName}"
                if (!seen.add(key)) return@forEach

                if (it is org.gradle.api.artifacts.result.ResolvedDependencyResult) {
                    rootDependencies.add(McpResolvedDependency(requested, converter.convert(it.selected), variant?.displayName, null))
                } else if (it is org.gradle.api.artifacts.result.UnresolvedDependencyResult) {
                    rootDependencies.add(McpResolvedDependency(requested, null, null, it.failure.message))
                } else {
                    rootDependencies.add(McpResolvedDependency(requested, null, null, "unknown dependency type"))
                }
            }

            return McpResolvedGraph(rootId, null, rootDependencies, components)
        }
    }

    private fun convert(component: org.gradle.api.artifacts.result.ResolvedComponentResult): String {
        val id = component.id.toString()
        if (id in components) return id

        val (group, name, version) = when (val cid = component.id) {
            is org.gradle.api.artifacts.component.ModuleComponentIdentifier -> Triple(cid.group, cid.module, cid.version)
            is org.gradle.api.artifacts.component.ProjectComponentIdentifier -> Triple("project", cid.projectPath, "")
            else -> Triple("", component.id.displayName, "")
        }

        // Register placeholder to break infinite recursion
        val mcpVariants = HashMap<String, McpResolvedVariant>()
        val mcpComp = McpResolvedComponent(id, group, name, version, component.selectionReason.toString(), mcpVariants, commonIdByPlatformId[id])
        components[id] = mcpComp

        component.variants.forEach {
            val depsList = component.getDependenciesForVariant(it)

            val deps = ArrayList<McpResolvedDependency>()
            depsList.forEach {
                val requested = it.requested.toString()
                if (it is org.gradle.api.artifacts.result.ResolvedDependencyResult) {
                    deps.add(McpResolvedDependency(requested, convert(it.selected), it.resolvedVariant.displayName, null))
                } else if (it is org.gradle.api.artifacts.result.UnresolvedDependencyResult) {
                    deps.add(McpResolvedDependency(requested, null, null, it.failure.message))
                } else {
                    deps.add(McpResolvedDependency(requested, null, null, "unknown dependency type"))
                }
            }
            val capabilities = ArrayList<String>()
            it.capabilities.forEach { capabilities.add("${it.group}:${it.name}:${it.version}") }
            mcpVariants[it.displayName] = McpResolvedVariant(it.displayName, capabilities, deps)
        }

        return id
    }
}

fun mcpNoArgMethod(target: Any, name: String) =
    target.javaClass.methods.find { it.name == name && it.parameterCount == 0 }

fun mcpReflectName(target: Any): String? =
    mcpNoArgMethod(target, "getName")?.invoke(target) as? String

fun mcpCollectSourceSetConfigurationNames(proj: org.gradle.api.Project, sourceSet: Any): ArrayList<String> {
    val configs = ArrayList<String>()
    listOf(
        "apiConfigurationName",
        "implementationConfigurationName",
        "compileOnlyConfigurationName",
        "runtimeOnlyConfigurationName",
        "compileClasspathConfigurationName",
        "runtimeClasspathConfigurationName",
        "annotationProcessorConfigurationName"
    ).forEach { propertyName ->
        try {
            val methodName = "get" + propertyName.replaceFirstChar { it.uppercaseChar() }
            val configName = mcpNoArgMethod(sourceSet, methodName)?.invoke(sourceSet) as? String
            if (configName != null && proj.configurations.findByName(configName) != null) {
                configs.add(configName)
            }
        } catch (e: Exception) {
            proj.logger.debug("Could not inspect source-set configuration property $propertyName for ${proj.path}.", e)
        }
    }
    return configs
}

fun mcpAttributeValueName(value: Any?): String =
    when (value) {
        null -> ""
        else -> mcpReflectName(value) ?: value.toString()
    }

fun mcpConfigurationAttributesIndicateJvm(proj: org.gradle.api.Project, configName: String): Boolean {
    val config = proj.configurations.findByName(configName) ?: return false
    return config.attributes.keySet().any { attribute ->
        @Suppress("UNCHECKED_CAST")
        val typedAttribute = attribute as org.gradle.api.attributes.Attribute<Any>
        val valueName = mcpAttributeValueName(config.attributes.getAttribute(typedAttribute))
        when (attribute.name) {
            "org.jetbrains.kotlin.platform.type" -> valueName.equals("jvm", ignoreCase = true)
            "org.gradle.jvm.environment" -> true
            "org.gradle.usage" -> valueName in setOf("java-api", "java-runtime")
            else -> false
        }
    }
}

fun mcpConfigurationsIndicateJvm(proj: org.gradle.api.Project, configs: Iterable<String>): Boolean =
    configs.any { mcpConfigurationAttributesIndicateJvm(proj, it) }

fun mcpCollectJavaSourceSetMetadata(proj: org.gradle.api.Project): Map<String, McpSourceSetMetadata> {
    val sourceSetsContainer = proj.extensions.findByType(SourceSetContainer::class.java) ?: return emptyMap()
    return sourceSetsContainer.associate { ss ->
        val configs = mcpCollectSourceSetConfigurationNames(proj, ss)
        ss.name to McpSourceSetMetadata(configs, true)
    }
}

fun mcpKotlinTargetIsJvm(target: Any): Boolean {
    val platformType = mcpNoArgMethod(target, "getPlatformType")?.invoke(target)
    val platformName = mcpAttributeValueName(platformType)
    return platformName.equals("jvm", ignoreCase = true)
}

fun mcpAddKotlinSourceSetName(value: Any?, result: MutableSet<String>) {
    when (value) {
        null -> return
        is Iterable<*> -> value.forEach { if (it != null) mcpReflectName(it)?.let(result::add) }
        else -> mcpReflectName(value)?.let(result::add)
    }
}

fun mcpCollectJvmKotlinSourceSetNames(proj: org.gradle.api.Project, kotlinExtension: Any): Set<String> {
    return try {
        val targets = buildList {
            val targetCollection = mcpNoArgMethod(kotlinExtension, "getTargets")?.invoke(kotlinExtension)
            if (targetCollection is Iterable<*>) {
                targetCollection.filterNotNull().forEach(::add)
            }
            mcpNoArgMethod(kotlinExtension, "getTarget")?.invoke(kotlinExtension)?.let(::add)
        }
        buildSet {
            targets.filterNotNull().filter(::mcpKotlinTargetIsJvm).forEach { target ->
                val compilations = mcpNoArgMethod(target, "getCompilations")?.invoke(target) as? Iterable<*> ?: emptyList<Any>()
                compilations.filterNotNull().forEach { compilation ->
                    mcpAddKotlinSourceSetName(mcpNoArgMethod(compilation, "getKotlinSourceSets")?.invoke(compilation), this)
                    mcpAddKotlinSourceSetName(mcpNoArgMethod(compilation, "getDefaultSourceSet")?.invoke(compilation), this)
                }
            }
        }
    } catch (e: Exception) {
        proj.logger.warn("Failed to inspect Kotlin JVM target source sets for JDK source detection; marking Kotlin source sets as non-JVM.")
        proj.logger.debug("Kotlin JVM target source set inspection failed.", e)
        emptySet()
    }
}

fun mcpCollectKotlinSourceSetMetadata(proj: org.gradle.api.Project): Map<String, McpSourceSetMetadata> {
    val kotlinExtension = proj.extensions.findByName("kotlin") ?: return emptyMap()
    val jvmSourceSetNames = mcpCollectJvmKotlinSourceSetNames(proj, kotlinExtension)

    return try {
        val getSourceSets = mcpNoArgMethod(kotlinExtension, "getSourceSets")
        if (getSourceSets == null) {
            proj.logger.warn("Kotlin plugin detected for ${proj.path}, but getSourceSets was not found; marking Kotlin source sets as non-JVM")
            return emptyMap()
        }
        val sourceSets = getSourceSets.invoke(kotlinExtension) as? NamedDomainObjectContainer<*> ?: return emptyMap()
        sourceSets.filterNotNull().associate { ss ->
            val ssName = mcpReflectName(ss) ?: ""
            val configs = mcpCollectSourceSetConfigurationNames(proj, ss)
            val isJvm = ssName in jvmSourceSetNames ||
                    mcpConfigurationsIndicateJvm(proj, configs) ||
                    (proj.plugins.hasPlugin("org.jetbrains.kotlin.jvm") && ssName in setOf("main", "test"))
            ssName to McpSourceSetMetadata(configs, isJvm)
        }
    } catch (e: Exception) {
        proj.logger.warn("Failed to inspect Kotlin source sets for JDK source detection; marking Kotlin source sets as non-JVM.")
        proj.logger.debug("Kotlin source set inspection failed.", e)
        emptyMap()
    }
}

fun mcpHasJvmSourceSets(
    proj: org.gradle.api.Project,
    javaSourceSets: Map<String, McpSourceSetMetadata>,
    kotlinSourceSets: Map<String, McpSourceSetMetadata>
): Boolean =
    javaSourceSets.values.any { it.isJvm } ||
            kotlinSourceSets.values.any { it.isJvm } ||
            proj.buildscript.configurations.findByName("classpath") != null

fun mcpDetectJdk(
    proj: org.gradle.api.Project,
    javaSourceSets: Map<String, McpSourceSetMetadata>,
    kotlinSourceSets: Map<String, McpSourceSetMetadata>
): Pair<String, String?>? {
    if (!mcpHasJvmSourceSets(proj, javaSourceSets, kotlinSourceSets)) return null

    try {
        val javaExt = proj.extensions.findByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
        if (javaExt != null && (
                    javaExt.toolchain.languageVersion.isPresent ||
                            javaExt.toolchain.vendor.isPresent ||
                            javaExt.toolchain.implementation.isPresent
                    )
        ) {
            val service = proj.extensions.getByType(org.gradle.jvm.toolchain.JavaToolchainService::class.java)
            val launcher = service.launcherFor(javaExt.toolchain).get()
            val metadata = launcher.metadata
            return metadata.installationPath.asFile.absolutePath to metadata.languageVersion.asInt().toString()
        }
    } catch (e: Exception) {
        proj.logger.warn("Java toolchain JDK detection failed for ${proj.path}; falling back to Kotlin toolchain or daemon JDK.")
        proj.logger.debug("Java toolchain JDK detection failed for ${proj.path}.", e)
    }

    try {
        val kotlinExt = proj.extensions.findByName("kotlin")
        if (kotlinExt != null) {
            val jvmToolchainMethod = mcpNoArgMethod(kotlinExt, "getJvmToolchain")
            if (jvmToolchainMethod == null) {
                proj.logger.warn("Kotlin plugin detected for ${proj.path}, but getJvmToolchain was not found; falling back to daemon JDK.")
            }
            val jvmToolchain = jvmToolchainMethod?.invoke(kotlinExt)
            val languageVersionMethod = jvmToolchain?.let { mcpNoArgMethod(it, "getLanguageVersion") }
            if (jvmToolchain != null && languageVersionMethod == null) {
                proj.logger.warn("Kotlin JVM toolchain detected for ${proj.path}, but getLanguageVersion was not found; falling back to daemon JDK.")
            }
            val versionStr = languageVersionMethod?.invoke(jvmToolchain)?.toString()
            val version = versionStr?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
            if (version != null) {
                val service = proj.extensions.getByType(org.gradle.jvm.toolchain.JavaToolchainService::class.java)
                val spec = proj.objects.newInstance(org.gradle.jvm.toolchain.JavaToolchainSpec::class.java)
                spec.languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(version))
                val metadata = service.launcherFor(spec).get().metadata
                return metadata.installationPath.asFile.absolutePath to metadata.languageVersion.asInt().toString()
            }
        }
    } catch (e: Exception) {
        proj.logger.warn("Kotlin toolchain JDK detection failed for ${proj.path}; falling back to daemon JDK.")
        proj.logger.debug("Kotlin toolchain JDK detection failed for ${proj.path}.", e)
    }

    return try {
        val current = org.gradle.internal.jvm.Jvm.current()
        current.javaHome.absolutePath to current.javaVersion?.toString()
    } catch (e: Exception) {
        proj.logger.warn("Daemon JDK detection failed for ${proj.path}.")
        proj.logger.debug("Daemon JDK detection failed for ${proj.path}.", e)
        null
    }
}

allprojects {
    tasks.register("mcpDependencyReport", McpDependencyReportTask::class.java) {
        val proj = this.project

        fun mcpProp(name: String): String? = if (proj.hasProperty("mcp.$name")) proj.property("mcp.$name").toString() else System.getProperty("mcp.$name")

        val checkUpdatesBool = mcpProp("checkUpdates") == "true"
        val onlyDirectBool = mcpProp("onlyDirect") == "true"
        val downloadSourcesBool = mcpProp("downloadSources") == "true"
        val excludeBuildscriptBool = mcpProp("excludeBuildscript") == "true"
        val stableOnlyBool = mcpProp("stableOnly") == "true"
        val versionFilterValue = mcpProp("versionFilter")
        val targetConfigName = mcpProp("configuration")
        val targetSourceSetName = mcpProp("sourceSet")?.trim(' ', '"', '\'')
        val dependencyFilterValue = McpDependencyReportTask.normalizeDependencyFilter(mcpProp("dependencyFilter"))
        val dependencyFilterRegex = dependencyFilterValue?.let(::Regex)

        checkUpdates.set(checkUpdatesBool)
        onlyDirect.set(onlyDirectBool)
        downloadSources.set(downloadSourcesBool)
        excludeBuildscript.set(excludeBuildscriptBool)
        stableOnly.set(stableOnlyBool)

        versionFilterValue?.let { versionFilter.set(it) }
        targetConfigName?.let { targetConfig.set(it) }
        targetSourceSetName?.let { targetSourceSet.set(it) }
        dependencyFilterValue?.let { dependencyFilter.set(it) }

        projectPath.set(proj.path)
        projectDisplayName.set(proj.displayName)

        fun targetSourceSetConfigurationNames() = collectTargetSourceSetConfigurationNames(proj, targetSourceSetName)
        fun scopedProjectConfigurations() =
            projectConfigurationsForScope(proj.configurations, targetConfigName, targetSourceSetName, targetSourceSetConfigurationNames())

        fun scopedBuildscriptConfigurations() =
            buildscriptConfigurationsForScope(proj.buildscript.configurations, targetConfigName, targetSourceSetName, excludeBuildscriptBool)

        val scopedDependencyInfo = proj.provider {
            collectDependencyInfo(
                scopedProjectConfigurations() + scopedBuildscriptConfigurations(),
                dependencyFilterRegex
            )
        }
        val scopedUpdateCandidates = proj.provider {
            if (checkUpdatesBool) {
                val info = scopedDependencyInfo.get()
                info.allUniqueModuleComponents
                    .filter { it.matchesRequestedScope(dependencyFilterRegex, onlyDirectBool, info.directDependencies) }
            } else {
                emptyList()
            }
        }

        configurationsMetadata.set(proj.provider {
            extractConfigurationsMetadata(proj.configurations)
        })

        configurationsGraphs.set(proj.provider {
            buildMap {
                scopedProjectConfigurations().forEach {
                    if (it.isCanBeResolved) {
                        try {
                            val root = it.incoming.resolutionResult.root
                            val all = it.incoming.resolutionResult.allComponents
                            put(it.name, McpGraphConverter.convertGraph(it.name, root, all))
                        } catch (e: Exception) {
                            println("[gradle-mcp] [ERROR] Failed to resolve configuration '${it.name}': ${e.message}")
                        }
                    }
                }
            }
        })

        buildscriptConfigurationsMetadata.set(proj.provider {
            extractConfigurationsMetadata(proj.buildscript.configurations)
        })

        buildscriptConfigurationsGraphs.set(proj.provider {
            buildMap {
                scopedBuildscriptConfigurations().forEach {
                    if (it.isCanBeResolved) {
                        try {
                            val root = it.incoming.resolutionResult.root
                            val all = it.incoming.resolutionResult.allComponents
                            put(it.name, McpGraphConverter.convertGraph("${McpDependencyReportTask.BUILDSCRIPT_PREFIX}${it.name}", root, all))
                        } catch (e: Exception) {
                            println("[gradle-mcp] [ERROR] Failed to resolve buildscript configuration '${it.name}': ${e.message}")
                        }
                    }
                }
            }
        })

        latestVersions.set(proj.provider {
            if (checkUpdatesBool) {
                val updatesConfig = proj.configurations.detachedConfiguration()
                val nonStable = listOf("alpha", "beta", "rc", "m", "preview", "snapshot", "canary")
                val versionRegex = versionFilterValue?.let { Regex(it) }
                updatesConfig.resolutionStrategy.componentSelection {
                    all {
                        if (stableOnlyBool) {
                            val version = candidate.version
                            if (nonStable.any { version.contains(it, ignoreCase = true) }) {
                                reject("Version '$version' is not stable")
                            }
                        }
                        if (versionRegex != null) {
                            if (!candidate.version.matches(versionRegex)) {
                                reject("Version '${candidate.version}' does not match the provided filter regex: $versionFilterValue")
                            }
                        }
                    }
                }

                scopedUpdateCandidates.get().forEach {
                    val id = it.id
                    val dep = proj.dependencies.create("${id.group}:${id.module}:+") {
                        (this as org.gradle.api.artifacts.ExternalModuleDependency).isTransitive = false
                    }
                    updatesConfig.dependencies.add(dep)
                }
                updatesConfig.resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")

                buildMap {
                    try {
                        updatesConfig.incoming.resolutionResult.allComponents.forEach {
                            val id = it.id
                            if (id is org.gradle.api.artifacts.component.ModuleComponentIdentifier) {
                                put("${id.group}:${id.module}", id.version)
                            }
                        }
                    } catch (e: Exception) {
                        println("[gradle-mcp] [ERROR] Failed to collect latest versions: ${e.message}")
                    }
                }
            } else {
                emptyMap()
            }
        })

        updateCheckCandidates.set(proj.provider {
            if (checkUpdatesBool) {
                scopedUpdateCandidates.get().map {
                    val id = it.id
                    "${id.group}:${id.module}"
                }
            } else {
                emptyList()
            }
        })

        sourcesFiles.set(proj.provider {
            if (downloadSourcesBool) {
                val info = scopedDependencyInfo.get()
                val sourceCandidates = info.allUniqueModuleComponents
                    .filter { it.matchesRequestedScope(dependencyFilterRegex, onlyDirectBool, info.directDependencies) }
                val sourcesConfig = proj.configurations.detachedConfiguration()
                sourceCandidates.forEach {
                    val id = it.id
                    val dep = proj.dependencies.create("${id.group}:${id.module}:${id.version}:sources@jar") {
                        (this as org.gradle.api.artifacts.ExternalModuleDependency).isTransitive = false
                    }
                    sourcesConfig.dependencies.add(dep)
                }

                buildMap {
                    try {
                        sourcesConfig.resolvedConfiguration.lenientConfiguration.artifacts.forEach {
                            val id = it.moduleVersion.id
                            val path = it.file.absolutePath
                            put("${id.group}:${id.name}:${id.version}", path)
                        }
                    } catch (e: Exception) {
                        println("[gradle-mcp] [ERROR] Failed to resolve sources configuration: ${e.message}")
                    }
                }
            } else {
                emptyMap()
            }
        })

        repositories.set(proj.provider {
            proj.repositories.mapNotNull {
                if (it is org.gradle.api.artifacts.repositories.MavenArtifactRepository) McpRepositoryData(it.name, it.url?.toString() ?: "unknown")
                else if (it is org.gradle.api.artifacts.repositories.IvyArtifactRepository) McpRepositoryData(it.name, it.url?.toString() ?: "unknown")
                else null
            }
        })
        buildscriptRepositories.set(proj.provider {
            proj.buildscript.repositories.mapNotNull {
                if (it is org.gradle.api.artifacts.repositories.MavenArtifactRepository) McpRepositoryData(it.name, it.url?.toString() ?: "unknown")
                else if (it is org.gradle.api.artifacts.repositories.IvyArtifactRepository) McpRepositoryData(it.name, it.url?.toString() ?: "unknown")
                else null
            }
        })

        val javaSourceSetMetadata = proj.provider {
            mcpCollectJavaSourceSetMetadata(proj)
        }
        val kotlinSourceSetMetadata = proj.provider {
            mcpCollectKotlinSourceSetMetadata(proj)
        }

        sourceSets.set(javaSourceSetMetadata)
        kotlinSourceSets.set(kotlinSourceSetMetadata)

        val detectedJdk = proj.provider { mcpDetectJdk(proj, javaSourceSetMetadata.get(), kotlinSourceSetMetadata.get()) }
        jdkHome.set(detectedJdk.map { it?.first ?: "" })
        jdkVersion.set(detectedJdk.map { it?.second ?: "" })
    }
}
