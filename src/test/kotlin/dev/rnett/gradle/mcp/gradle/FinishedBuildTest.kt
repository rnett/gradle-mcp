package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.Failure
import dev.rnett.gradle.mcp.gradle.build.FailureId
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.GradleBuildScan
import dev.rnett.gradle.mcp.gradle.build.TestOutcome
import dev.rnett.gradle.mcp.gradle.build.TestResult
import dev.rnett.gradle.mcp.gradle.build.TestResults
import dev.rnett.gradle.mcp.tools.toSummary
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class GradleBuildScanTest {

    @Test
    fun `can create build scan from components`() {
        val scan = GradleBuildScan(
            url = "https://scans.gradle.com/s/abc123",
            id = "abc123",
            develocityInstance = "https://scans.gradle.com"
        )
        assert(scan.url == "https://scans.gradle.com/s/abc123")
        assert(scan.id == "abc123")
        assert(scan.develocityInstance == "https://scans.gradle.com")
    }

    @Test
    fun `can parse build scan from scans gradle com URL`() {
        val scan = GradleBuildScan.fromUrl("https://scans.gradle.com/s/abc123")
        assert(scan.url == "https://scans.gradle.com/s/abc123")
        assert(scan.id == "abc123")
        assert(scan.develocityInstance == "https://scans.gradle.com")
    }

    @Test
    fun `can parse build scan from gradle com shortlink`() {
        val scan = GradleBuildScan.fromUrl("https://gradle.com/s/abc123")
        assert(scan.url == "https://scans.gradle.com/s/abc123")
        assert(scan.id == "abc123")
        assert(scan.develocityInstance == "https://scans.gradle.com")
    }

    @Test
    fun `can parse build scan from custom develocity instance`() {
        val scan = GradleBuildScan.fromUrl("https://ge.company.com/s/xyz789")
        assert(scan.url == "https://ge.company.com/s/xyz789")
        assert(scan.id == "xyz789")
        assert(scan.develocityInstance == "https://ge.company.com")
    }
}

class FinishedBuildTest {
    private val args = GradleInvocationArguments.DEFAULT

    @Test
    fun `can create successful build result`() {
        val testResults = TestResults(
            passed = emptySet(),
            skipped = emptySet(),
            failed = emptySet()
        )

        val result = FinishedBuild(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "BUILD SUCCESSFUL",
            publishedScans = emptyList(),
            testResults = testResults,
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )

        assert(result.outcome is BuildOutcome.Success)
        assert(result.consoleOutput == "BUILD SUCCESSFUL")
        assert(result.id != null)
    }

