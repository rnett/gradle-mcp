package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectPath
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.matches
import dev.rnett.gradle.mcp.gradle.throwFailure
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import kotlinx.serialization.Serializable
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.gradle.GradleBuild

class GradleIntrospectionTools(
    val gradle: GradleProvider,
) : McpServerComponent("Introspection Tools", "Tools for inspecting Gradle build configuration.") {

    @Serializable
    data class DescribeProjectArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        val invocationArgs: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    val describeProject by tool<DescribeProjectArgs, String>(
        "describe_project",
        "Describes a Gradle project or subproject. Includes the tasks and child projects. Can be used to query available tasks."
    ) { args ->
        gradle.getBuildModel<GradleProject>(args.projectRoot, args.invocationArgs).throwFailure().let { (id, projectModel) ->
            val project = projectModel.findByPath(args.projectPath.path)
                ?: throw IllegalArgumentException("Project with project path \"${args.projectPath}\" not found")
            buildString {
                appendLine("Project: ${project.path}")
                appendLine("Name: ${project.name}")
                if (project.description != null) {
                    appendLine("Description: ${project.description}")
                }
                appendLine("Project directory: ${project.projectDirectory?.absolutePath}")
                appendLine("Build script: ${project.buildScript?.sourceFile?.absolutePath}")

                if (project.children.isNotEmpty()) {
                    appendLine()
                    appendLine("Child projects:")
                    project.children.forEach {
                        appendLine("  - ${it.path}")
                    }
                }

                val tasksByGroup = project.tasks.groupBy { it.group ?: "other" }
                if (tasksByGroup.isNotEmpty()) {
                    appendLine()
                    appendLine("Tasks:")
                    tasksByGroup.toSortedMap(compareBy { if (it == "other") "zzz" else it }).forEach { (group, tasks) ->
                        appendLine("  [$group]")
                        tasks.forEach { task ->
                            append("    ${task.name}")
                            if (task.description != null) {
                                append(" - ${task.description}")
                            }
                            appendLine()
                        }
                    }
                }
            }
        }
    }

    @Serializable
    data class IncludedBuildsArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        val invocationArgs: GradleInvocationArguments = GradleInvocationArguments.DEFAULT,
    )

    val getIncludedBuilds by tool<IncludedBuildsArgs, String>(
        "get_included_builds",
        "Gets the included builds of a Gradle project."
    ) {
        gradle.getBuildModel<GradleBuild>(it.projectRoot, it.invocationArgs).throwFailure().let { (id, buildModel) ->
            buildString {
                val builds = buildModel.editableBuilds
                if (builds.isEmpty()) {
                    appendLine("No included builds found.")
                } else {
                    appendLine("Included builds:")
                    builds.forEach {
                        appendLine("  - ${it.rootProject.name} (${it.rootProject.projectDirectory.absolutePath})")
                    }
                }
            }
        }
    }


    data class Publication(
        val group: String,
        val name: String,
        val version: String
    )

    @Serializable
    data class ProjectPublicationsArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        val invocationArgs: GradleInvocationArguments = GradleInvocationArguments.DEFAULT,
    )

    val getProjectPublications by tool<ProjectPublicationsArgs, String>(
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

        buildString {
            if (publications.isEmpty()) {
                appendLine("No publications found for project \"${args.projectPath}\".")
            } else {
                appendLine("Publications for project \"${args.projectPath}\":")
                publications.sortedWith(compareBy({ it.group }, { it.name }, { it.version })).forEach {
                    appendLine("  - ${it.group}:${it.name}:${it.version}")
                }
            }
        }
    }

}