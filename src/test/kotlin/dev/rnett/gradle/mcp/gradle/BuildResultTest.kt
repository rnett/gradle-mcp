package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.tools.toSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class GradleBuildScanTest {

    @Test
    fun `can create build scan from components`() {
        val scan = GradleBuildScan(
            url = "https://scans.gradle.com/s/abc123",
            id = "abc123",
            develocityInstance = "https://scans.gradle.com"
        )
        assertEquals("https://scans.gradle.com/s/abc123", scan.url)
        assertEquals("abc123", scan.id)
        assertEquals("https://scans.gradle.com", scan.develocityInstance)
    }

    @Test
    fun `can parse build scan from scans gradle com URL`() {
        val scan = GradleBuildScan.fromUrl("https://scans.gradle.com/s/abc123")
        assertEquals("https://scans.gradle.com/s/abc123", scan.url)
        assertEquals("abc123", scan.id)
        assertEquals("https://scans.gradle.com", scan.develocityInstance)
    }

    @Test
    fun `can parse build scan from gradle com shortlink`() {
        val scan = GradleBuildScan.fromUrl("https://gradle.com/s/abc123")
        assertEquals("https://scans.gradle.com/s/abc123", scan.url)
        assertEquals("abc123", scan.id)
        assertEquals("https://scans.gradle.com", scan.develocityInstance)
    }

    @Test
    fun `can parse build scan from custom develocity instance`() {
        val scan = GradleBuildScan.fromUrl("https://ge.company.com/s/xyz789")
        assertEquals("https://ge.company.com/s/xyz789", scan.url)
        assertEquals("xyz789", scan.id)
        assertEquals("https://ge.company.com", scan.develocityInstance)
    }
}

class BuildResultTest {
    private val args = GradleInvocationArguments.DEFAULT

    @Test
    fun `can create successful build result`() {
        val testResults = BuildResult.TestResults(
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
            problems = emptyMap()
        )

        assertTrue(result.isSuccessful)
        assertEquals("BUILD SUCCESSFUL", result.consoleOutput)
        assertNotNull(result.id)
    }

    @Test
    fun `can create failed build result`() {
        val testResults = BuildResult.TestResults(
            passed = emptySet(),
            skipped = emptySet(),
            failed = emptySet()
        )

        val failure = BuildResult.Failure(
            id = FailureId("failure-1"),
            message = "Build failed",
            description = "Task execution failed",
            causes = emptyList(),
            problems = emptyList()
        )

        val result = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "BUILD FAILED",
            publishedScans = emptyList(),
            testResults = testResults,
            buildFailures = listOf(failure),
            problems = emptyMap()
        )

