import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.diagnostics.AbstractDependencyReportTask
import org.gradle.api.tasks.diagnostics.internal.ConfigurationDetails
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.internal.logging.text.StyledTextOutput
import java.io.File

abstract class McpDependencyReportTask : AbstractDependencyReportTask() {
    init {
        group = "help"
        description = "Generates a structured dependency report for MCP."
        val mcpRenderer = McpDependencyReportRenderer()
        setRenderer(mcpRenderer)

        notCompatibleWithConfigurationCache("Uses Project object during execution")
    }

    override fun generateReportFor(project: ProjectDetails, model: AbstractDependencyReportTask.DependencyReportModel) {
        val mcpRenderer = renderer as McpDependencyReportRenderer
        val realProject = getProject()
        mcpRenderer.gradleProject = realProject

        val checkUpdates = realProject.hasProperty("mcp.checkUpdates") && realProject.property("mcp.checkUpdates").toString() == "true"
        val onlyDirect = realProject.hasProperty("mcp.onlyDirect") && realProject.property("mcp.onlyDirect").toString() == "true"
        val downloadSources = realProject.hasProperty("mcp.downloadSources") && realProject.property("mcp.downloadSources").toString() == "true"
        val stableOnly = realProject.hasProperty("mcp.stableOnly") && realProject.property("mcp.stableOnly").toString() == "true"
        val versionFilter = if (realProject.hasProperty("mcp.versionFilter")) realProject.property("mcp.versionFilter").toString() else null
        val targetConfig = if (realProject.hasProperty("mcp.configuration")) realProject.property("mcp.configuration").toString() else null
        val targetSourceSet = if (realProject.hasProperty("mcp.sourceSet")) realProject.property("mcp.sourceSet").toString() else null
        val dependencyFilter = if (realProject.hasProperty("mcp.dependencyFilter")) realProject.property("mcp.dependencyFilter").toString() else null
        val filterParts = dependencyFilter?.split(":", limit = 4)

        fun matchesFilter(id: ModuleComponentIdentifier): Boolean {
            if (filterParts == null) return true
            val group = id.group
            val module = id.module
            val version = id.version

            return when (filterParts.size) {
                1 -> group == filterParts[0]
                2 -> group == filterParts[0] && module == filterParts[1]
                3 -> group == filterParts[0] && module == filterParts[1] && version == filterParts[2]
                else -> group == filterParts[0] && module == filterParts[1] && version == filterParts[2]
                // Note: variant matching is harder here as we don't have it in ModuleComponentIdentifier easily,
                // so we fallback to 3-part matching (G:A:V) for filters with 4+ parts.
                // The SourcesService will do the final filtering if variants are specified.
            }
        }

        if (targetConfig != null) {
            val isBuildscript = targetConfig.startsWith("buildscript:")
            val configName = if (isBuildscript) targetConfig.substringAfter("buildscript:") else targetConfig
            val exists = if (isBuildscript) {
                realProject.buildscript.configurations.findByName(configName) != null
            } else {
                realProject.configurations.findByName(configName) != null
            }
            if (!exists) {
                throw IllegalArgumentException("Configuration '$targetConfig' not found in project '${realProject.path}'")
            }
        }

        if (targetSourceSet != null) {
            val sourceSets = realProject.extensions.findByType(SourceSetContainer::class.java)
            if (sourceSets == null || sourceSets.findByName(targetSourceSet) == null) {
                throw IllegalArgumentException("SourceSet '$targetSourceSet' not found in project '${realProject.path}'")
            }
        }

        val modelConfigurations = getModelConfigurations(model)

        val projectConfigs = if (targetConfig != null && targetConfig.startsWith("buildscript:")) {
            emptyList()
        } else {
            modelConfigurations.mapNotNull { realProject.configurations.findByName(it.name) }
        }
        val buildscriptConfigs = realProject.buildscript.configurations.filter {
            targetConfig == null || targetConfig == "buildscript:${it.name}"
        }
        val allConfigs = projectConfigs + buildscriptConfigs

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

        val directDependencies = gatherDirectDependencies(allConfigs)

        // Phase 1: Resolving
        val allComponents = if (checkUpdates || downloadSources) {
            val resolvableConfigs = allConfigs.filter { it.isCanBeResolved }
            val components = mutableSetOf<ModuleComponentIdentifier>()
            resolvableConfigs.forEach { config ->
                reportProgress("Resolving", config.name)
                try {
                    config.incoming.resolutionResult.allComponents.forEach {
                        val id = it.id
                        if (id is ModuleComponentIdentifier) {
                            components.add(id)
                        }
                    }
                } catch (e: Exception) {
                }
            }
            // Mark remaining non-resolvable configs as finished for this phase
            val nonResolvableCount = allConfigs.size - resolvableConfigs.size
            repeat(nonResolvableCount) { completedItems++ }
            components
        } else {
            allConfigs.forEach { reportProgress("Resolving", "Skipping ${it.name}") }
            emptySet()
        }

        val targetComponents = allComponents.filter { (!onlyDirect || "${it.group}:${it.module}" in directDependencies) && matchesFilter(it) }
        if (checkUpdates || downloadSources) {
            var newTotal = allConfigs.size.toLong() * 2
            if (checkUpdates) newTotal += targetComponents.size
            if (downloadSources) newTotal += targetComponents.size
            updateTotal(newTotal)
        }

        // Phase 2: Checking Updates
        val latestVersions = if (checkUpdates && targetComponents.isNotEmpty()) {
            gatherLatestVersions(targetComponents, directDependencies, onlyDirect, versionFilter, stableOnly) { id ->
                reportProgress("Checking Updates", id)
            }
        } else emptyMap()

        // Phase 3: Downloading Sources
        val sourcesFiles = if (downloadSources && targetComponents.isNotEmpty()) {
            gatherSources(targetComponents, directDependencies, onlyDirect) { id ->
                reportProgress("Downloading Sources", id)
            }
        } else emptyMap()

        mcpRenderer.latestVersions = latestVersions
        mcpRenderer.sourcesFiles = sourcesFiles
        mcpRenderer.checksEnabled = checkUpdates
        mcpRenderer.updatesCheckedDeps = if (checkUpdates) targetComponents.map { "${it.group}:${it.module}" }.toSet() else emptySet()
        mcpRenderer.onlyDirect = onlyDirect

        mcpRenderer.outputProject(project)
        outputRepositories(project, realProject, mcpRenderer)
        outputSourceSets(project, realProject, mcpRenderer)
        outputKotlinSourceSets(project, realProject, mcpRenderer)

        // Phase 4: Collecting
        mcpRenderer.startProject(project)
        projectConfigs.forEach { config ->
            reportProgress("Collecting", "[${realProject.path}] ${config.name}")
            val details = ConfigurationDetails.of(config)
            mcpRenderer.startConfiguration(details)
            mcpRenderer.render(details)
            mcpRenderer.completeConfiguration(details)
        }

        mcpRenderer.inBuildscript = true
        for (config in buildscriptConfigs) {
            reportProgress("Collecting", "[${realProject.path}] buildscript:${config.name}")
            val details = ConfigurationDetails.of(config)
            mcpRenderer.startConfiguration(details)
            mcpRenderer.render(details)
            mcpRenderer.completeConfiguration(details)
        }
        mcpRenderer.inBuildscript = false
        mcpRenderer.completeProject(project)
    }

