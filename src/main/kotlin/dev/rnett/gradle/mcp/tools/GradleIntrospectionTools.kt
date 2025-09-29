package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.throwFailure
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.gradle.GradleBuild

class GradleIntrospectionTools(
    val gradle: GradleProvider,
) : McpServerComponent() {

    @Serializable
    data class GradleBuildEnvironment(
        @Description("Information about the Gradle build environment")
        val gradleInformation: GradleInfo,
        @Description("Information about the JVM used to execute Gradle in the build environment")
        val javaInformation: JavaInfo,
    ) {
        @Serializable
        data class GradleInfo(
            @Description("The Gradle user home directory")
            val gradleUserHome: String,
            @Description("The Gradle version used by this project")
            val gradleVersion: String
        )

        @Serializable
        data class JavaInfo(
            @Description("The path of the Java home used by this Gradle project")
            val javaHome: String,
            @Description("The JVM used by this Gradle project")
            val jvmArguments: List<String>
        )
    }

    @Serializable
    data class GetEnvArguments(
        val projectRoot: GradleProjectRoot,
        val invocationArgs: GradleInvocationArguments = GradleInvocationArguments.DEFAULT,
    )

    val getEnv by tool<GetEnvArguments, GradleBuildEnvironment>(
        "get_environment",
        "Get the environment used to execute Gradle for the given project, including the Gradle version and JVM information."
    ) {
        gradle.getBuildModel<BuildEnvironment>(
            it.projectRoot.projectRoot,
            it.invocationArgs
        ).outcome.throwFailure().value.let {
            GradleBuildEnvironment(
                GradleBuildEnvironment.GradleInfo(it.gradle.gradleUserHome.absolutePath, it.gradle.gradleVersion),
                GradleBuildEnvironment.JavaInfo(it.java.javaHome.absolutePath, it.java.jvmArguments)
            )
        }
    }

    @Serializable
    data class GradleProjectInfo(
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
        val projectRoot: GradleProjectRoot,
        val projectPath: GradleProjectPath = GradleProjectPath(":"),
        val invocationArgs: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    val describeProject by tool<DescribeProjectArgs, GradleProjectInfo>(
        "describe_project",
        "Describes a Gradle project or subproject. Includes the tasks and child projects. Can be used to query available tasks."
    ) { args ->
        gradle.getBuildModel<GradleProject>(args.projectRoot.projectRoot, args.invocationArgs).outcome.throwFailure().value.let {
            val project = it.findByPath(args.projectPath.projectPath) ?: throw IllegalArgumentException("Project with project path \"${args.projectPath}\" not found")
            GradleProjectInfo(
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
        @Description("Builds added as included builds to this Gradle project. Defined in the settings.gradle(.kts) file.")
        val includedBuilds: List<IncludedBuild>
    )

    @Serializable
    data class IncludedBuild(
        @Description("The root project name of the included build. Used to reference it from the main build, e.g. ':included-build-root-project-name:included-build-subproject:task'.")
        val rootProjectName: String,
        @Description("The file system path of the included build's root project directory.")
        val rootProjectDirectoryPath: String
    )

    @Serializable
    data class IncludedBuildsArgs(
        val projectRoot: GradleProjectRoot,
        val invocationArgs: GradleInvocationArguments = GradleInvocationArguments.DEFAULT,
    )

    val getIncludedBuilds by tool<IncludedBuildsArgs, GradleIncludedBuilds>(
        "get_included_builds",
        "Gets the included builds of a Gradle project."
    ) {
        gradle.getBuildModel<GradleBuild>(it.projectRoot.projectRoot, it.invocationArgs).outcome.throwFailure().value.let {
            GradleIncludedBuilds(it.editableBuilds.map {
                IncludedBuild(it.rootProject.name, it.rootProject.projectDirectory.absolutePath)
            })
        }
    }


}