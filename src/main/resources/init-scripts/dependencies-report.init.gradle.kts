import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.diagnostics.AbstractDependencyReportTask
import org.gradle.api.tasks.diagnostics.internal.ConfigurationDetails
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableModuleResult
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

        // Output Project Info
        mcpRenderer.outputProject(project)

        // Output Repositories
        val realProject = getProject()
        realProject.repositories.forEach { repo ->
            val name = repo.name
            val url = when (repo) {
                is MavenArtifactRepository -> repo.url.toString()
                is IvyArtifactRepository -> repo.url.toString()
                else -> "unknown"
            }
            mcpRenderer.outputRepository(project, name, url)
        }

        // Output Source Sets
        val sourceSets = realProject.extensions.findByType(SourceSetContainer::class.java)
        sourceSets?.forEach { sourceSet ->
            val configs = mutableListOf<String>()
            configs.add(sourceSet.implementationConfigurationName)
            configs.add(sourceSet.runtimeOnlyConfigurationName)
            configs.add(sourceSet.compileClasspathConfigurationName)
            configs.add(sourceSet.runtimeOnlyConfigurationName)
            mcpRenderer.outputSourceSet(project, sourceSet.name, configs.filter { it.isNotBlank() })
        }

        // Kotlin Source Sets via Reflection
        try {
            val kotlinExtension = realProject.extensions.findByName("kotlin")
            if (kotlinExtension != null) {
                val kotlinSourceSets = kotlinExtension.javaClass.getMethod("getSourceSets").invoke(kotlinExtension) as? NamedDomainObjectContainer<*>
                kotlinSourceSets?.forEach { sourceSet ->
                    val name = sourceSet.javaClass.getMethod("getName").invoke(sourceSet) as String
                    val configs = mutableListOf<String>()
                    listOf("apiConfigurationName", "implementationConfigurationName", "compileOnlyConfigurationName", "runtimeOnlyConfigurationName").forEach { propName ->
                        try {
                            val configName = sourceSet.javaClass.getMethod("get" + propName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }).invoke(sourceSet) as String
                            configs.add(configName)
                        } catch (e: Exception) {
                        }
                    }
                    mcpRenderer.outputSourceSet(project, "kotlin:$name", configs.filter { it.isNotBlank() })
                }
            }
        } catch (e: Exception) {
            // Kotlin not present or reflection failed
        }

        // Output Dependencies (standard behavior of AbstractDependencyReportTask calls renderer.startConfiguration/render/completeConfiguration)
        super.generateReportFor(project, model)
    }
}

class McpDependencyReportRenderer : DependencyReportRenderer {
    private var output: StyledTextOutput? = null

    override fun setOutput(textOutput: StyledTextOutput) {
        this.output = textOutput
    }

    override fun setOutputFile(file: File) {
        // Not supported for this custom renderer, we want stdout
    }

    fun outputProject(project: ProjectDetails) {
        val path = if (project is ProjectDetails.ProjectNameAndPath) {
            project.path
        } else if (project is ProjectDetails.ProjectDisplayNameAndDescription) {
            // Accessing internal projectLogicalPath might be tricky via reflection or if it's not exposed
            // But usually we can get path from the actual project object
            // For now let's try to get it from the display name if path is not available
            project.displayName.substringAfter("project '").substringBefore("'")
        } else {
            project.displayName
        }
        output?.println("PROJECT: $path | ${project.displayName}")
    }

    private fun getProjectPath(project: ProjectDetails): String {
        return if (project is ProjectDetails.ProjectNameAndPath) {
            project.path
        } else {
            // fallback
            project.displayName.substringAfter("project '").substringBefore("'")
        }
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

    override fun complete() {}

    private var currentProject: ProjectDetails? = null

    override fun startConfiguration(configuration: ConfigurationDetails) {
        val path = currentProject?.let { getProjectPath(it) } ?: "unknown"
        output?.println("CONFIGURATION: $path | ${configuration.name} | ${configuration.description ?: ""} | ${configuration.isCanBeResolved}")
    }

    override fun render(configuration: ConfigurationDetails) {
        if (configuration.isCanBeResolved) {
            val result = configuration.resolutionResultRoot?.get()
            if (result != null) {
                val root = RenderableModuleResult(result)
                renderDependency(root, 1, mutableSetOf())
            }
        }
    }

    private fun renderDependency(dep: RenderableDependency, depth: Int, visited: MutableSet<Any>) {
        val id = dep.id
        val markers = "*".repeat(depth)
        val path = currentProject?.let { getProjectPath(it) } ?: "unknown"

        val (group, name, version) = when (id) {
            is org.gradle.api.artifacts.component.ModuleComponentIdentifier -> Triple(id.group, id.module, id.version)
            is org.gradle.api.artifacts.component.ProjectComponentIdentifier -> Triple("project", id.projectPath, "")
            else -> Triple("", dep.name, "")
        }
        val reason = dep.description ?: ""
        val idStr = id?.toString() ?: "unknown"

        if (id != null && !visited.add(id)) {
            output?.println("DEP: $path | $markers | $idStr | $group | $name | $version | $reason | true")
            return
        }

        output?.println("DEP: $path | $markers | $idStr | $group | $name | $version | $reason | false")

        dep.children.forEach { child ->
            renderDependency(child, depth + 1, visited)
        }
    }

    override fun completeConfiguration(configuration: ConfigurationDetails) {}
}

allprojects {
    tasks.register("mcpDependencyReport", McpDependencyReportTask::class.java)
}