    private fun getModelConfigurations(model: AbstractDependencyReportTask.DependencyReportModel): List<ConfigurationDetails> {
        val field = model.javaClass.getDeclaredField("configurations")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(model) as List<ConfigurationDetails>
    }

    private fun gatherDirectDependencies(configurations: List<Configuration>): Set<String> {
        val directDependencies = mutableSetOf<String>()
        configurations.forEach { config ->
            config.dependencies.forEach { dep ->
                if (dep is ProjectDependency) {
                    directDependencies.add("project:${dep.path}")
                } else if (dep.group != null) {
                    directDependencies.add("${dep.group}:${dep.name}")
                }
            }
        }
        return directDependencies
    }

    private fun gatherLatestVersions(
        targetComponents: List<ModuleComponentIdentifier>,
        directDependencies: Set<String>,
        onlyDirect: Boolean,
        versionFilter: String?,
        stableOnly: Boolean,
        onProgress: (String) -> Unit
    ): Map<String, String> {
        val realProject = getProject()
        val updatesConfig = realProject.configurations.detachedConfiguration()

        if (versionFilter != null || stableOnly) {
            val versionRegex = versionFilter?.let { Regex(it) }
            val unstableKeywords = setOf("alpha", "beta", "rc", "m", "milestone", "releasecandidate", "dev", "ea", "preview", "snapshot", "canary")

            fun isStable(version: String): Boolean {
                val tokens = version.split(Regex("[.\\-_+ ]"))
                val parts = tokens.flatMap { token ->
                    val result = mutableListOf<String>()
                    var start = 0
                    for (i in 1 until token.length) {
                        if (token[i].isDigit() != token[i - 1].isDigit()) {
                            result.add(token.substring(start, i))
                            start = i
                        }
                    }
                    result.add(token.substring(start))
                    result
                }
                return parts.none { it.lowercase() in unstableKeywords }
            }

            updatesConfig.resolutionStrategy.componentSelection {
                all(delegateClosureOf<org.gradle.api.artifacts.ComponentSelection> {
                    val candidate = this.candidate
                    val version = candidate.version

                    if (stableOnly && !isStable(version)) {
                        reject("Version '$version' is a pre-release version")
                    } else if (versionRegex != null && !versionRegex.containsMatchIn(version)) {
                        reject("Version '$version' does not match the provided filter regex: $versionFilter")
                    }
                })
            }
        }

        targetComponents.forEach { id ->
            val dep = realProject.dependencies.create("${id.group}:${id.module}:+") {
                (this as org.gradle.api.artifacts.ExternalModuleDependency).isTransitive = false
            }
            updatesConfig.dependencies.add(dep)
        }
        updatesConfig.resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")

        val latestVersions = mutableMapOf<String, String>()
        try {
            updatesConfig.incoming.resolutionResult.allComponents.forEach {
                val id = it.id
                if (id is ModuleComponentIdentifier) {
                    latestVersions["${id.group}:${id.module}"] = id.version
                    onProgress("${id.group}:${id.module}")
                }
            }
        } catch (e: Exception) {
        }
        return latestVersions
    }