    @Test
    fun `can create failed build result`() {
        val testResults = TestResults(
            passed = emptySet(),
            skipped = emptySet(),
            failed = emptySet()
        )

        val failure = Failure(
            id = FailureId("failure-1"),
            message = "Build failed",
            description = "Task execution failed",
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val result = FinishedBuild(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "BUILD FAILED",
            publishedScans = emptyList(),
            testResults = testResults,
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Failed(listOf(failure)),
            finishTime = Clock.System.now()
        )

        assert(result.outcome is BuildOutcome.Failed)
        assert((result.outcome as BuildOutcome.Failed).failures.size == 1)
    }

    @Test
    fun `test results tracks counts correctly`() {
        val passed = setOf(
            TestResult("test1", null, 100.milliseconds, null, TestOutcome.PASSED),
            TestResult("test2", null, 200.milliseconds, null, TestOutcome.PASSED)
        )
        val skipped = setOf(
            TestResult("test3", null, 0.milliseconds, null, TestOutcome.SKIPPED)
        )
        val failed = setOf(
            TestResult("test4", "output", 150.milliseconds, emptyList(), TestOutcome.FAILED)
        )

        val testResults = TestResults(
            passed = passed,
            skipped = skipped,
            failed = failed
        )

        assert(testResults.passed.size == 2)
        assert(testResults.skipped.size == 1)
        assert(testResults.failed.size == 1)
        assert(testResults.totalCount == 4)
        assert(!testResults.isEmpty)
    }

    @Test
    fun `test results isEmpty is true when no tests`() {
        val testResults = TestResults(
            passed = emptySet(),
            skipped = emptySet(),
            failed = emptySet()
        )

        assert(testResults.isEmpty)
        assert(testResults.totalCount == 0)
    }

    @Test
    fun `test results all sequence contains all tests`() {
        val passed = setOf(
            TestResult("test1", null, 100.milliseconds, null, TestOutcome.PASSED)
        )
        val skipped = setOf(
            TestResult("test2", null, 0.milliseconds, null, TestOutcome.SKIPPED)
        )
        val failed = setOf(
            TestResult("test3", "output", 150.milliseconds, emptyList(), TestOutcome.FAILED)
        )

        val testResults = TestResults(
            passed = passed,
            skipped = skipped,
            failed = failed
        )

        val allTests = testResults.all.toList()
        assert(allTests.size == 3)
        assert(allTests.any { it.testName == "test1" })
        assert(allTests.any { it.testName == "test2" })
        assert(allTests.any { it.testName == "test3" })
    }

    @Test
    fun `failure can flatten nested causes`() {
        val innerFailure = Failure(
            id = FailureId("inner"),
            message = "Inner failure",
            description = null,
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val middleFailure = Failure(
            id = FailureId("middle"),
            message = "Middle failure",
            description = null,
            causes = listOf(innerFailure),
            problemAggregations = emptyMap()
        )

        val outerFailure = Failure(
            id = FailureId("outer"),
            message = "Outer failure",
            description = null,
            causes = listOf(middleFailure),
            problemAggregations = emptyMap()
        )

        val flattened = outerFailure.flatten().toList()
        assert(flattened.size == 3)
        assert(flattened.any { it.id.id == "outer" })
        assert(flattened.any { it.id.id == "middle" })
        assert(flattened.any { it.id.id == "inner" })
    }

    @Test
    fun `consoleOutputLines lazily splits console output`() {
        val result = FinishedBuild(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "Line 1\nLine 2\nLine 3",
            publishedScans = emptyList(),
            testResults = TestResults(emptySet(), emptySet(), emptySet()),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )

        val lines = result.consoleOutputLines
        assert(lines.size == 3)
        assert(lines[0] == "Line 1")
        assert(lines[1] == "Line 2")
        assert(lines[2] == "Line 3")
    }

    @Test
    fun `allTestFailures extracts failures from test results`() {
        val testFailure = Failure(
            id = FailureId("test-failure"),
            message = "Test failed",
            description = null,
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val testResult = TestResult(
            testName = "failingTest",
            consoleOutput = null,
            executionDuration = 100.milliseconds,
            failures = listOf(testFailure),
            status = TestOutcome.FAILED
        )

        val result = FinishedBuild(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "",
            publishedScans = emptyList(),
            testResults = TestResults(
                passed = emptySet(),
                skipped = emptySet(),
                failed = setOf(testResult)
            ),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )

        assert(result.allTestFailures.size == 1)
        assert(result.allTestFailures.containsKey(FailureId("test-failure")))
    }

    @Test
    fun `allBuildFailures excludes test failures`() {
        val testFailure = Failure(
            id = FailureId("test-failure"),
            message = "Test failed",
            description = null,
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val buildFailure = Failure(
            id = FailureId("build-failure"),
            message = "Build failed",
            description = null,
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val testResult = TestResult(
            testName = "failingTest",
            consoleOutput = null,
            executionDuration = 100.milliseconds,
            failures = listOf(testFailure),
            status = TestOutcome.FAILED
        )

        val result = FinishedBuild(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "",
            publishedScans = emptyList(),
            testResults = TestResults(
                passed = emptySet(),
                skipped = emptySet(),
                failed = setOf(testResult)
            ),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Failed(listOf(buildFailure)),
            finishTime = Clock.System.now()
        )

        assert(result.allBuildFailures.size == 1)
        assert(result.allBuildFailures.containsKey(FailureId("build-failure")))
        assert(!result.allBuildFailures.containsKey(FailureId("test-failure")))
    }

    @Test
    fun `allFailures combines test and build failures`() {
        val testFailure = Failure(
            id = FailureId("test-failure"),
            message = "Test failed",
            description = null,
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val buildFailure = Failure(
            id = FailureId("build-failure"),
            message = "Build failed",
            description = null,
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val testResult = TestResult(
            testName = "failingTest",
            consoleOutput = null,
            executionDuration = 100.milliseconds,
            failures = listOf(testFailure),
            status = TestOutcome.FAILED
        )

        val result = FinishedBuild(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "",
            publishedScans = emptyList(),
            testResults = TestResults(
                passed = emptySet(),
                skipped = emptySet(),
                failed = setOf(testResult)
            ),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Failed(listOf(buildFailure)),
            finishTime = Clock.System.now()
        )

        assert(result.allFailures.size == 2)
        assert(result.allFailures.containsKey(FailureId("test-failure")))
        assert(result.allFailures.containsKey(FailureId("build-failure")))
    }

    @Test
    fun `problems toSummary correctly aggregates and calculates totalCount`() {
        val problemAggregations = mapOf(
            ProblemSeverity.ERROR to listOf(
                ProblemAggregation(
                    ProblemAggregation.ProblemDefinition(ProblemId("e1"), "Error 1", ProblemSeverity.ERROR, null),
                    listOf(
                        ProblemAggregation.ProblemOccurence("d1", emptyList(), emptyList(), emptyList()),
                        ProblemAggregation.ProblemOccurence("d2", emptyList(), emptyList(), emptyList())
                    )
                )
            ),
            ProblemSeverity.WARNING to listOf(
                ProblemAggregation(
                    ProblemAggregation.ProblemDefinition(ProblemId("w1"), "Warning 1", ProblemSeverity.WARNING, null),
                    listOf(
                        ProblemAggregation.ProblemOccurence("d3", emptyList(), emptyList(), emptyList())
                    )
                )
            )
        )

        val summary = problemAggregations.values.flatten().toSummary()
        assert(summary.errorCounts[ProblemId("e1")]?.occurences == 2)
        assert(summary.warningCounts[ProblemId("w1")]?.occurences == 1)
        assert(summary.totalCount == 3)
    }

    @Test
    fun `getTaskOutput extracts output for a specific task`() {
        val consoleOutput = """
            |> Task :help
            |Welcome to Gradle 8.5.
            |
            |To run a build, run gradlew <task> ...
            |
            |To see a list of available tasks, run gradlew tasks
            |
            |To see more detail about a task, run gradlew help --task <task>
            |
            |To see a list of command-line options, run gradlew --help
            |
            |To get Gradle help, go to https://help.gradle.org
            |
            |BUILD SUCCESSFUL in 541ms
            |1 actionable task: 1 executed
        """.trimMargin()

        val result = FinishedBuild(
            id = BuildId.newId(),
            args = args,
            consoleOutput = consoleOutput,
            publishedScans = emptyList(),
            testResults = TestResults(emptySet(), emptySet(), emptySet()),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )

        val taskOutput = result.getTaskOutput(":help")
        val expected = """
            |Welcome to Gradle 8.5.
            |
            |To run a build, run gradlew <task> ...
            |
            |To see a list of available tasks, run gradlew tasks
            |
            |To see more detail about a task, run gradlew help --task <task>
            |
            |To see a list of command-line options, run gradlew --help
            |
            |To get Gradle help, go to https://help.gradle.org
            |
        """.trimMargin().trim()

        assert(expected == taskOutput?.trim())
    }

    @Test
    fun `getTaskOutput extracts output when followed by another task`() {
        val consoleOutput = """
            |> Task :task1
            |Output 1
            |> Task :task2
            |Output 2
            |BUILD SUCCESSFUL
        """.trimMargin()

        val result = FinishedBuild(
            id = BuildId.newId(),
            args = args,
            consoleOutput = consoleOutput,
            publishedScans = emptyList(),
            testResults = TestResults(emptySet(), emptySet(), emptySet()),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )

        assert(result.getTaskOutput(":task1")?.trim() == "Output 1")
        assert(result.getTaskOutput(":task2")?.trim() == "Output 2")
    }

    @Test
    fun `getTaskOutput returns null if task not found`() {
        val result = FinishedBuild(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "BUILD SUCCESSFUL",
            publishedScans = emptyList(),
            testResults = TestResults(emptySet(), emptySet(), emptySet()),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )
        assert(result.getTaskOutput(":nonexistent") == null)
    }
}

class GradleResultTest {
    private val args = GradleInvocationArguments.DEFAULT

    @Test
    fun `can create successful gradle result`() {
        val finishedBuild = FinishedBuild(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "BUILD SUCCESSFUL",
            publishedScans = emptyList(),
            testResults = TestResults(emptySet(), emptySet(), emptySet()),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )

        val result = GradleResult<String>(
            build = finishedBuild,
            value = Result.success("test-value")
        )

        assert(result.value.isSuccess)
        assert(result.value.getOrNull() == "test-value")
        assert((result.build as FinishedBuild).outcome is BuildOutcome.Success)
    }

    @Test
    fun `can create failed gradle result`() {
        val finishedBuild = FinishedBuild(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "BUILD FAILED",
            publishedScans = emptyList(),
            testResults = TestResults(emptySet(), emptySet(), emptySet()),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Failed(emptyList()),
            finishTime = Clock.System.now()
        )

        val exception = RuntimeException("Build failed")
        val result = GradleResult<Unit>(
            build = finishedBuild,
            value = Result.failure(exception)
        )

        assert(result.value.isFailure)
        assert(result.value.exceptionOrNull() == exception)
    }

    @Test
    fun `throwFailure returns build id and value on success`() {
        val finishedBuild = FinishedBuild(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "",
            publishedScans = emptyList(),
            testResults = TestResults(emptySet(), emptySet(), emptySet()),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )

        val result = GradleResult<String>(
            build = finishedBuild,
            value = Result.success("test-value")
        )

        val (id, value) = result.throwFailure()
        assert(id == finishedBuild.id)
        assert(value == "test-value")
    }
}
