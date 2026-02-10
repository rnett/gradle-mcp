package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectPath
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Example
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

class GradleExecutionTools(
    val gradle: GradleProvider,
) : McpServerComponent("Execution Tools", "Tools for executing Gradle tasks and running tests.") {
    @Serializable
    data class ExecuteCommandArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The Gradle command to run. Will be ran as if it had been passed directly to './gradlew'")
        val commandLine: List<String>,
        @Description("Whether to run with the --scan argument to publish a build scan. Will use scans.gradle.com if there is not a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false.")
        val scan: Boolean = false,
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    @OptIn(ExperimentalTime::class)
    val executeCommand by tool<ExecuteCommandArgs, String>(
        "run_gradle_command",
        """
            |Runs a Gradle command in the given project, just as if the command line had been passed directly to './gradlew'. Always prefer using this tool over invoking Gradle via the command line or shell.
            |Use the `lookup_*` tools to get detailed results after running the build.
            |Can be used to execute any Gradle tasks.
            |When running tests, prefer the `run_tests_with_gradle` tool.
            |The console output is included in the result. Show this to the user, as if they had ran the command themselves.
            |Can publish a Develocity Build Scan if requested. This is the preferred way to diagnose issues, using something like the Develocity MCP server.
        """.trimMargin(),
    ) {
        val result = gradle.doBuild(
            it.projectRoot,
            GradleInvocationArguments(additionalArguments = it.commandLine, publishScan = it.scan) + it.invocationArguments
        )

        if (!result.buildResult.isSuccessful) {
            isError = true
        }

        result.buildResult.toOutputString()
    }

    @Serializable
    data class ExecuteSingleTaskArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The absolute path of the Gradle task to run (e.g. ':build', ':subproject:test')")
        val taskPath: String,
        @Description("Additional arguments to pass to the task.")
        val arguments: List<String> = emptyList(),
        @Description("Whether to force the task to rerun by adding --rerun. Defaults to false.")
        val rerun: Boolean = false,
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    @OptIn(ExperimentalTime::class)
    val runSingleTaskAndGetOutput by tool<ExecuteSingleTaskArgs, String>(
        "run_single_task_and_get_output",
        """
            |Runs a single Gradle task and returns its output.
            |If the task fails, it will error and return the full build results's output.
            |If it succeeds, it will extract the task's specific output from the console output.
            |
            |### Useful Gradle tasks:
            |- `help`: Gets the Gradle version and shows other basic information
            |- `help --task <task>`: Gets detailed information about a specific Gradle task, including its arguments.
            |- `tasks`: Gets all available Gradle tasks
            |- `dependencies`: Gets all dependencies of a Gradle project.
            |  - Example: `run_single_task_and_get_output(taskPath=":my-project:dependencies")`
            |- `resolvableConfigurations`: Gets all resolvable configurations (i.e. configurations that pull dependencies).
            |- `dependencies --configuration <configuration name>`: Gets all dependencies of a specific configuration.
            |  - Example: `run_single_task_and_get_output(taskPath=":my-project:dependencies", arguments=["--configuration", "runtimeClasspath"])`
            |- `dependencyInsight --configuration <configuration name> --dependency <dependency prefix, of the dependency GAV slug>`: Gets detailed information about the resolution of specific dependencies.
            |  - Example: `run_single_task_and_get_output(taskPath=":my-project:dependencyInsight", arguments=["--configuration", "runtimeClasspath", "--dependency", "org.jetbrains.kotlin"])`
            |- `buildEnvironment`: Gets the Gradle build dependencies (plugins and buildscript dependencies) and JVM information.
            |- `javaToolchains`: Gets all available Java/JVM toolchains.
            |- `properties`: Gets all properties of a Gradle project. MAY CONTAIN SECRETS OR SENSITIVE INFORMATION.
            |- `artifactTransforms`: Gets all artifact transforms of the project.
            |- `outgoingVariants`: Gets all outgoing variants of the project (i.e. configurations that can be published or consumed from other projects).
        """.trimMargin(),
    ) {
        val additionalArgs = buildList {
            add(it.taskPath)
            addAll(it.arguments)
            if (it.rerun) {
                add("--rerun")
            }
        }

        val result = gradle.doBuild(
            it.projectRoot,
            GradleInvocationArguments(additionalArguments = additionalArgs) + it.invocationArguments
        )

        if (!result.buildResult.isSuccessful) {
            isError = true
            result.buildResult.toOutputString()
        } else {
            buildString {

                if (result.buildResult.taskOutputCapturingFailed) {
                    appendLine("Task output capturing failed. Task output may be incomplete or interleaved with other tasks.\n")
                }
                appendLine(result.buildResult.getTaskOutput(it.taskPath, true) ?: "Task output not found in console output. Build result:\n${result.buildResult.toOutputString()}")
            }
        }
    }

    @Description("A pattern to select tests. This is a prefix of the test class or method's fully qualified name. '*' wildcards are supported. Test classes may omit the package, e.g. `SomeClass` or `SomeClass.someMethod`. A filter of '*' will select all tests.")
    @Example("com.example.MyTestClass")
    @Example("com.example.MyTestClass.myTestMethod")
    @Example("com.example.http.*")
    @Example("com.example.MyTestClass.myTestMethod")
    @Example("MyTestClass")
    @Example("MyTestClass.myTestMethod")
    @Example("*IntegTest)")
    @JvmInline
    @Serializable
    value class TestPattern(val pattern: String)


    @Serializable
    data class ExecuteSingleTestArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        @Description("The Gradle task to run. REAUIRED. Must be a test task. The usual test task is `test`, but THIS IS NOT USED AS A DEFAULT AND MUST BE SPECIFIED.")
        val taskName: String,
        @Description("The tests to run, as test patterns. The default is all tests. Note that this is the task name (e.g. `test`) not the task path (e.g. `:test`).")
        val tests: List<TestPattern> = emptyList(),
        @Description("Whether to run with the --scan argument to publish a build scan. Will use scans.gradle.com if there is not a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended when possible. Defaults to false.")
        val scan: Boolean = false,
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    @OptIn(ExperimentalTime::class)
    val runSingleTest by tool<ExecuteSingleTestArgs, String>(
        "run_tests_with_gradle",
        """
            |Runs a single test task, with an option to filter which tests to run. Always prefer using this tool over invoking Gradle via the command line or shell.
            |Use the `lookup_*` tools to get detailed results after running the build.
            |The console output is included in the result. Show this to the user, as if they had ran the command themselves.
            |Can publish a Develocity Build Scan if requested. This is the preferred way to diagnose issues and test failures, using something like the Develocity MCP server.
            |The typical test task is `test`.  At least one task is required. A task with no patterns will run all tests.
            |If there are more than 1000 tests, the results will be truncated.  Use `lookup_build_tests_summary` or `lookup_build_test_details` to get the results you care about.
        """.trimMargin(),
    ) {
        val result = gradle.doTests(
            it.projectRoot,
            mapOf(it.projectPath.taskPath(it.taskName) to it.tests.map { it.pattern }.toSet().ifEmpty { setOf("*") }),
            GradleInvocationArguments(publishScan = it.scan) + it.invocationArguments
        )

        if (!result.buildResult.isSuccessful) {
            isError = true
        }

        result.buildResult.toOutputString()
    }


    @Serializable
    data class ExecuteManyTestsArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("A map (i.e. JSON object) of each absolute task paths of the test tasks to run (e.g. `:test`, `:project-a:sub-b:test`) to the test patterns for the tests to run for that task (e.g. `com.example.*`, `*MyTest*`).  The typical test task is `:test`.  At least one task is required. A task with no patterns will run all tests in that task.")
        val testsExecutions: Map<String, Set<TestPattern>>,
        @Description("Whether to run with the --scan argument to publish a build scan. Will use scans.gradle.com if there is not a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false.")
        val scan: Boolean = false,
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    @OptIn(ExperimentalTime::class)
    val runTests by tool<ExecuteManyTestsArgs, String>(
        "run_many_test_tasks_with_gradle",
        """
            |Runs may test tasks, each with their own test filters. To run a single test task, use the `run_test_task` tool. Always prefer using this tool over invoking Gradle via the command line or shell.
            |Use the `lookup_*` tools to get detailed results after running the build.
            |Note that the test tasks passed must be absolute paths (i.e. including the project paths).
            |The console output is included in the result. Show this to the user, as if they had ran the command themselves.
            |Can publish a Develocity Build Scan if requested. This is the preferred way to diagnose issues and test failures, using something like the Develocity MCP server.
            |The `tests` parameter is REQUIRED, and is simply a map (i.e. JSON object) of each test task to run (e.g. `:test`, `:project-a:sub-b:test`), to the test patterns for the tests to run for that task (e.g. `com.example.*`, `*MyTest*`).
            |The typical test task is `:test`.  At least one task is required. A task with no patterns will run all tests.
            |If there are more than 1000 tests, the results will be truncated.  Use `lookup_build_tests_summary` or `lookup_build_test_details` to get the results you care about.
        """.trimMargin(),
    ) {
        if (it.testsExecutions.isEmpty()) {
            throw IllegalArgumentException("At least one test task is required.")
        }

        val result = gradle.doTests(
            it.projectRoot,
            it.testsExecutions.mapValues { it.value.map { it.pattern }.toSet().ifEmpty { setOf("*") } },
            GradleInvocationArguments(publishScan = it.scan) + it.invocationArguments,
        )

        if (!result.buildResult.isSuccessful) {
            isError = true
        }

        result.buildResult.toOutputString()
    }

}