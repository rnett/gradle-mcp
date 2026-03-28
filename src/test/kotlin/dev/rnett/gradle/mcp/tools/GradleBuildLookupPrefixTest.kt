package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.gradle.build.TaskOutcome
import dev.rnett.gradle.mcp.gradle.build.TaskResult
import dev.rnett.gradle.mcp.gradle.build.TestOutcome
import dev.rnett.gradle.mcp.gradle.build.TestResult
import dev.rnett.gradle.mcp.gradle.build.TestResults
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class GradleBuildLookupPrefixTest {

    private val buildManager = mockk<BuildManager>()
    private val tools = GradleBuildLookupTools(buildManager)

    private fun createSyntheticBuild(): FinishedBuild {
        val id = BuildId(Uuid.random().toString())
        val testResults = TestResults(
            passed = setOf(
                TestResult("testOne", "com.example.TestA", "Output A1", 0.1.seconds, null, TestOutcome.PASSED, emptyMap(), emptyList()),
                TestResult("testTwo", "com.example.TestA", "Output A2", 0.2.seconds, null, TestOutcome.PASSED, emptyMap(), emptyList()),
                TestResult("testUnique", "com.example.TestB", "Output B Unique", 0.3.seconds, null, TestOutcome.PASSED, emptyMap(), emptyList()),
                TestResult("DuplicateName", "com.example", "Output Dup 1", 0.4.seconds, null, TestOutcome.PASSED, emptyMap(), emptyList()),
                TestResult("DuplicateName", "com.example", "Output Dup 2", 0.5.seconds, null, TestOutcome.PASSED, emptyMap(), emptyList())
            ),
            failed = emptySet(),
            skipped = emptySet()
        )
        val taskResults = mapOf(
            ":app:compileJava" to TaskResult(":app:compileJava", TaskOutcome.SUCCESS, 1.0.seconds, "Compile output"),
            ":app:processResources" to TaskResult(":app:processResources", TaskOutcome.SUCCESS, 0.5.seconds, "Resources output"),
            ":lib:compileJava" to TaskResult(":lib:compileJava", TaskOutcome.SUCCESS, 0.8.seconds, "Lib compile output")
        )
        return FinishedBuild(
            id = id,
            startTime = Clock.System.now(),
            args = GradleInvocationArguments.DEFAULT,
            consoleOutput = "Synthetic build console",
            publishedScans = emptyList(),
            testResults = testResults,
            problemAggregations = emptyMap(),
            taskResults = taskResults,
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )
    }

    @Test
    fun `test exact test match still works`() = runTest {
        val build = createSyntheticBuild()

        val args = GradleBuildLookupTools.InspectBuildArgs(
            buildId = build.id,
            mode = GradleBuildLookupTools.LookupMode.details,
            testName = "com.example.TestB.testUnique"
        )

        val output = tools.getTestsOutput(build, args)
        assertContains(output, "com.example.TestB.testUnique - PASSED")
        assertContains(output, "Output B Unique")
        // Should NOT have the prefix match note
        assertEquals(false, output.contains("Note: Showing details for unique prefix match"))
    }

    @Test
    fun `test multiple executions with exact name`() = runTest {
        val build = createSyntheticBuild()

        val args = GradleBuildLookupTools.InspectBuildArgs(
            buildId = build.id,
            mode = GradleBuildLookupTools.LookupMode.details,
            testName = "com.example.DuplicateName",
            testIndex = 1
        )

        val output = tools.getTestsOutput(build, args)
        assertContains(output, "com.example.DuplicateName - PASSED")
        assertContains(output, "Output Dup 2")
        // Should NOT have the prefix match note for exact name, even if multiple executions
        assertEquals(false, output.contains("Note: Showing details for unique prefix match"))
    }

    @Test
    fun `test unique test prefix match`() = runTest {
        val build = createSyntheticBuild()

        val args = GradleBuildLookupTools.InspectBuildArgs(
            buildId = build.id,
            mode = GradleBuildLookupTools.LookupMode.details,
            testName = "com.example.TestB"
        )

        val output = tools.getTestsOutput(build, args)
        assertContains(output, "Note: Showing details for unique prefix match: com.example.TestB.testUnique")
        assertContains(output, "com.example.TestB.testUnique - PASSED")
        assertContains(output, "Output B Unique")
    }

    @Test
    fun `test ambiguous test prefix match`() = runTest {
        val build = createSyntheticBuild()

        val args = GradleBuildLookupTools.InspectBuildArgs(
            buildId = build.id,
            mode = GradleBuildLookupTools.LookupMode.details,
            testName = "com.example.TestA"
        )

        val output = tools.getTestsOutput(build, args)
        assertContains(output, "Multiple tests match prefix 'com.example.TestA':")
        assertContains(output, "  - com.example.TestA.testOne")
        assertContains(output, "  - com.example.TestA.testTwo")
    }

    @Test
    fun `test multiple executions with same name and unique prefix`() = runTest {
        val build = createSyntheticBuild()

        val args = GradleBuildLookupTools.InspectBuildArgs(
            buildId = build.id,
            mode = GradleBuildLookupTools.LookupMode.details,
            testName = "com.example.Dup",
            testIndex = 1
        )

        val output = tools.getTestsOutput(build, args)
        assertContains(output, "Note: Showing details for unique prefix match: com.example.DuplicateName")
        assertContains(output, "Output Dup 2")
    }

    @Test
    fun `test testIndex out of bounds`() = runTest {
        val build = createSyntheticBuild()

        val args = GradleBuildLookupTools.InspectBuildArgs(
            buildId = build.id,
            mode = GradleBuildLookupTools.LookupMode.details,
            testName = "com.example.DuplicateName",
            testIndex = 5
        )

        val output = tools.getTestsOutput(build, args)
        assertContains(output, "2 test executions with this name found. Pass a valid `testIndex` (0 to 1) to select one.")
    }

    @Test
    fun `test test not found with suite-based fallback`() = runTest {
        val build = createSyntheticBuild()

        val args = GradleBuildLookupTools.InspectBuildArgs(
            buildId = build.id,
            mode = GradleBuildLookupTools.LookupMode.details,
            testName = "com.example.TestA.nonExistent"
        )

        val output = tools.getTestsOutput(build, args)
        assertContains(output, "Test not found: com.example.TestA.nonExistent")
        assertContains(output, "Other tests in suite 'com.example.TestA':")
        assertContains(output, "  - com.example.TestA.testOne")
        assertContains(output, "  - com.example.TestA.testTwo")
    }

    @Test
    fun `test test not found with substring matches`() = runTest {
        val build = createSyntheticBuild()

        val args = GradleBuildLookupTools.InspectBuildArgs(
            buildId = build.id,
            mode = GradleBuildLookupTools.LookupMode.details,
            testName = "testOne"
        )

        val output = tools.getTestsOutput(build, args)
        assertContains(output, "Test not found: testOne")
        assertContains(output, "Tests containing 'testOne':")
        assertContains(output, "  - com.example.TestA.testOne")
    }

    @Test
    fun `test test not found for finished build`() = runTest {
        val build = createSyntheticBuild()

        val args = GradleBuildLookupTools.InspectBuildArgs(
            buildId = build.id,
            mode = GradleBuildLookupTools.LookupMode.details,
            testName = "DefinitelyNotThere"
        )

        assertFailsWith<IllegalStateException> {
            tools.getTestsOutput(build, args)
        }.also {
            assertContains(it.message ?: "", "Test not found: DefinitelyNotThere")
        }
    }

    @Test
    fun `test test not found for running build`() = runTest {
        val buildId = BuildId(Uuid.random().toString())
        val build = mockk<RunningBuild> {
            every { id } returns buildId
            every { testResults.all } returns emptySequence()
            every { isRunning } returns true
        }

        val args = GradleBuildLookupTools.InspectBuildArgs(
            buildId = buildId,
            mode = GradleBuildLookupTools.LookupMode.details,
            testName = "com.example.Any"
        )

        val output = tools.getTestsOutput(build, args)
        assertContains(output, "Test not found. The build is still running, so it may not have been executed yet.")
    }

    @Test
    fun `test unique task prefix match`() = runTest {
        val build = createSyntheticBuild()

        val args = GradleBuildLookupTools.InspectBuildArgs(
            buildId = build.id,
            mode = GradleBuildLookupTools.LookupMode.details,
            taskPath = ":app:process"
        )

        val output = tools.getTasksOutput(build, args)
        assertContains(output, "Note: Showing details for unique prefix match: :app:processResources")
        assertContains(output, "Task: :app:processResources")
        assertContains(output, "Resources output")
    }

    @Test
    fun `test ambiguous task prefix match`() = runTest {
        val build = createSyntheticBuild()

        val args = GradleBuildLookupTools.InspectBuildArgs(
            buildId = build.id,
            mode = GradleBuildLookupTools.LookupMode.details,
            taskPath = ":app"
        )

        val output = tools.getTasksOutput(build, args)
        assertContains(output, "Multiple tasks match prefix ':app':")
        assertContains(output, "  - :app:compileJava")
        assertContains(output, "  - :app:processResources")
    }
}
