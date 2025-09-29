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
        @Description("Whether to capture the console output of failed tests. Defaults to true.")
        val captureFailedTestOutput: Boolean = true,
        @Description("Whether to capture the console output of all tests. Defaults to false.")
        val captureAllTestOutput: Boolean = false,
        @Description("Whether to run with the --scan argument to publish a build scan. Requires a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false.")
        val scan: Boolean = false,
        @Description("Whether to include failure information in the result, if the build fails. Defaults to false. The information can be helpful in diagnosing failures, but is very verbose.")
        val includeFailureInformation: Boolean = false,
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    @OptIn(ExperimentalTime::class)
    val executeCommand by tool<ExecuteCommandArgs, BuildResult>(
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
            it.captureFailedTestOutput,
            it.captureAllTestOutput,
            GradleInvocationArguments(additionalArguments = it.commandLine, publishScan = it.scan) + it.invocationArguments,
            it.includeFailureInformation
        )

        addAdditionalContent(TextContent(result.output, Annotations(listOf(Role.user), null, 1.0)))

        if (!result.isSuccessful) {
            isError = true
        }

        result
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
    data class ExecuteTestsArgs(
        val projectRoot: GradleProjectRoot,
        @Description("A map of each test task to run (e.g. `:test`), to the test patterns for the tests to run for that task (e.g. `com.example.*`).  The typical test task is `:test`.  At least one task is required. A task with no patterns will run all tests.")
        val testPatterns: Map<String, Set<TestPattern>>,
        @Description("Whether to capture the console output of failed tests. Defaults to true.")
        val captureFailedTestOutput: Boolean = true,
        @Description("Whether to capture the console output of all tests. Defaults to false.")
        val captureAllTestOutput: Boolean = false,
        @Description("Whether to run with the --scan argument to publish a build scan. Requires a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false.")
        val scan: Boolean = false,
        @Description("Whether to include build (not test) failure information in the result, if the build fails. Defaults to false. The information can be helpful in diagnosing failures, but is very verbose.")
        val includeNonTestFailureInformation: Boolean = false,
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    @OptIn(ExperimentalTime::class)
    val runTests by tool<ExecuteTestsArgs, BuildResult>(
        "run_tests_with_gradle",
        """
            |Runs some tests in the given project via Gradle.
            |The console output is included in the result. Show this to the user, as if they had ran the command themselves.
            |Can publish a Develocity Build Scan if requested. This is the preferred way to diagnose issues and test failures, using something like the Develocity MCP server.
        """.trimMargin(),
    ) {
        if (it.testPatterns.isEmpty()) {
            throw IllegalArgumentException("At least one test task is required.")
        }

        if (it.testPatterns.all { it.value.isEmpty() }) {
            throw IllegalArgumentException("At least one test pattern is required.")
        }

        val result = gradle.doTests(
            it.projectRoot,
            it.testPatterns.mapValues { it.value.map { it.pattern }.toSet().ifEmpty { setOf("*") } },
            it.captureFailedTestOutput,
            it.captureAllTestOutput,
            GradleInvocationArguments(publishScan = it.scan) + it.invocationArguments,
            it.includeNonTestFailureInformation
        )

        addAdditionalContent(TextContent(result.output, Annotations(listOf(Role.user), null, 1.0)))

        if (!result.isSuccessful) {
            isError = true
        }

        result
    }

}