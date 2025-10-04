package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Example
import io.modelcontextprotocol.kotlin.sdk.Annotations
import io.modelcontextprotocol.kotlin.sdk.Role
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

class GradleExecutionTools(
    val gradle: GradleProvider,
) : McpServerComponent() {
    @Serializable
    data class ExecuteCommandArgs(
        val projectRoot: GradleProjectRoot,
        @Description("The Gradle command to run. Will be ran as if it had been passed directly to './gradlew'")
        val commandLine: List<String>,
        @Description("Whether to run with the --scan argument to publish a build scan. Requires a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false.")
        val scan: Boolean = false,
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    @OptIn(ExperimentalTime::class)
    val executeCommand by tool<ExecuteCommandArgs, BuildResultSummary>(
        "run_gradle_command",
        """
            |Runs a Gradle command in the given project, just as if the command line had been passed directly to './gradlew'.
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

        if (!result.isSuccessful) {
            isError = true
        }

        result.toSummary()
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
        val projectRoot: GradleProjectRoot,
        val projectPath: GradleProjectPath = GradleProjectPath.DEFAULT,
        @Description("The Gradle task to run. REAUIRED. Must be a test task. The usual test task is `test`, but THIS IS NOT USED AS A DEFAULT AND MUST BE SPECIFIED.")
        val taskName: String,
        @Description("The tests to run, as test patterns. The default is all tests. Note that this is the task name (e.g. `test`) not the task path (e.g. `:test`).")
        val tests: List<TestPattern> = emptyList(),
        @Description("Whether to run with the --scan argument to publish a build scan. Requires a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false.")
        val scan: Boolean = false,
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    //TODO may need to truncate even the test name list in this.
    @Serializable
    data class TestResultOutput(
        val testsSummary: TestResultsSummary,
        val buildResult: BuildResultSummary
    )

    private fun BuildResult.toTestResultOutput(maxResults: Int = 1000) = TestResultOutput(
        this.testResults.toSummary(maxResults),
        this.toSummary()
    )

    @OptIn(ExperimentalTime::class)
    val runSingleTest by tool<ExecuteSingleTestArgs, TestResultOutput>(
        "run_tests",
        """
            |Runs a single test task, with an option to filter which tests to run.
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

        if (!result.isSuccessful) {
            isError = true
        }

        result.toTestResultOutput()
    }


    @Serializable
    data class ExecuteManyTestsArgs(
        val projectRoot: GradleProjectRoot,
        @Description("A map (i.e. JSON object) of each absolute task paths of the test tasks to run (e.g. `:test`, `:project-a:sub-b:test`) to the test patterns for the tests to run for that task (e.g. `com.example.*`, `*MyTest*`).  The typical test task is `:test`.  At least one task is required. A task with no patterns will run all tests in that task.")
        val testsExecutions: Map<String, Set<TestPattern>>,
        @Description("Whether to run with the --scan argument to publish a build scan. Requires a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false.")
        val scan: Boolean = false,
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    @OptIn(ExperimentalTime::class)
    val runTests by tool<ExecuteManyTestsArgs, TestResultOutput>(
        "run_many_test_tasks",
        """
            |Runs may test tasks, each with their own test filters. To run a single test task, use the `run_test_task` tool.
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

        if (!result.isSuccessful) {
            isError = true
        }

        result.toTestResultOutput()
    }

}