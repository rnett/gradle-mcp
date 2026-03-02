import org.gradle.api.NamedDomainObjectContainer
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
        val versionFilter = if (realProject.hasProperty("mcp.versionFilter")) realProject.property("mcp.versionFilter").toString() else null

        val configurations = getModelConfigurations(model)
        val directDependencies = gatherDirectDependencies(configurations)
        val latestVersions = if (checkUpdates) gatherLatestVersions(configurations, directDependencies, onlyDirect, versionFilter) else emptyMap()

        mcpRenderer.latestVersions = latestVersions
        mcpRenderer.onlyDirect = onlyDirect

        mcpRenderer.outputProject(project)
        outputRepositories(project, realProject, mcpRenderer)
        outputSourceSets(project, realProject, mcpRenderer)
        outputKotlinSourceSets(project, realProject, mcpRenderer)

        super.generateReportFor(project, model)
    }

    private fun getModelConfigurations(model: AbstractDependencyReportTask.DependencyReportModel): List<ConfigurationDetails> {
        val field = model.javaClass.getDeclaredField("configurations")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(model) as List<ConfigurationDetails>
    }

    private fun gatherDirectDependencies(configurations: List<ConfigurationDetails>): Set<String> {
        val directDependencies = mutableSetOf<String>()
        val realProject = getProject()
        configurations.forEach { configDetails ->
            val config = realProject.configurations.findByName(configDetails.name)
            config?.dependencies?.forEach { dep ->
                if (dep is ProjectDependency) {
                    directDependencies.add("project:${dep.path}")
                } else if (dep.group != null && dep.name != null) {
                    directDependencies.add("${dep.group}:${dep.name}")
                }
            }
        }
        return directDependencies
    }

    private fun gatherLatestVersions(
        configurations: List<ConfigurationDetails>,
        directDependencies: Set<String>,
        onlyDirect: Boolean,
        versionFilter: String?
    ): Map<String, String> {
        val realProject = getProject()
        val allUniqueModuleComponents = mutableSetOf<ModuleComponentIdentifier>()

        configurations.filter { it.isCanBeResolved }.forEach { configDetails ->
            try {
                val config = realProject.configurations.findByName(configDetails.name)
                config?.incoming?.resolutionResult?.allComponents?.forEach {
                    val id = it.id
                    if (id is ModuleComponentIdentifier) {
                        allUniqueModuleComponents.add(id)
                    }
                }
            } catch (e: Exception) {
            }
        }

        if (allUniqueModuleComponents.isEmpty()) return emptyMap()

        val updatesConfig = realProject.configurations.detachedConfiguration()

        if (versionFilter != null) {
            val versionRegex = Regex(versionFilter)
            updatesConfig.resolutionStrategy.componentSelection {
                all(delegateClosureOf<org.gradle.api.artifacts.ComponentSelection> {
                    val candidate = this.candidate
                    val version = candidate.version

                    if (!version.matches(versionRegex)) {
                        reject("Version '$version' does not match the provided filter regex: $versionFilter")
                    }
                })
            }
        }

        allUniqueModuleComponents.forEach { id ->
            if (!onlyDirect || "${id.group}:${id.module}" in directDependencies) {
                val dep = realProject.dependencies.create("${id.group}:${id.module}:+") {
                    (this as org.gradle.api.artifacts.ExternalModuleDependency).isTransitive = false
                }
                updatesConfig.dependencies.add(dep)
            }
        }
        updatesConfig.resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")

        val latestVersions = mutableMapOf<String, String>()
        try {
            updatesConfig.incoming.resolutionResult.allComponents.forEach {
                val id = it.id
                if (id is ModuleComponentIdentifier) {
                    latestVersions["${id.group}:${id.module}"] = id.version
                }
            }
        } catch (e: Exception) {
        }
        return latestVersions
    }

    private fun outputRepositories(project: ProjectDetails, realProject: Project, mcpRenderer: McpDependencyReportRenderer) {
        realProject.repositories.forEach { repo ->
            val name = repo.name
            val url = when (repo) {
                is MavenArtifactRepository -> repo.url.toString()
                is IvyArtifactRepository -> repo.url.toString()
                else -> "unknown"
            }
            mcpRenderer.outputRepository(project, name, url)
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

// use AsciiDependencyReportRenderer for guidence
class McpDependencyReportRenderer : DependencyReportRenderer {
    private var output: StyledTextOutput? = null
    var latestVersions: Map<String, String> = emptyMap()
    var onlyDirect: Boolean = false
    var gradleProject: Project? = null
    private var currentProject: ProjectDetails? = null
    private var currentConfigurationName: String? = null

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
        val realConfig = gradleProject?.configurations?.findByName(configuration.name)
        val extendsFrom = realConfig?.extendsFrom?.map { it.name }?.joinToString(",") ?: ""
        output?.println("CONFIGURATION: $path | ${configuration.name} | ${configuration.description ?: ""} | ${configuration.isCanBeResolved} | $extendsFrom")
    }

    override fun render(configuration: ConfigurationDetails) {
        val project = currentProject ?: return
        val realConfig = gradleProject?.configurations?.findByName(configuration.name) ?: return
        
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

                if (declaringConfig == realConfig) {
                    renderDependencyResult(project, dep, 1, visited, null)
                } else if (!declaringConfig.isCanBeResolved()) {
                    renderDependencyResult(project, dep, 1, visited, declaringConfig.name)
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

        // We want to find a configuration in the hierarchy that declares this dependency.
        // Hierarchy includes the configuration itself.
        for (conf in currentConfig.getHierarchy()) {
            val match = conf.dependencies.any { declared ->
                when (requested) {
                    is ModuleComponentSelector -> {
                        declared.group == requested.group && declared.name == requested.module
                    }

                    is ProjectComponentSelector -> {
                        val dPath = if (declared is ProjectDependency) declared.getPath() else if (declared is org.gradle.api.artifacts.ExternalModuleDependency && declared.group == "project") declared.name else null
                        dPath != null && (dPath == requested.projectPath || (dPath == ":" && requested.projectPath == ":") || (dPath == requested.projectPath.removePrefix(":")))
                    }

                    else -> false
                }
            }
            if (match) return conf
        }
        return null
    }

    private fun isDeclaredInUnresolvable(configName: String, dep: RenderableDependency): Boolean {
        val config = gradleProject?.configurations?.findByName(configName) ?: return false
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

            var selectionReason = ""
            val requested = dep.requested
            if (requested is ModuleComponentSelector) {
                val requestedVersion = requested.version
                if (requestedVersion != version && requestedVersion.isNotEmpty()) {
                    selectionReason = "requested: $requestedVersion"
                }
            }

            output?.println("DEP: $path | $markers | $id | $group | $name | $version | $selectionReason | $latestVersion | $isDirect | $variantName | ${caps.joinToString(",")} | ${fromConfiguration ?: ""}")

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

        output?.println("DEP: $path | $markers | $renderedId | $group | $name | $version | ${dep.getDescription() ?: ""} | | $isDirect | | |")

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
    tasks.register("mcpDependencyReport", McpDependencyReportTask::class.java)
}