        assertFalse(result.isSuccessful)
        assertEquals(1, result.buildFailures?.size)
    }

    @Test
    fun `test results tracks counts correctly`() {
        val passed = setOf(
            BuildResult.TestResult("test1", null, 100.milliseconds, null, TestOutcome.PASSED),
            BuildResult.TestResult("test2", null, 200.milliseconds, null, TestOutcome.PASSED)
        )
        val skipped = setOf(
            BuildResult.TestResult("test3", null, 0.milliseconds, null, TestOutcome.SKIPPED)
        )
        val failed = setOf(
            BuildResult.TestResult("test4", "output", 150.milliseconds, emptyList(), TestOutcome.FAILED)
        )

        val testResults = BuildResult.TestResults(
            passed = passed,
            skipped = skipped,
            failed = failed
        )

        assertEquals(2, testResults.passed.size)
        assertEquals(1, testResults.skipped.size)
        assertEquals(1, testResults.failed.size)
        assertEquals(4, testResults.totalCount)
        assertFalse(testResults.isEmpty)
    }

    @Test
    fun `test results isEmpty is true when no tests`() {
        val testResults = BuildResult.TestResults(
            passed = emptySet(),
            skipped = emptySet(),
            failed = emptySet()
        )

        assertTrue(testResults.isEmpty)
        assertEquals(0, testResults.totalCount)
    }

    @Test
    fun `test results all sequence contains all tests`() {
        val passed = setOf(
            BuildResult.TestResult("test1", null, 100.milliseconds, null, TestOutcome.PASSED)
        )
        val skipped = setOf(
            BuildResult.TestResult("test2", null, 0.milliseconds, null, TestOutcome.SKIPPED)
        )
        val failed = setOf(
            BuildResult.TestResult("test3", "output", 150.milliseconds, emptyList(), TestOutcome.FAILED)
        )

        val testResults = BuildResult.TestResults(
            passed = passed,
            skipped = skipped,
            failed = failed
        )

        val allTests = testResults.all.toList()
        assertEquals(3, allTests.size)
        assertTrue(allTests.any { it.testName == "test1" })
        assertTrue(allTests.any { it.testName == "test2" })
        assertTrue(allTests.any { it.testName == "test3" })
    }

    @Test
    fun `failure can flatten nested causes`() {
        val innerFailure = BuildResult.Failure(
            id = FailureId("inner"),
            message = "Inner failure",
            description = null,
            causes = emptyList(),
            problems = emptyList()
        )

        val middleFailure = BuildResult.Failure(
            id = FailureId("middle"),
            message = "Middle failure",
            description = null,
            causes = listOf(innerFailure),
            problems = emptyList()
        )

        val outerFailure = BuildResult.Failure(
            id = FailureId("outer"),
            message = "Outer failure",
            description = null,
            causes = listOf(middleFailure),
            problems = emptyList()
        )

        val flattened = outerFailure.flatten().toList()
        assertEquals(3, flattened.size)
        assertTrue(flattened.any { it.id.id == "outer" })
        assertTrue(flattened.any { it.id.id == "middle" })
        assertTrue(flattened.any { it.id.id == "inner" })
    }

    @Test
    fun `consoleOutputLines lazily splits console output`() {
        val result = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "Line 1\nLine 2\nLine 3",
            publishedScans = emptyList(),
            testResults = BuildResult.TestResults(emptySet(), emptySet(), emptySet()),
            buildFailures = null,
            problems = emptyMap()
        )

        val lines = result.consoleOutputLines
        assertEquals(3, lines.size)
        assertEquals("Line 1", lines[0])
        assertEquals("Line 2", lines[1])
        assertEquals("Line 3", lines[2])
    }

    @Test
    fun `allTestFailures extracts failures from test results`() {
        val testFailure = BuildResult.Failure(
            id = FailureId("test-failure"),
            message = "Test failed",
            description = null,
            causes = emptyList(),
            problems = emptyList()
        )

        val testResult = BuildResult.TestResult(
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
            testResults = BuildResult.TestResults(
                passed = emptySet(),
                skipped = emptySet(),
                failed = setOf(testResult)
            ),
            buildFailures = null,
            problems = emptyMap()
        )

        assertEquals(1, result.allTestFailures.size)
        assertTrue(result.allTestFailures.containsKey(FailureId("test-failure")))
    }

    @Test
    fun `allBuildFailures excludes test failures`() {
        val testFailure = BuildResult.Failure(
            id = FailureId("test-failure"),
            message = "Test failed",
            description = null,
            causes = emptyList(),
            problems = emptyList()
        )

        val buildFailure = BuildResult.Failure(
            id = FailureId("build-failure"),
            message = "Build failed",
            description = null,
            causes = emptyList(),
            problems = emptyList()
        )

        val testResult = BuildResult.TestResult(
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
            testResults = BuildResult.TestResults(
                passed = emptySet(),
                skipped = emptySet(),
                failed = setOf(testResult)
            ),
            buildFailures = listOf(buildFailure),
            problems = emptyMap()
        )

        assertEquals(1, result.allBuildFailures.size)
        assertTrue(result.allBuildFailures.containsKey(FailureId("build-failure")))
        assertFalse(result.allBuildFailures.containsKey(FailureId("test-failure")))
    }

    @Test
    fun `allFailures combines test and build failures`() {
        val testFailure = BuildResult.Failure(
            id = FailureId("test-failure"),
            message = "Test failed",
            description = null,
            causes = emptyList(),
            problems = emptyList()
        )

        val buildFailure = BuildResult.Failure(
            id = FailureId("build-failure"),
            message = "Build failed",
            description = null,
            causes = emptyList(),
            problems = emptyList()
        )

        val testResult = BuildResult.TestResult(
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
            testResults = BuildResult.TestResults(
                passed = emptySet(),
                skipped = emptySet(),
                failed = setOf(testResult)
            ),
            buildFailures = listOf(buildFailure),
            problems = emptyMap()
        )

        assertEquals(2, result.allFailures.size)
        assertTrue(result.allFailures.containsKey(FailureId("test-failure")))
        assertTrue(result.allFailures.containsKey(FailureId("build-failure")))
    }

    @Test
    fun `problems toSummary correctly aggregates and calculates totalCount`() {
        val problems = mapOf(
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

        val summary = problems.toSummary()
        assertEquals(2, summary.errorCounts[ProblemId("e1")]?.occurences)
        assertEquals(1, summary.warningCounts[ProblemId("w1")]?.occurences)
        assertEquals(3, summary.totalCount)
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
            testResults = BuildResult.TestResults(emptySet(), emptySet(), emptySet()),
            buildFailures = null,
            problems = emptyMap()
        )

        val result = GradleResult<String>(
            buildResult = buildResult,
            value = Result.success("test-value")
        )

        assertTrue(result.value.isSuccess)
        assertEquals("test-value", result.value.getOrNull())
        assertTrue(result.buildResult.isSuccessful)
    }

    @Test
    fun `can create failed gradle result`() {
        val buildResult = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "BUILD FAILED",
            publishedScans = emptyList(),
            testResults = BuildResult.TestResults(emptySet(), emptySet(), emptySet()),
            buildFailures = emptyList(),
            problems = emptyMap()
        )

        val exception = RuntimeException("Build failed")
        val result = GradleResult<Unit>(
            buildResult = buildResult,
            value = Result.failure(exception)
        )

        assertTrue(result.value.isFailure)
        assertEquals(exception, result.value.exceptionOrNull())
    }

    @Test
    fun `throwFailure returns build id and value on success`() {
        val buildResult = BuildResult(
            id = BuildId.newId(),
            args = args,
            consoleOutput = "",
            publishedScans = emptyList(),
            testResults = BuildResult.TestResults(emptySet(), emptySet(), emptySet()),
            buildFailures = null,
            problems = emptyMap()
        )

        val result = GradleResult<String>(
            buildResult = buildResult,
            value = Result.success("test-value")
        )

        val (id, value) = result.throwFailure()
        assertEquals(buildResult.id, id)
        assertEquals("test-value", value)
    }
}
