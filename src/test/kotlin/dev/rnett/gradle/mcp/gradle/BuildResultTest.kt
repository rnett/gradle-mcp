package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.tools.toSummary
import kotlin.test.Test
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

class BuildResultTest {
    private val args = GradleInvocationArguments.DEFAULT

    @Test
    fun `can create successful build result`() {
        val testResults = Build.TestResults(
            passed = emptySet(),
            skipped = emptySet(),
            failed = emptySet()
        )

        val result = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "BUILD SUCCESSFUL",
            publishedScans = emptyList(),
            testResults = testResults,
            buildFailures = null,
            problemAggregations = emptyMap()
        )

        assert(result.isSuccessful == true)
        assert(result.consoleOutput == "BUILD SUCCESSFUL")
        assert(result.id != null)
    }

    @Test
    fun `can create failed build result`() {
        val testResults = Build.TestResults(
            passed = emptySet(),
            skipped = emptySet(),
            failed = emptySet()
        )

        val failure = Build.Failure(
            id = FailureId("failure-1"),
            message = "Build failed",
            description = "Task execution failed",
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val result = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "BUILD FAILED",
            publishedScans = emptyList(),
            testResults = testResults,
            buildFailures = listOf(failure),
            problemAggregations = emptyMap()
        )

        assert(!(result.isSuccessful ?: true))
        assert(result.buildFailures?.size == 1)
    }

    @Test
    fun `test results tracks counts correctly`() {
        val passed = setOf(
            Build.TestResult("test1", null, 100.milliseconds, null, TestOutcome.PASSED),
            Build.TestResult("test2", null, 200.milliseconds, null, TestOutcome.PASSED)
        )
        val skipped = setOf(
            Build.TestResult("test3", null, 0.milliseconds, null, TestOutcome.SKIPPED)
        )
        val failed = setOf(
            Build.TestResult("test4", "output", 150.milliseconds, emptyList(), TestOutcome.FAILED)
        )

        val testResults = Build.TestResults(
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
        val testResults = Build.TestResults(
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
            Build.TestResult("test1", null, 100.milliseconds, null, TestOutcome.PASSED)
        )
        val skipped = setOf(
            Build.TestResult("test2", null, 0.milliseconds, null, TestOutcome.SKIPPED)
        )
        val failed = setOf(
            Build.TestResult("test3", "output", 150.milliseconds, emptyList(), TestOutcome.FAILED)
        )

        val testResults = Build.TestResults(
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
        val innerFailure = Build.Failure(
            id = FailureId("inner"),
            message = "Inner failure",
            description = null,
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val middleFailure = Build.Failure(
            id = FailureId("middle"),
            message = "Middle failure",
            description = null,
            causes = listOf(innerFailure),
            problemAggregations = emptyMap()
        )

        val outerFailure = Build.Failure(
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
        val result = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "Line 1\nLine 2\nLine 3",
            publishedScans = emptyList(),
            testResults = Build.TestResults(emptySet(), emptySet(), emptySet()),
            buildFailures = null,
            problemAggregations = emptyMap()
        )

        val lines = result.consoleOutputLines
        assert(lines.size == 3)
        assert(lines[0] == "Line 1")
        assert(lines[1] == "Line 2")
        assert(lines[2] == "Line 3")
    }

    @Test
    fun `allTestFailures extracts failures from test results`() {
        val testFailure = Build.Failure(
            id = FailureId("test-failure"),
            message = "Test failed",
            description = null,
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val testResult = Build.TestResult(
            testName = "failingTest",
            consoleOutput = null,
            executionDuration = 100.milliseconds,
            failures = listOf(testFailure),
            status = TestOutcome.FAILED
        )

        val result = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "",
            publishedScans = emptyList(),
            testResults = Build.TestResults(
                passed = emptySet(),
                skipped = emptySet(),
                failed = setOf(testResult)
            ),
            buildFailures = null,
            problemAggregations = emptyMap()
        )

        assert(result.allTestFailures.size == 1)
        assert(result.allTestFailures.containsKey(FailureId("test-failure")))
    }

    @Test
    fun `allBuildFailures excludes test failures`() {
        val testFailure = Build.Failure(
            id = FailureId("test-failure"),
            message = "Test failed",
            description = null,
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val buildFailure = Build.Failure(
            id = FailureId("build-failure"),
            message = "Build failed",
            description = null,
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val testResult = Build.TestResult(
            testName = "failingTest",
            consoleOutput = null,
            executionDuration = 100.milliseconds,
            failures = listOf(testFailure),
            status = TestOutcome.FAILED
        )

        val result = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "",
            publishedScans = emptyList(),
            testResults = Build.TestResults(
                passed = emptySet(),
                skipped = emptySet(),
                failed = setOf(testResult)
            ),
            buildFailures = listOf(buildFailure),
            problemAggregations = emptyMap()
        )

        assert(result.allBuildFailures.size == 1)
        assert(result.allBuildFailures.containsKey(FailureId("build-failure")))
        assert(!result.allBuildFailures.containsKey(FailureId("test-failure")))
    }

    @Test
    fun `allFailures combines test and build failures`() {
        val testFailure = Build.Failure(
            id = FailureId("test-failure"),
            message = "Test failed",
            description = null,
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val buildFailure = Build.Failure(
            id = FailureId("build-failure"),
            message = "Build failed",
            description = null,
            causes = emptyList(),
            problemAggregations = emptyMap()
        )

        val testResult = Build.TestResult(
            testName = "failingTest",
            consoleOutput = null,
            executionDuration = 100.milliseconds,
            failures = listOf(testFailure),
            status = TestOutcome.FAILED
        )

        val result = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "",
            publishedScans = emptyList(),
            testResults = Build.TestResults(
                passed = emptySet(),
                skipped = emptySet(),
                failed = setOf(testResult)
            ),
            buildFailures = listOf(buildFailure),
            problemAggregations = emptyMap()
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

        val result = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = consoleOutput,
            publishedScans = emptyList(),
            testResults = Build.TestResults(emptySet(), emptySet(), emptySet()),
            buildFailures = null,
            problemAggregations = emptyMap()
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

        val result = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = consoleOutput,
            publishedScans = emptyList(),
            testResults = Build.TestResults(emptySet(), emptySet(), emptySet()),
            buildFailures = null,
            problemAggregations = emptyMap()
        )

        assert(result.getTaskOutput(":task1")?.trim() == "Output 1")
        assert(result.getTaskOutput(":task2")?.trim() == "Output 2")
    }

    @Test
    fun `getTaskOutput returns null if task not found`() {
        val result = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "BUILD SUCCESSFUL",
            publishedScans = emptyList(),
            testResults = Build.TestResults(emptySet(), emptySet(), emptySet()),
            buildFailures = null,
            problemAggregations = emptyMap()
        )
        assert(result.getTaskOutput(":nonexistent") == null)
    }
}

class GradleResultTest {
    private val args = GradleInvocationArguments.DEFAULT

    @Test
    fun `can create successful gradle result`() {
        val buildResult = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "BUILD SUCCESSFUL",
            publishedScans = emptyList(),
            testResults = Build.TestResults(emptySet(), emptySet(), emptySet()),
            buildFailures = null,
            problemAggregations = emptyMap()
        )

        val result = GradleResult<String>(
            buildResult = buildResult,
            value = Result.success("test-value")
        )

        assert(result.value.isSuccess)
        assert(result.value.getOrNull() == "test-value")
        assert(result.buildResult.isSuccessful == true)
    }

    @Test
    fun `can create failed gradle result`() {
        val buildResult = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "BUILD FAILED",
            publishedScans = emptyList(),
            testResults = Build.TestResults(emptySet(), emptySet(), emptySet()),
            buildFailures = emptyList(),
            problemAggregations = emptyMap()
        )

        val exception = RuntimeException("Build failed")
        val result = GradleResult<Unit>(
            buildResult = buildResult,
            value = Result.failure(exception)
        )

        assert(result.value.isFailure)
        assert(result.value.exceptionOrNull() == exception)
    }

    @Test
    fun `throwFailure returns build id and value on success`() {
        val buildResult = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "",
            publishedScans = emptyList(),
            testResults = Build.TestResults(emptySet(), emptySet(), emptySet()),
            buildFailures = null,
            problemAggregations = emptyMap()
        )

        val result = GradleResult<String>(
            buildResult = buildResult,
            value = Result.success("test-value")
        )

        val (id, value) = result.throwFailure()
        assert(id == buildResult.id)
        assert(value == "test-value")
    }
}