    private fun gatherSources(
        targetComponents: List<ModuleComponentIdentifier>,
        directDependencies: Set<String>,
        onlyDirect: Boolean,
        onProgress: (String) -> Unit
    ): Map<String, String> {
        val realProject = getProject()
        val sourcesFiles = mutableMapOf<String, String>()
        try {
            val sourcesConfig = realProject.configurations.detachedConfiguration()
            targetComponents.forEach { id ->
                val dep = realProject.dependencies.create("${id.group}:${id.module}:${id.version}:sources@jar") {
                    (this as org.gradle.api.artifacts.ExternalModuleDependency).isTransitive = false
                }
                sourcesConfig.dependencies.add(dep)
            }

            sourcesConfig.resolvedConfiguration.lenientConfiguration.artifacts.forEach { artifact ->
                val id = artifact.moduleVersion.id
                sourcesFiles["${id.group}:${id.name}:${id.version}"] = artifact.file.absolutePath
                onProgress("${id.group}:${id.name}:${id.version}")
            }
        } catch (e: Throwable) {
            println("ERROR_GATHERING_SOURCES: ${e.message}")
        }
        return sourcesFiles
    }


    private fun outputRepositories(project: ProjectDetails, realProject: Project, mcpRenderer: McpDependencyReportRenderer) {
        realProject.repositories.forEach { repo ->
            val name = repo.name
            val url = when (repo) {
                is MavenArtifactRepository -> repo.url?.toString() ?: "unknown"
                is IvyArtifactRepository -> repo.url?.toString() ?: "unknown"
                else -> "unknown"
            }
            mcpRenderer.outputRepository(project, name, url)
        }
        realProject.buildscript.repositories.forEach { repo ->
            val name = repo.name
            val url = when (repo) {
                is MavenArtifactRepository -> repo.url?.toString() ?: "unknown"
                is IvyArtifactRepository -> repo.url?.toString() ?: "unknown"
                else -> "unknown"
            }
            mcpRenderer.outputRepository(project, "buildscript:$name", url)
        }
    }

