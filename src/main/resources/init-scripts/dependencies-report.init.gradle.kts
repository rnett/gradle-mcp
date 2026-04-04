import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.logging.text.StyledTextOutput

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

abstract class McpDependencyReportTask : DefaultTask() {
    companion object {
        const val BUILDSCRIPT_PREFIX = "buildscript:"
        const val BUILDSCRIPT_SOURCE_SET = "__mcp_buildscript__"
        const val KMP_METADATA_CONFIGURATION_SUFFIX = "DependenciesMetadata"
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
    abstract val sourcesFiles: MapProperty<String, String>

    @get:Input
    abstract val repositories: ListProperty<McpRepositoryData>

    @get:Input
    abstract val buildscriptRepositories: ListProperty<McpRepositoryData>

    @get:Input
    abstract val sourceSets: MapProperty<String, ArrayList<String>>

    @get:Input
    abstract val kotlinSourceSets: MapProperty<String, ArrayList<String>>

    init {
        group = "help"
        description = "Generates a structured dependency report for MCP."
    }

    fun extractConfigurationsMetadata(configs: Iterable<org.gradle.api.artifacts.Configuration>): Map<String, McpConfigurationMetadata> = buildMap {
        configs.forEach {
            val isInternal = it.name.endsWith(McpDependencyReportTask.KMP_METADATA_CONFIGURATION_SUFFIX)
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
        targetConfigName: String?
    ): List<org.gradle.api.artifacts.Configuration> {
        return configs.filter {
            it.isCanBeResolved && (targetConfigName == it.name || (targetConfigName == null && !it.name.endsWith(KMP_METADATA_CONFIGURATION_SUFFIX)))
        }
    }

    data class DependencyInfo(
        val directDependencies: Set<String>,
        val allUniqueModuleComponents: Set<org.gradle.api.artifacts.component.ModuleComponentIdentifier>
    )

    fun collectDependencyInfo(configs: List<org.gradle.api.artifacts.Configuration>): DependencyInfo {
        val directDependencies = mutableSetOf<String>()
        val allUniqueModuleComponents = mutableSetOf<org.gradle.api.artifacts.component.ModuleComponentIdentifier>()

        configs.forEach { config ->
            config.dependencies.forEach {
                if (it is org.gradle.api.artifacts.ProjectDependency) {
                    directDependencies.add("project:${it.path}")
                } else if (it.group != null) {
                    directDependencies.add("${it.group}:${it.name}")
                }
            }
            try {
                config.incoming.resolutionResult.allComponents.forEach { component ->
                    val id = component.id
                    if (id is org.gradle.api.artifacts.component.ModuleComponentIdentifier) {
                        allUniqueModuleComponents.add(id)
                    }
                }
            } catch (e: Exception) {
                println("[gradle-mcp] [ERROR] Failed to collect components from ${config.name}: ${e.message}")
            }
        }
        return DependencyInfo(directDependencies, allUniqueModuleComponents)
    }

    @TaskAction
    fun generate() {
        val mcpRenderer = McpDependencyReportRenderer()
        mcpRenderer.setOutput(services.get(org.gradle.internal.logging.text.StyledTextOutputFactory::class.java).create(McpDependencyReportTask::class.java))

        mcpRenderer.configurationsMetadata = configurationsMetadata.get()
        mcpRenderer.buildscriptConfigurationsMetadata = buildscriptConfigurationsMetadata.get()

        val checkUpdates = this.checkUpdates.get()
        val onlyDirect = this.onlyDirect.get()
        val targetConfig = this.targetConfig.orNull
        val targetSourceSet = this.targetSourceSet.orNull?.trim { it == ' ' || it == '"' || it == '\'' }
        val dependencyFilter = this.dependencyFilter.orNull

        val projectPath = this.projectPath.get()
        val projectDisplayName = this.projectDisplayName.get()

        val isTargetingBuildscript = targetSourceSet == BUILDSCRIPT_SOURCE_SET || (targetConfig != null && targetConfig.startsWith(BUILDSCRIPT_PREFIX))
        if (targetConfig != null) {
            val isBuildscript = targetConfig.startsWith(BUILDSCRIPT_PREFIX)
            val configName = if (isBuildscript) targetConfig.substringAfter(BUILDSCRIPT_PREFIX) else targetConfig
            val meta = if (isBuildscript) buildscriptConfigurationsMetadata.get() else configurationsMetadata.get()
            if (!meta.containsKey(configName)) {
                throw IllegalArgumentException("Configuration '$targetConfig' not found in project '$projectPath'")
            }
        }

        if (targetSourceSet != null && targetSourceSet != BUILDSCRIPT_SOURCE_SET) {
            val ss = if (targetSourceSet.startsWith("kotlin:")) targetSourceSet.substringAfter("kotlin:") else targetSourceSet
            if (!sourceSets.get().containsKey(ss) && !kotlinSourceSets.get().containsKey(ss)) {
                throw IllegalArgumentException("SourceSet '$targetSourceSet' not found in project '$projectPath'")
            }
        }

        val projectConfigs = configurationsMetadata.get()
        val buildscriptConfigs = if (!excludeBuildscript.get() || isTargetingBuildscript) {
            buildscriptConfigurationsMetadata.get()
        } else emptyMap()

        val allConfigs = projectConfigs.keys + buildscriptConfigs.keys.map { "$BUILDSCRIPT_PREFIX$it" }

        // Progress Tracking
        var totalItems = allConfigs.size.toLong() * 2 // RESOLUTION + COLLECTING
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

        mcpRenderer.latestVersions = latestVersions.get()
        mcpRenderer.sourcesFiles = sourcesFiles.get()
        mcpRenderer.checksEnabled = checkUpdates
        mcpRenderer.onlyDirect = onlyDirect
        mcpRenderer.dependencyFilter = dependencyFilter
        mcpRenderer.updatesCheckedDeps = if (checkUpdates) latestVersions.get().keys else emptySet()

        mcpRenderer.outputProject(projectPath, projectDisplayName)

        repositories.get().forEach {
            mcpRenderer.outputRepository(projectPath, it.name, it.url)
        }
        buildscriptRepositories.get().forEach {
            mcpRenderer.outputRepository(projectPath, "$BUILDSCRIPT_PREFIX${it.name}", it.url)
        }

        sourceSets.get().forEach { (name, configs) ->
            mcpRenderer.outputSourceSet(projectPath, name, configs)
        }
        if (!excludeBuildscript.get() || isTargetingBuildscript) {
            mcpRenderer.outputSourceSet(projectPath, BUILDSCRIPT_SOURCE_SET, buildscriptConfigurationsMetadata.get().keys.map { "$BUILDSCRIPT_PREFIX$it" })
        }
        kotlinSourceSets.get().forEach { (name, configs) ->
            mcpRenderer.outputSourceSet(projectPath, "kotlin:$name", configs)
        }

        // Phase 4: Collecting (Processing)
        mcpRenderer.startProject(projectPath, projectDisplayName)

        val graphs = configurationsGraphs.get()
        configurationsMetadata.get().forEach { (name, metadata) ->
            if (targetConfig == null || targetConfig == name) {
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
        }

        mcpRenderer.inBuildscript = true
        val bGraphs = buildscriptConfigurationsGraphs.get()
        buildscriptConfigs.forEach { (name, metadata) ->
            val fullName = "$BUILDSCRIPT_PREFIX$name"
            if (targetConfig == null || targetConfig == fullName) {
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
        }
        mcpRenderer.inBuildscript = false
        mcpRenderer.completeProject()
    }
}

class McpDependencyReportRenderer {
    private var output: StyledTextOutput? = null
    var latestVersions: Map<String, String> = emptyMap()
    var sourcesFiles: Map<String, String> = emptyMap()

    // Set of "group:module" coordinates that were targeted for update checking
    var updatesCheckedDeps: Set<String> = emptySet()

    // Whether update checking was enabled at all for this run.
    var checksEnabled: Boolean = false
    var onlyDirect: Boolean = false
    var dependencyFilter: String? = null
    var inBuildscript: Boolean = false
    var configurationsMetadata: Map<String, McpConfigurationMetadata> = emptyMap()
    var buildscriptConfigurationsMetadata: Map<String, McpConfigurationMetadata> = emptyMap()

    private var currentConfigurationName: String? = null

    // Performance Caches
    private val hierarchyCache = mutableMapOf<String, List<String>>()
    private val declaringConfigCache = mutableMapOf<Pair<String, String>, String?>()

    fun setOutput(textOutput: StyledTextOutput) {
        this.output = textOutput
    }

    private fun String.escape() = this.replace("|", "\\|")

    fun outputProject(path: String, displayName: String) {
        output?.println("PROJECT: ${path.escape()} | ${displayName.escape()}")
    }

    fun outputRepository(path: String, name: String, url: String) {
        output?.println("REPOSITORY: ${path.escape()} | ${name.escape()} | ${url.escape()}")
    }

    fun outputSourceSet(path: String, name: String, configurations: List<String>) {
        output?.println("SOURCESET: ${path.escape()} | ${name.escape()} | ${configurations.joinToString(",").escape()}")
    }

    fun startProject(path: String, displayName: String) {}

    fun completeProject() {}

    fun startConfiguration(projectPath: String, name: String, metadata: McpConfigurationMetadata) {
        this.currentConfigurationName = name
        val prefix = if (inBuildscript) McpDependencyReportTask.BUILDSCRIPT_PREFIX else ""
        val extendsFrom = metadata.extendsFrom.map { if (inBuildscript) "${McpDependencyReportTask.BUILDSCRIPT_PREFIX}$it" else it }.joinToString(",")
        output?.println("CONFIGURATION: ${projectPath.escape()} | $prefix${name.escape()} | ${metadata.isCanBeResolved} | ${extendsFrom.escape()} | ${metadata.description?.escape() ?: ""} | ${metadata.isInternal}")
    }

    fun render(projectPath: String, graph: McpResolvedGraph, configName: String) {
        val root = graph.components[graph.rootId] ?: return
        val visited = mutableSetOf<String>()

        // Root itself is visited
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
            if (dependencyFilter != null && !("${group ?: ""}:${name}").contains(dependencyFilter!!)) return@forEach

            val markers = "*"
            val requested = if (group == "project") "project $name" else if (group != null) "$group:$name" else name
            output?.println("DEP: ${projectPath.escape()} | $markers | UNRESOLVED:${requested.escape()} | ${(group ?: "").escape()} | ${name.escape()} | | UNRESOLVABLE CONFIGURATION | | true | | | | | false")
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

            // Filtering
            if (dependencyFilter != null && depth == 1) {
                if (!selected.id.contains(dependencyFilter!!) && !dep.requested.contains(dependencyFilter!!)) return
            }

            val variant = dep.variantName?.let { selected.variants[it] } ?: selected.variants.values.firstOrNull()

            val visitKey = if (variant != null) "${selected.id} | ${variant.name} | ${variant.capabilities.joinToString(",")}" else selected.id
            val alreadyVisited = !visited.add(visitKey)

            if (onlyDirect && depth > 1) return

            val latestVersion = latestVersions["${selected.group}:${selected.name}"] ?: ""
            val sourcesFile = sourcesFiles[selected.id] ?: ""
            val updatesChecked = isUpdateCheckComplete(selected.group, selected.name)

            val reason = selected.selectionReason.let {
                if (it.startsWith("requested: ")) it.removePrefix("requested: ")
                else it
            }

            if (variant != null) {
                output?.println(
                    "DEP: ${projectPath.escape()} | $markers | ${selected.id.escape()} | ${selected.group.escape()} | ${selected.name.escape()} | ${selected.version.escape()} | ${reason.escape()} | ${latestVersion.escape()} | ${depth == 1} | ${variant.name.escape()} | ${
                        variant.capabilities.joinToString(
                            ","
                        ).escape()
                    } | ${(fromConfiguration ?: "").escape()} | ${sourcesFile.escape()} | $updatesChecked | ${(selected.commonComponentId ?: "").escape()}"
                )

                if (!alreadyVisited) {
                    variant.dependencies.forEach {
                        renderDependency(projectPath, graph, it, depth + 1, visited, null)
                    }
                }
            } else {
                output?.println("DEP: ${projectPath.escape()} | $markers | ${selected.id.escape()} | ${selected.group.escape()} | ${selected.name.escape()} | ${selected.version.escape()} | ${reason.escape()} | ${latestVersion.escape()} | ${depth == 1} | | | ${(fromConfiguration ?: "").escape()} | ${sourcesFile.escape()} | $updatesChecked | ${(selected.commonComponentId ?: "").escape()}")
            }
        } else {
            // Unresolved
            if (dependencyFilter != null && depth == 1 && !dep.requested.contains(dependencyFilter!!)) return

            val requested = dep.requested
            val parts = requested.split(":")
            val group = if (parts.size >= 2) parts[0] else ""
            val name = if (parts.size >= 2) parts[1] else parts[0]
            val version = if (parts.size >= 3) parts[2] else ""

            output?.println("DEP: ${projectPath.escape()} | $markers | UNRESOLVED:${requested.escape()} | ${group.escape()} | ${name.escape()} | ${version.escape()} | ${(dep.failure ?: "unknown").escape()} | | ${depth == 1} | | | ${(fromConfiguration ?: "").escape()} | | false |")
        }
    }

    private fun isUpdateCheckComplete(group: String, name: String): Boolean {
        if (!checksEnabled) return false
        val coordinateKey = "$group:$name"
        return !updatesCheckedDeps.contains(coordinateKey) || latestVersions.containsKey(coordinateKey)
    }

    fun completeConfiguration() {
        this.currentConfigurationName = null
    }

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

allprojects {
    tasks.register("mcpDependencyReport", McpDependencyReportTask::class.java) {
        val proj = this.project

        fun mcpProp(name: String): String? = if (proj.hasProperty("mcp.$name")) proj.property("mcp.$name").toString() else System.getProperty("mcp.$name")

        checkUpdates.set(mcpProp("checkUpdates") == "true")
        onlyDirect.set(mcpProp("onlyDirect") == "true")
        downloadSources.set(mcpProp("downloadSources") == "true")
        excludeBuildscript.set(mcpProp("excludeBuildscript") == "true")
        stableOnly.set(mcpProp("stableOnly") == "true")

        mcpProp("versionFilter")?.let { versionFilter.set(it) }
        mcpProp("configuration")?.let { targetConfig.set(it) }
        mcpProp("sourceSet")?.let { targetSourceSet.set(it) }
        mcpProp("dependencyFilter")?.let { dependencyFilter.set(it) }

        projectPath.set(proj.path)
        projectDisplayName.set(proj.displayName)

        val targetConfigName = mcpProp("configuration")
        val targetSourceSetName = mcpProp("sourceSet")?.trim { it == ' ' || it == '"' || it == '\'' }
        val excludeBuildscriptBool = mcpProp("excludeBuildscript") == "true"

        val isTargetingBuildscript = targetSourceSetName == McpDependencyReportTask.BUILDSCRIPT_SOURCE_SET || (targetConfigName != null && targetConfigName.startsWith(McpDependencyReportTask.BUILDSCRIPT_PREFIX))

        configurationsMetadata.set(proj.provider {
            extractConfigurationsMetadata(proj.configurations)
        })

        configurationsGraphs.set(proj.provider {
            buildMap {
                proj.configurations.filter { targetConfigName == null || targetConfigName == it.name }.forEach {
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
                val buildscriptConfigs = if (!excludeBuildscriptBool || isTargetingBuildscript) {
                    proj.buildscript.configurations.filter {
                        targetConfigName == null || targetConfigName == "${McpDependencyReportTask.BUILDSCRIPT_PREFIX}${it.name}"
                    }
                } else {
                    emptyList()
                }
                buildscriptConfigs.forEach {
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
            val projectConfigs = if (isTargetingBuildscript) emptyList() else filterRelevantConfigs(proj.configurations, targetConfigName)
            val buildscriptConfigs = if (!excludeBuildscriptBool || isTargetingBuildscript) {
                val prefix = McpDependencyReportTask.BUILDSCRIPT_PREFIX
                proj.buildscript.configurations.filter {
                    targetConfigName == null || targetConfigName == "$prefix${it.name}"
                }
            } else emptyList()

            val info = collectDependencyInfo(projectConfigs + buildscriptConfigs)

            if (mcpProp("checkUpdates") == "true" && info.allUniqueModuleComponents.isNotEmpty()) {
                val versionFilterStr = mcpProp("versionFilter")
                val onlyDirectBool = mcpProp("onlyDirect") == "true"
                val isStableOnly = mcpProp("stableOnly") == "true"

                val updatesConfig = proj.configurations.detachedConfiguration()
                updatesConfig.resolutionStrategy.componentSelection {
                    all {
                        if (isStableOnly) {
                            val version = candidate.version
                            val nonStable = listOf("alpha", "beta", "rc", "m", "preview", "snapshot", "canary")
                            if (nonStable.any { version.contains(it, ignoreCase = true) }) {
                                reject("Version '$version' is not stable")
                            }
                        }
                        if (versionFilterStr != null) {
                            val versionRegex = Regex(versionFilterStr)
                            if (!candidate.version.matches(versionRegex)) {
                                reject("Version '${candidate.version}' does not match the provided filter regex: $versionFilterStr")
                            }
                        }
                    }
                }

                info.allUniqueModuleComponents.forEach {
                    if (!onlyDirectBool || "${it.group}:${it.module}" in info.directDependencies) {
                        val dep = proj.dependencies.create("${it.group}:${it.module}:+") {
                            (this as org.gradle.api.artifacts.ExternalModuleDependency).isTransitive = false
                        }
                        updatesConfig.dependencies.add(dep)
                    }
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

        sourcesFiles.set(proj.provider {
            val projectConfigs = if (isTargetingBuildscript) emptyList() else filterRelevantConfigs(proj.configurations, targetConfigName)
            val buildscriptConfigs = if (!excludeBuildscriptBool || isTargetingBuildscript) {
                val prefix = McpDependencyReportTask.BUILDSCRIPT_PREFIX
                proj.buildscript.configurations.filter {
                    targetConfigName == null || targetConfigName == "$prefix${it.name}"
                }
            } else emptyList()

            val info = collectDependencyInfo(projectConfigs + buildscriptConfigs)

            val val_downloadSources = mcpProp("downloadSources") == "true"
            if (val_downloadSources && info.allUniqueModuleComponents.isNotEmpty()) {
                val onlyDirectBool = mcpProp("onlyDirect") == "true"
                val sourcesConfig = proj.configurations.detachedConfiguration()
                info.allUniqueModuleComponents.forEach {
                    if (!onlyDirectBool || "${it.group}:${it.module}" in info.directDependencies) {
                        val dep = proj.dependencies.create("${it.group}:${it.module}:${it.version}:sources@jar") {
                            (this as org.gradle.api.artifacts.ExternalModuleDependency).isTransitive = false
                        }
                        sourcesConfig.dependencies.add(dep)
                    }
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

        sourceSets.set(proj.provider {
            val sourceSetsContainer = proj.extensions.findByType(SourceSetContainer::class.java)
            sourceSetsContainer?.associate { ss ->
                val configs = ArrayList<String>()
                listOfNotNull(
                    ss.apiConfigurationName.takeIf { proj.configurations.findByName(it) != null },
                    ss.implementationConfigurationName.takeIf { proj.configurations.findByName(it) != null },
                    ss.compileOnlyConfigurationName.takeIf { proj.configurations.findByName(it) != null },
                    ss.runtimeOnlyConfigurationName.takeIf { proj.configurations.findByName(it) != null }
                ).forEach { configs.add(it) }
                ss.name to configs
            } ?: emptyMap()
        })

        kotlinSourceSets.set(proj.provider {
            val kotlinData = mutableMapOf<String, ArrayList<String>>()
            try {
                val kotlinExtension = proj.extensions.findByName("kotlin")
                if (kotlinExtension != null) {
                    val getSourceSets = kotlinExtension.javaClass.getMethod("getSourceSets")
                    val ssContainer = getSourceSets.invoke(kotlinExtension) as NamedDomainObjectContainer<*>
                    ssContainer.forEach { ss ->
                        val getName = ss.javaClass.getMethod("getName")
                        val ssName = getName.invoke(ss) as String
                        val configs = ArrayList<String>()
                        val propNames = listOf("apiConfigurationName", "implementationConfigurationName", "compileOnlyConfigurationName", "runtimeOnlyConfigurationName")
                        propNames.forEach {
                            try {
                                val methodName = "get" + it.replaceFirstChar { it.uppercaseChar() }
                                val configName = ss.javaClass.getMethod(methodName).invoke(ss) as? String
                                if (configName != null && proj.configurations.findByName(configName) != null) {
                                    configs.add(configName)
                                }
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                        kotlinData[ssName] = configs
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            kotlinData
        })
    }
}
