package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.throwFailure
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.IdeaSourceDirectory

class GradleIntrospectionTools(
    val gradle: GradleProvider,
) : McpServerComponent() {

    companion object {
        const val BUILD_ID_DESCRIPTION = "The build ID of the build used to query this information."
    }

    @Serializable
    data class GradleBuildEnvironment(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId?,
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
            @Description("The JVM arguments used by this Gradle project")
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
            it.projectRoot,
            it.invocationArgs,
            requiresGradleProject = false
        ).throwFailure().let { (id, it) ->
            GradleBuildEnvironment(
                id,
                GradleBuildEnvironment.GradleInfo(it.gradle.gradleUserHome.absolutePath, it.gradle.gradleVersion),
                GradleBuildEnvironment.JavaInfo(it.java.javaHome.absolutePath, it.java.jvmArguments)
            )
        }
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
        val projectRoot: GradleProjectRoot,
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
        val projectRoot: GradleProjectRoot,
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
        val projectRoot: GradleProjectRoot,
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

        val publications = publicationsModel.publications
            .filter { it.projectIdentifier.matches(args.projectRoot, args.projectPath) }
            .map { Publication(it.id.group, it.id.name, it.id.version) }
            .toSet()

        ProjectPublicationsResult(id, publications)
    }

    @Serializable
    enum class SourceDirectoryType {
        @Description("A source directory (main sources)")
        SOURCE,

        @Description("A test source directory")
        TEST_SOURCE,

        @Description("A resource directory (main resources)")
        RESOURCE,

        @Description("A test resource directory")
        TEST_RESOURCE
    }

    @Serializable
    @Description("A source directory in a Gradle project")
    data class SourceDirectoryEntry(
        @Description("Absolute path to the directory")
        val path: String,
        @Description("The type/category of this directory.")
        val type: SourceDirectoryType,
        @Description("Whether this directory is generated")
        val isGenerated: Boolean,
        @Description("The java language level for this directory. DOES NOT MEAN IT IS A JAVA SOURCE SET.")
        val javaLanguageLevel: String?
    )

    @Serializable
    data class ProjectSourceDirectoriesResult(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId?,
        @Description("All source directories known by Gradle.")
        val directoriesByModulePath: List<SourceDirectoryEntry>
    )

    @Serializable
    data class ProjectSourceDirectoriesArgs(
        val projectRoot: GradleProjectRoot,
        val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        val invocationArgs: GradleInvocationArguments = GradleInvocationArguments.DEFAULT,
    )

    private fun IdeaSourceDirectory.toEntry(type: SourceDirectoryType, javaLanguageLevel: String?) = SourceDirectoryEntry(
        directory.absolutePath,
        type,
        isGenerated,
        javaLanguageLevel
    )

    val getProjectSourceDirectories by tool<ProjectSourceDirectoriesArgs, ProjectSourceDirectoriesResult>(
        "get_project_source_directories",
        "Gets source/test/resource directories for the project. Sometimes non-JVM source directories will also exist that aren't known to Gradle. Note that the javaLanguageLevel setting does not necessarily mean the directory is a Java source set."
    ) { args ->
        val (id, ideaProject) = gradle.getBuildModel<org.gradle.tooling.model.idea.BasicIdeaProject>(
            args.projectRoot,
            args.invocationArgs
        ).throwFailure()

        ProjectSourceDirectoriesResult(
            id,
            ideaProject.modules
                .asSequence()
                .filter { it.projectIdentifier.matches(args.projectRoot, args.projectPath) }
                .flatMap { module ->
                    module.javaLanguageSettings.languageLevel.majorVersion
                    module.contentRoots.asSequence().flatMap {
                        it.sourceDirectories?.map { it.toEntry(SourceDirectoryType.SOURCE, module.javaLanguageSettings?.languageLevel?.majorVersion) }.orEmpty() +
                                it.resourceDirectories?.map { it.toEntry(SourceDirectoryType.RESOURCE, module.javaLanguageSettings?.languageLevel?.majorVersion) }.orEmpty() +
                                it.testDirectories?.map { it.toEntry(SourceDirectoryType.TEST_SOURCE, module.javaLanguageSettings?.languageLevel?.majorVersion) }.orEmpty() +
                                it.testResourceDirectories?.map { it.toEntry(SourceDirectoryType.TEST_RESOURCE, module.javaLanguageSettings?.languageLevel?.majorVersion) }.orEmpty()
                    }
                }
                .distinct()
                .toList()
        )
    }

}