    private fun outputSourceSets(project: ProjectDetails, realProject: Project, mcpRenderer: McpDependencyReportRenderer) {
        val sourceSets = realProject.extensions.findByType(SourceSetContainer::class.java)
        sourceSets?.forEach { sourceSet ->
            val configs = listOfNotNull(
                sourceSet.implementationConfigurationName,
                sourceSet.runtimeOnlyConfigurationName,
                sourceSet.compileClasspathConfigurationName
            )
            mcpRenderer.outputSourceSet(project, sourceSet.name, configs)
        }
    }

    private fun outputKotlinSourceSets(project: ProjectDetails, realProject: Project, mcpRenderer: McpDependencyReportRenderer) {
        try {
            val kotlinExtension = realProject.extensions.findByName("kotlin") ?: return
            val getSourceSets = kotlinExtension.javaClass.getMethod("getSourceSets")
            val kotlinSourceSets = getSourceSets.invoke(kotlinExtension) as? NamedDomainObjectContainer<*> ?: return

            kotlinSourceSets.forEach { sourceSet ->
                val getName = sourceSet.javaClass.getMethod("getName")
                val name = getName.invoke(sourceSet) as String
                val configs = mutableListOf<String>()
                val propNames = listOf("apiConfigurationName", "implementationConfigurationName", "compileOnlyConfigurationName", "runtimeOnlyConfigurationName")
                propNames.forEach { propName ->
                    try {
                        val methodName = "get" + propName.replaceFirstChar { it.uppercaseChar() }
                        val configName = sourceSet.javaClass.getMethod(methodName).invoke(sourceSet) as? String
                        if (configName != null) configs.add(configName)
                    } catch (e: Exception) {
                    }
                }
                mcpRenderer.outputSourceSet(project, "kotlin:$name", configs)
            }
        } catch (e: Exception) {
        }
    }
}

// use AsciiDependencyReportRenderer for guidance
class McpDependencyReportRenderer : DependencyReportRenderer {
    private var output: StyledTextOutput? = null
    var latestVersions: Map<String, String> = emptyMap()
    var sourcesFiles: Map<String, String> = emptyMap()

    // Set of "group:module" coordinates that were targeted for update checking (i.e., in targetComponents).
    // Used together with latestVersions to determine whether a dep's check genuinely failed.
    var updatesCheckedDeps: Set<String> = emptySet()

    // Whether update checking was enabled at all for this run.
    var checksEnabled: Boolean = false
    var onlyDirect: Boolean = false
    var gradleProject: Project? = null
    var inBuildscript: Boolean = false
    private var currentProject: ProjectDetails? = null
    private var currentConfigurationName: String? = null

    // Performance Caches
    private val hierarchyCache = mutableMapOf<Configuration, List<Configuration>>()
    private val declaredDependenciesCache = mutableMapOf<Configuration, Set<Pair<String?, String>>>()
    private val declaringConfigCache = mutableMapOf<Pair<Configuration, Any>, Configuration?>()

    override fun setOutput(textOutput: StyledTextOutput) {
        this.output = textOutput
    }

    override fun setOutputFile(file: File) {}

    fun outputProject(project: ProjectDetails) {
        output?.println("PROJECT: ${getProjectPath(project)} | ${project.displayName}")
    }

    fun outputRepository(project: ProjectDetails, name: String, url: String) {
        output?.println("REPOSITORY: ${getProjectPath(project)} | $name | $url")
    }

    fun outputSourceSet(project: ProjectDetails, name: String, configurations: List<String>) {
        output?.println("SOURCESET: ${getProjectPath(project)} | $name | ${configurations.joinToString(",")}")
    }

    override fun startProject(project: ProjectDetails) {
        this.currentProject = project
    }

