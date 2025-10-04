package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectPath
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable

class GradleTaskWrapperTools(
    val gradle: GradleProvider,
) : McpServerComponent() {

    interface BaseProjectInput {
        val projectRoot: GradleProjectRootInput
        val projectPath: GradleProjectPath
        val invocationArgs: GradleInvocationArguments
    }

    @Serializable
    data class ProjectInput(
        override val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        override val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        override val invocationArgs: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    ) : BaseProjectInput

    fun basicTaskWrapperTool(name: String, description: String, task: String) = taskWrapperTool<ProjectInput>(name, description, task) { emptyList() }

    inline fun <reified I : BaseProjectInput> taskWrapperTool(name: String, description: String, task: String, crossinline taskArgs: (I) -> List<String>) = tool<I, String>(
        name,
        description.trimEnd('.') + ".  Works by executing the `$task` task of the given project."
    ) { args ->
        val task = args.projectPath.taskPath(task)

        val commandLine = listOf("-q", task) + taskArgs(args)

        val result = gradle.doBuild(
            args.projectRoot,
            args.invocationArgs.copy(additionalArguments = commandLine)
        )

        (if (result.isSuccessful) {
            return@tool result.consoleOutput
        } else {
            isError = true
            addAdditionalContent(TextContent(result.consoleOutput))
            return@tool "Error executing Gradle command `commandLine`. Try running it via run_gradle_command for more information and diagnostics, especially if you ask it to publish a build scan."
        })
    }

    @Serializable
    data class DependenciesInput(
        override val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        override val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        @Description("The configuration to get dependencies from.  Defaults to all.")
        val configuration: String? = null,
        override val invocationArgs: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    ) : BaseProjectInput

    val getDependencies by taskWrapperTool<DependenciesInput>(
        "get_dependencies",
        """
            |Gets all dependencies of a Gradle project, optionally filtered by configuration. Use `get_resolvable_configurations` to get all configurations.  Use `get_build_dependencies` to get the Gradle build dependencies (i.e. plugins and buildscript dependencies).
            |In the output, a `(*)` indicates that the dependency tree is repeated because the dependency is used multiple times. Only the first occurence in the report expands the tree.
            |A `(c)` indicates that a dependency is only a constraint, not an actual dependency, and a `(n)` indicates that it could not be resolved.
            |WARNING: The response can be quite large. Prefer specifying a configuration and/or using `get_dependency_resolution_information` when possible.
        """.trimMargin(),
        "dependencies"
    ) {
        if (it.configuration == null) emptyList() else listOf("--configuration", it.configuration)
    }

    @Serializable
    data class DependenciesInsightInput(
        override val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        override val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        @Description("The configuration that resolves the dependency. Required. Use `get_dependencies` to see which dependencies are present in which configurations, and `get_resolvable_configurations` to see all configurations.")
        val configuration: String,
        @Description("The prefix used to select dependencies to report about. Required. Compared to the dependency's `group:artifact` - if it is a prefix, that dependency will be included.")
        val dependencyPrefix: String,
        @Description("If true (false is default), only show a single requirement path for the reported on dependencies.")
        val singlePath: Boolean = false,
        @Description("If true (false is default), show all variants of the dependency, not just variant that was selected.")
        val allVariants: Boolean = false,
        override val invocationArgs: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    ) : BaseProjectInput

    val getDependencyInsight by taskWrapperTool<DependenciesInsightInput>(
        "get_dependency_resolution_information",
        """
            |Gets detailed information about the resolution of the specific dependencies. Any dependencies with a `group:artifact` that start with the `dependencyPrefix` will be included in the report.
            |The configuration
        """.trimMargin(),
        "dependencyInsight"
    ) {
        buildList {
            add("--configuration")
            add(it.configuration)
            add("--dependency")
            add(it.dependencyPrefix)
            if (it.singlePath) add("--single-path")
            if (it.allVariants) add("--all-variants")
        }
    }

    val getBuildDependencies by basicTaskWrapperTool("get_build_dependencies", "Gets the Gradle build dependencies of a Gradle project, as well as some information about the JVM used to execute the build.", "buildEnvironment")

    val getResolvableConfigurations by basicTaskWrapperTool("get_resolvable_configurations", "Gets all resolvable configurations of a Gradle project.", "resolvableConfigurations")

    val getAvailableToolchains by basicTaskWrapperTool("get_available_toolchains", "Gets all available Java/JVM toolchains for a Gradle project. Also includes whether auto-detection and auto-downloading are enabled.", "javaToolchains")

    val getProperties by basicTaskWrapperTool("get_properties", "Gets all properties of a Gradle project. WARNING: may return sensitive information like configured credentials.", "properties")

    val getArtifactTransforms by basicTaskWrapperTool("get_artifact_transforms", "Gets all artifact transforms of a Gradle project.", "artifactTransforms")

    val outgoingVariants by basicTaskWrapperTool("get_outgoing_variants", "Gets all outgoing variants of a Gradle project. These are configurations that may be consumed by other projects or published.", "outgoingVariants")
}