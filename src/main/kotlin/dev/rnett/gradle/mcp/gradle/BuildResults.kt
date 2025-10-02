package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.mapToSet
import dev.rnett.gradle.mcp.tools.GradleInvocationArguments
import dev.rnett.gradle.mcp.tools.GradleProjectRoot
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import kotlin.time.Duration


@Serializable
@Description("The result of a Gradle build.")
data class BuildResult(
    @Description("The console output of the build.")
    val output: String,
    @Description("All Gradle Build Scans published during the build.")
    val publishedScans: List<GradleBuildScan>,
    @Description("Detailed test results, if the build ran tests.")
    val testResults: TestResults?,
    @Description("The failures reported by the build, if it failed. Includes the failures of test tasks if tests failed.")
    val failures: List<Failure>?,
    @Description("True if the build was a success, false if it was a failure")
    val isSuccessful: Boolean = failures == null
) {

    @Description("A failure encountered during a Gradle build or test.")
    @Serializable
    data class Failure(
        @Description("A short message describing the failure")
        val message: String?,
        @Description("A longer description of the details of the failure")
        val description: String?,
        @Description("Other failures that caused this failure. Only contains the failure message, check the result for matching failures.")
        val causesMessages: List<String>,
        @Description("Problems in the build that caused this failure")
        val problems: List<Problem>,
    )

    @Serializable
    data class TestResults(
        val passed: Set<TestResult>,
        val skipped: Set<TestResult>,
        val failed: Set<TestResult>,
    )

    @Serializable
    data class TestResult(
        @Description("The name of the test.")
        val testName: String,
        @Description("Console output of the test, if it was captured.")
        val output: String?,
        @Description("How long the test took to execute")
        val executionDuration: Duration,
        @Description("The test's failures, if it failed")
        val failures: List<Failure>?,
    )
}


private fun org.gradle.tooling.Failure.toModel(): BuildResult.Failure {
    return BuildResult.Failure(
        message,
        description,
        causes.map { it.message },
        problems.map { it.toModel() }
    )
}

private fun TestResult.toModel(): BuildResult.TestResult {
    return BuildResult.TestResult(
        testName,
        output,
        duration,
        failures?.map { it.toModel() },
    )
}

fun GradleResult<Unit>.toBuildResult(output: String, includeFailures: Boolean): BuildResult {
    val modelTestResults = testResults?.let {
        BuildResult.TestResults(
            it.passed.mapToSet { it.toModel() },
            it.skipped.mapToSet { it.toModel() },
            it.failed.mapToSet { it.toModel() },
        )
    }

    return when (outcome) {
        is GradleResult.Failure -> BuildResult(output, publishedScans, modelTestResults, if (includeFailures) outcome.error.failures.map { it.toModel() } else emptyList())
        is GradleResult.Success -> BuildResult(output, publishedScans, modelTestResults, null)
    }
}

suspend fun GradleProvider.runBuildAndGetOutput(
    projectRoot: GradleProjectRoot,
    captureFailedTestOutput: Boolean,
    captureAllTestOutput: Boolean,
    invocationArgs: GradleInvocationArguments,
    includeFailures: Boolean,
    tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
    stdoutLineHandler: ((String) -> Unit)? = null,
    stderrLineHandler: ((String) -> Unit)? = null,
): BuildResult {
    val result = StringBuilder()
    return runBuild(
        projectRoot,
        invocationArgs,
        captureFailedTestOutput,
        captureAllTestOutput,
        tosAccepter,
        stdoutLineHandler = {
            result.appendLine(it)
            stdoutLineHandler?.invoke(it)
        },
        stderrLineHandler = {
            result.appendLine("ERR:  $it")
            stderrLineHandler?.invoke(it)
        }
    ).toBuildResult(result.toString(), includeFailures)
}

suspend fun GradleProvider.runTestsAndGetOutput(
    projectRoot: GradleProjectRoot,
    testPatterns: Map<String, Set<String>>,
    captureFailedTestOutput: Boolean,
    captureAllTestOutput: Boolean,
    invocationArgs: GradleInvocationArguments,
    includeFailures: Boolean,
    tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
    stdoutLineHandler: ((String) -> Unit)? = null,
    stderrLineHandler: ((String) -> Unit)? = null,
): BuildResult {
    val result = StringBuilder()
    return runTests(
        projectRoot,
        testPatterns,
        captureFailedTestOutput,
        captureAllTestOutput,
        invocationArgs,
        tosAccepter,
        stdoutLineHandler = {
            result.appendLine(it)
            stdoutLineHandler?.invoke(it)
        },
        stderrLineHandler = {
            result.appendLine("ERR:  $it")
            stderrLineHandler?.invoke(it)
        }
    ).toBuildResult(result.toString(), includeFailures)
}