    override fun completeProject(project: ProjectDetails) {
        this.currentProject = null
    }

    override fun startConfiguration(configuration: ConfigurationDetails) {
        this.currentConfigurationName = configuration.name
        val path = currentProject?.let { getProjectPath(it) } ?: "unknown"
        val realConfig = if (inBuildscript) gradleProject?.buildscript?.configurations?.findByName(configuration.name) else gradleProject?.configurations?.findByName(configuration.name)
        val extendsFrom = realConfig?.extendsFrom?.map { if (inBuildscript) "buildscript:${it.name}" else it.name }?.joinToString(",") ?: ""
        val prefix = if (inBuildscript) "buildscript:" else ""
        output?.println("CONFIGURATION: $path | $prefix${configuration.name} | ${configuration.description ?: ""} | ${configuration.isCanBeResolved} | $extendsFrom")
    }

    override fun render(configuration: ConfigurationDetails) {
        val project = currentProject ?: return
        val realConfig = (if (inBuildscript) gradleProject?.buildscript?.configurations?.findByName(configuration.name) else gradleProject?.configurations?.findByName(configuration.name)) ?: return
        
        if (configuration.isCanBeResolved) {
            val root = configuration.resolutionResultRoot?.get() ?: return
            val visited = mutableSetOf<Any>()

            // For the root, we use its ID and first variant (if any) as the visited key
            val variants = root.variants
            val (vName, caps) = if (variants.isNotEmpty()) getVariantInfoFromResolved(variants[0]) else "" to emptyList<String>()
            visited.add(listOf(root.id, vName, caps))

            root.dependencies.forEach { dep ->
                if (dep is ResolvedDependencyResult && dep.isConstraint()) return@forEach

                val declaringConfig = findDeclaringConfiguration(realConfig, dep)
                if (declaringConfig == null) return@forEach

                val declaringName = if (inBuildscript) "buildscript:${declaringConfig.name}" else declaringConfig.name

                if (declaringConfig == realConfig) {
                    renderDependencyResult(project, dep, 1, visited, null)
                } else if (!declaringConfig.isCanBeResolved()) {
                    renderDependencyResult(project, dep, 1, visited, declaringName)
                }
            }
        } else {
            val rootNode = configuration.unresolvableResult
            if (rootNode != null) {
                val visited = mutableSetOf<Any>()
                rootNode.id?.let { visited.add(it) }
                rootNode.children.forEach { child ->
                    if (isDeclaredInUnresolvable(configuration.name, child)) {
                        renderRenderableDependency(project, child, 1, visited)
                    }
                }
            }
        }
    }

    private fun findDeclaringConfiguration(currentConfig: Configuration, dep: DependencyResult): Configuration? {
        val requested = dep.requested
        val key = currentConfig to requested
        return declaringConfigCache.getOrPut(key) {
            val hierarchy = hierarchyCache.getOrPut(currentConfig) { currentConfig.hierarchy.toList() }

            // We want to find a configuration in the hierarchy that declares this dependency.
            for (conf in hierarchy) {
                val declaredDeps = declaredDependenciesCache.getOrPut(conf) {
                    conf.dependencies.map { it.group to it.name }.toSet()
                }

                val match = when (requested) {
                    is ModuleComponentSelector -> {
                        (requested.group to requested.module) in declaredDeps
                    }

                    is ProjectComponentSelector -> {
                        conf.dependencies.any { declared ->
                            if (declared is ProjectDependency) {
                                val dPath = declared.path
                                dPath == requested.projectPath || (dPath == ":" && requested.projectPath == ":") || (dPath == requested.projectPath.removePrefix(":"))
                            } else if (declared is org.gradle.api.artifacts.ExternalModuleDependency && declared.group == "project") {
                                val dPath = declared.name
                                dPath == requested.projectPath || (dPath == ":" && requested.projectPath == ":") || (dPath == requested.projectPath.removePrefix(":"))
                            } else false
                        }
                    }

                    else -> false
                }
                if (match) return@getOrPut conf
            }
            null
        }
    }

