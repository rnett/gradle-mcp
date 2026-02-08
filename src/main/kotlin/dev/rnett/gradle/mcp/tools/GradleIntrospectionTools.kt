package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectPath
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.matches
import dev.rnett.gradle.mcp.gradle.throwFailure
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.gradle.GradleBuild

class GradleIntrospectionTools(
    val gradle: GradleProvider,
) : McpServerComponent("Introspection Tools", "Tools for inspecting Gradle build configuration.") {

    companion object {
        const val BUILD_ID_DESCRIPTION = "The build ID of the build used to query this information."
    }

    @Serializable
    data class GradleProjectInfo(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId?,
        @Description("The Gradle project's path, e.g. :project-a")
        val path: String,
        @Description("The name of the project - not related to the path.")
        val name: String,
        @Description("The project's description, if it has one")
        val description: String?,
        @Description("The tasks of the project, keyed by group. Note that the group is purely information and not used when invoking the task.")
        val tasksByGroup: Map<String, List<GradleTask>>,
        @Description("The paths of child projects.")
        val childProjects: List<String>,
        @Description("The path to the build script of this project, if it exists")
        val buildScriptPath: String?,
        @Description("The path to the project directory of this project, if it exists")
        val projectDirectoryPath: String?
    )

    @Serializable
    data class GradleTask(
        @Description("The name of the task, used to invoke it.")
        val name: String,
        @Description("A description of the task")
        val description: String?
    )

    @Serializable
    data class DescribeProjectArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        val invocationArgs: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    val describeProject by tool<DescribeProjectArgs, GradleProjectInfo>(
        "describe_project",
        "Describes a Gradle project or subproject. Includes the tasks and child projects. Can be used to query available tasks."
    ) { args ->
        gradle.getBuildModel<GradleProject>(args.projectRoot, args.invocationArgs).throwFailure().let { (id, it) ->
            val project = it.findByPath(args.projectPath.path) ?: throw IllegalArgumentException("Project with project path \"${args.projectPath}\" not found")
            GradleProjectInfo(
                id,
                project.path,
                project.name,
                project.description,
                project.tasks.groupBy { it.group ?: "other" }.mapValues { it.value.map { task -> GradleTask(task.name, task.description) } },
                project.children.map { it.path },
                project.buildScript?.sourceFile?.absolutePath,
                project.projectDirectory?.absolutePath
            )
        }
    }

    @Serializable
    data class GradleIncludedBuilds(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId?,
        @Description("Builds added as included builds to this Gradle project. Defined in the settings.gradle(.kts) file.")
        val includedBuilds: List<IncludedBuild>
    ) {
        @Serializable
        data class IncludedBuild(
            @Description("The root project name of the included build. Used to reference it from the main build, e.g. ':included-build-root-project-name:included-build-subproject:task'.")
            val rootProjectName: String,
            @Description("The file system path of the included build's root project directory.")
            val rootProjectDirectoryPath: String
        )
    }

    @Serializable
    data class IncludedBuildsArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        val invocationArgs: GradleInvocationArguments = GradleInvocationArguments.DEFAULT,
    )

    val getIncludedBuilds by tool<IncludedBuildsArgs, GradleIncludedBuilds>(
        "get_included_builds",
        "Gets the included builds of a Gradle project."
    ) {
        gradle.getBuildModel<GradleBuild>(it.projectRoot, it.invocationArgs).throwFailure().let { (id, it) ->
            GradleIncludedBuilds(
                id,
                it.editableBuilds.map {
                    GradleIncludedBuilds.IncludedBuild(it.rootProject.name, it.rootProject.projectDirectory.absolutePath)
                }
            )
        }
    }


    @Description("An artifact published by Gradle")
    @Serializable
    data class Publication(
        @Description("The group of the publication's module identifier")
        val group: String,
        @Description("The name of the publication's module identifier")
        val name: String,
        @Description("The version of the publication's module identifier")
        val version: String
    )

    @Serializable
    data class ProjectPublicationsResult(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId?,
        @Description("All publications that Gradle knows about for the project.")
        val publications: Set<Publication>
    )

    @Serializable
    data class ProjectPublicationsArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        val invocationArgs: GradleInvocationArguments = GradleInvocationArguments.DEFAULT,
    )

    val getProjectPublications by tool<ProjectPublicationsArgs, ProjectPublicationsResult>(
        "get_project_publications",
        "Gets all publications (i.e. artifacts published that Gradle knows about) for the Gradle project."
    ) { args ->
        val (id, publicationsModel) = gradle.getBuildModel<org.gradle.tooling.model.gradle.ProjectPublications>(
            args.projectRoot,
            args.invocationArgs
        ).throwFailure()

        val root = args.projectRoot.resolve()

        val publications = publicationsModel.publications
            .filter { it.projectIdentifier.matches(root, args.projectPath) }
            .map { Publication(it.id.group, it.id.name, it.id.version) }
            .toSet()

        ProjectPublicationsResult(id, publications)
    }

}