package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpFactory
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.modelcontextprotocol.spec.McpSchema.LoggingLevel
import kotlinx.serialization.Serializable
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.gradle.GradleBuild
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service

@Service
class BasicGradleTools(
    val connectionProvider: GradleProvider,
    val toolFactory: McpFactory
) {

    @Serializable
    @Description("Arguments that determine how Gradle is invoked")
    data class GradleInvocationArguments(
        @Description("Additional environment variables to set for the Gradle process. Optional.")
        val additionalEnvVars: Map<String, String>? = null,
        @Description("Additional system properties to set for the Gradle process. Optional.")
        val additionalSystemProps: Map<String, String>? = null,
        @Description("Additional JVM arguments to set for the Gradle process. Optional.")
        val additionalJvmArgs: List<String>? = null,
        @Description("Additional arguments for the Gradle process. Optional.")
        val additionalArguments: List<String>? = null,
    )

    final suspend inline fun <reified T : Model> McpFactory.McpContext<*>.getBuildModel(
        projectRoot: String,
        invocationArgs: GradleInvocationArguments?,
    ): T {
        return connectionProvider.getBuildModel(
            projectRoot,
            T::class,
            invocationArgs?.additionalJvmArgs.orEmpty(),
            invocationArgs?.additionalSystemProps.orEmpty(),
            invocationArgs?.additionalEnvVars.orEmpty(),
            invocationArgs?.additionalArguments.orEmpty(),
            stdoutLineHandler = { emitLoggingNotification("gradle-build", LoggingLevel.INFO, it) },
            stderrLineHandler = { emitLoggingNotification("gradle-build", LoggingLevel.ERROR, it) },
        )
    }

    companion object {
        const val PROJECT_ROOT_DESCRIPTION = "The path of the Gradle project's root, where the gradlew script and settings.gradle(.kts) files are located"
        const val GRADLE_PROJECT_PATH = "The Gradle project path, e.g. :project-a:subproject-b. ':' is the root project."
        const val INVOCATION_ARGS = "Additional arguments to configure the Gradle process. Optional."
    }

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
        @Description(PROJECT_ROOT_DESCRIPTION)
        val projectRoot: String,
        @Description(INVOCATION_ARGS)
        val invocationArgs: GradleInvocationArguments = GradleInvocationArguments(),
    )

    @Bean
    fun getEnvTool() = toolFactory.tool<GetEnvArguments, GradleBuildEnvironment>(
        "get_environment",
        "Get the environment used to execute Gradle for the given project"
    ) {
        getBuildModel<BuildEnvironment>(
            it.projectRoot,
            it.invocationArgs
        ).let {
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
        @Description(PROJECT_ROOT_DESCRIPTION)
        val projectRoot: String,
        @Description(description = GRADLE_PROJECT_PATH)
        val projectPath: String = ":",
        @Description(description = INVOCATION_ARGS)
        val invocationArgs: GradleInvocationArguments = GradleInvocationArguments()
    )

    @Bean
    fun describeProjectTool() = toolFactory.tool<DescribeProjectArgs, GradleProjectInfo>(
        "describe_project",
        "Describes a Gradle project or subproject. Includes the tasks and child projects."
    ) { args ->
        getBuildModel<GradleProject>(args.projectRoot, args.invocationArgs).let {
            val project = it.findByPath(args.projectPath) ?: throw IllegalArgumentException("Project with project path \"${args.projectPath}\" not found")
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
        @Description(PROJECT_ROOT_DESCRIPTION)
        val projectRoot: String,
        @Description(INVOCATION_ARGS)
        val invocationArgs: GradleInvocationArguments = GradleInvocationArguments(),
    )

    @Bean
    fun includedBuildsTool() = toolFactory.tool<IncludedBuildsArgs, GradleIncludedBuilds>(
        "get_included_builds",
        "Gets the included builds of a Gradle project."
    ) {
        getBuildModel<GradleBuild>(it.projectRoot, it.invocationArgs).let {
            GradleIncludedBuilds(it.editableBuilds.map {
                IncludedBuild(it.rootProject.name, it.rootProject.projectDirectory.absolutePath)
            })
        }
    }
}