    private fun isDeclaredInUnresolvable(configName: String, dep: RenderableDependency): Boolean {
        val config = (if (inBuildscript) gradleProject?.buildscript?.configurations?.findByName(configName) else gradleProject?.configurations?.findByName(configName)) ?: return false
        val name = dep.getName()
        val depId = dep.getId()
        return config.dependencies.any { declared ->
            if (declared is ProjectDependency) {
                name.contains(declared.getPath()) || (depId is ProjectComponentSelector && depId.projectPath == declared.getPath())
            } else {
                val dGroup = declared.group
                val dName = declared.name
                if (dGroup != null) {
                    name.contains("$dGroup:$dName") || (depId is ModuleComponentSelector && depId.group == dGroup && depId.module == dName)
                } else {
                    name.contains(dName) || (depId is ModuleComponentSelector && depId.module == dName)
                }
            }
        }
    }

    /**
     * Returns true when the dep's update-check status is complete (no annotation needed):
     *  - dep not in updatesCheckedDeps → dep was intentionally excluded from scope (e.g., transitive
     *    dep when onlyDirect=true, or excluded by a dependency filter); not a genuine failure.
     *  - dep in latestVersions → check was attempted and succeeded; not a failure.
     * Returns false (annotation needed) when: checksEnabled=true AND dep was in scope
     * (present in updatesCheckedDeps) AND resolution yielded no result (absent from latestVersions).
     *
     * When checksEnabled=false the renderer gates on checksUpdates (the Kotlin-side flag) and
     * suppresses the annotation regardless of this field's value, so returning false here is safe.
     */
    private fun isUpdateCheckComplete(group: String, name: String): Boolean {
        if (!checksEnabled) return false
        val coordinateKey = "$group:$name"
        return !updatesCheckedDeps.contains(coordinateKey) || latestVersions.containsKey(coordinateKey)
    }

    private fun renderDependencyResult(project: ProjectDetails, dep: DependencyResult, depth: Int, visited: MutableSet<Any>, fromConfiguration: String?) {
        val path = getProjectPath(project)

        if (dep is UnresolvedDependencyResult) {
            val markers = "*".repeat(depth)
            val id = dep.requested.toString()
            val reason = dep.failure?.message ?: ""
            output?.println("DEP: $path | $markers | $id | | | | $reason | | true | | | ${fromConfiguration ?: ""}")
            return
        }

        if (dep is ResolvedDependencyResult) {
            val component = dep.selected
            val variant = dep.resolvedVariant
            val (variantName, caps) = getVariantInfoFromResolved(variant)

            val id = component.id
            val (group, name, version) = when (id) {
                is ModuleComponentIdentifier -> Triple(id.group, id.module, id.version)
                is ProjectComponentIdentifier -> Triple("project", id.projectPath, "")
                else -> Triple("", dep.requested.displayName, "")
            }

            val isDirect = (depth == 1)
            if (onlyDirect && !isDirect) return

            val visitKey = listOf(id, variantName, caps)
            val alreadyRendered = !visited.add(visitKey)
            val markers = "*".repeat(depth)
            val latestVersion = latestVersions["$group:$name"] ?: ""
            val sourcesFile = sourcesFiles[id.toString()] ?: sourcesFiles["$group:$name:$version"] ?: ""
            val updatesChecked = isUpdateCheckComplete(group, name)

            var selectionReason = ""
            val requested = dep.requested
            if (requested is ModuleComponentSelector) {
                val requestedVersion = requested.version
                if (requestedVersion != version && requestedVersion.isNotEmpty()) {
                    selectionReason = "requested: $requestedVersion"
                }
            }

            output?.println("DEP: $path | $markers | $id | $group | $name | $version | $selectionReason | $latestVersion | $isDirect | $variantName | ${caps.joinToString(",")} | ${fromConfiguration ?: ""} | $sourcesFile | $updatesChecked")

            if (!alreadyRendered) {
                component.getDependenciesForVariant(variant).forEach { child ->
                    if (child is ResolvedDependencyResult && child.isConstraint()) return@forEach
                    renderDependencyResult(project, child, depth + 1, visited, null)
                }
            }
        }
    }

    private fun renderRenderableDependency(project: ProjectDetails, dep: RenderableDependency, depth: Int, visited: MutableSet<Any>) {
        val depId = dep.getId() ?: return
        val path = getProjectPath(project)

        val alreadyRendered = !visited.add(depId)
        val markers = "*".repeat(depth)
        val isDirect = (depth == 1)

        var group = ""
        var name = ""
        var version = ""

        when (depId) {
            is ModuleComponentSelector -> {
                group = depId.group
                name = depId.module
                version = depId.version
            }

            is ModuleComponentIdentifier -> {
                group = depId.group
                name = depId.module
                version = depId.version
            }

            is ProjectComponentSelector -> {
                group = "project"
                name = depId.projectPath
                version = ""
            }

            is ProjectComponentIdentifier -> {
                group = "project"
                name = depId.projectPath
                version = ""
            }

            else -> {
                val fullName = dep.getName() ?: ""
                val parts = fullName.split(":")
                if (parts.size >= 2) {
                    group = parts[0]
                    name = parts[1]
                    version = parts.getOrNull(2) ?: ""
                } else {
                    name = fullName
                }
            }
        }

        val renderedId = if (group.isNotEmpty() && name.isNotEmpty()) {
            if (version.isNotEmpty()) "$group:$name:$version" else "$group:$name"
        } else {
            depId.toString()
        }

        val sourcesFile = sourcesFiles[renderedId] ?: sourcesFiles["$group:$name:$version"] ?: ""
        val updatesChecked = isUpdateCheckComplete(group, name)

        output?.println("DEP: $path | $markers | $renderedId | $group | $name | $version | ${dep.getDescription() ?: ""} | | $isDirect | | | | $sourcesFile | $updatesChecked")

        if (!alreadyRendered) {
            dep.children.forEach { renderRenderableDependency(project, it, depth + 1, visited) }
        }
    }

    private fun getVariantInfoFromResolved(variant: ResolvedVariantResult?): Pair<String, List<String>> {
        if (variant == null) return "" to emptyList()
        val displayName = variant.displayName
        val caps = variant.capabilities.map { "${it.group}:${it.name}:${it.version}" }
        return displayName to caps
    }

    override fun completeConfiguration(configuration: ConfigurationDetails) {
        this.currentConfigurationName = null
    }

    override fun complete() {}

    private fun getProjectPath(project: ProjectDetails): String {
        if (project is ProjectDetails.ProjectNameAndPath) return project.path
        val displayName = project.displayName
        return when {
            displayName.startsWith("project '") && displayName.endsWith("'") -> displayName.substring(9, displayName.length - 1)
            displayName == "root project" -> ":"
            displayName.startsWith("root project '") && displayName.endsWith("'") -> ":"
            else -> displayName
        }
    }
}

allprojects {
    tasks.register("mcpDependencyReport", McpDependencyReportTask::class.java) {
        doFirst {
            val targetConfig = if (project.hasProperty("mcp.configuration")) project.property("mcp.configuration").toString() else null
            val targetSourceSet = if (project.hasProperty("mcp.sourceSet")) project.property("mcp.sourceSet").toString() else null

            if (targetConfig != null) {
                val isBuildscript = targetConfig.startsWith("buildscript:")
                val configName = if (isBuildscript) targetConfig.substringAfter("buildscript:") else targetConfig
                val exists = if (isBuildscript) {
                    project.buildscript.configurations.findByName(configName) != null
                } else {
                    project.configurations.findByName(configName) != null
                }
                if (!exists) {
                    throw IllegalArgumentException("Configuration '$targetConfig' not found in project '${project.path}'")
                }
            }

            if (targetSourceSet != null) {
                val sourceSets = project.extensions.findByType(org.gradle.api.tasks.SourceSetContainer::class.java)
                if (sourceSets == null || sourceSets.findByName(targetSourceSet) == null) {
                    throw IllegalArgumentException("SourceSet '$targetSourceSet' not found in project '${project.path}'")
                }
            }
        }
    }
}
