package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.build.BuildComponentOutcome
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.PhaseCount
import dev.rnett.gradle.mcp.gradle.build.TaskResult
import dev.rnett.gradle.mcp.gradle.build.TestResults
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class BuildResultIntelligenceOutputTest {

    private fun createBuild(
        taskResults: Map<String, TaskResult> = emptyMap(),
        phaseCounts: Map<String, PhaseCount> = emptyMap(),
        taskOriginAggregation: Map<String, Int> = emptyMap(),
        configCacheReportPointer: String? = null
    ): FinishedBuild {
        val id = BuildId(Uuid.random().toString())
        return FinishedBuild(
            id = id,
            args = GradleInvocationArguments.DEFAULT,
            startTime = Clock.System.now(),
            consoleOutput = "Synthetic build console",
            publishedScans = emptyList(),
            testResults = TestResults(emptySet(), emptySet(), emptySet()),
            problemAggregations = emptyMap(),
            taskResults = taskResults,
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now(),
            phaseCounts = phaseCounts,
            taskOriginAggregation = taskOriginAggregation,
            configCacheReportPointer = configCacheReportPointer
        )
    }

    @Test
    fun `toOutputString includes phase counts and cc pointer but not task origins`() {
        val build = createBuild(
            phaseCounts = mapOf(
                "configuration" to PhaseCount(10, 10),
                "dependency-resolution" to PhaseCount(3, 3),
                "task-execution" to PhaseCount(40, 35)
            ),
            taskOriginAggregation = mapOf(
                "org.jetbrains.kotlin.jvm" to 30,
                "_unknown" to 10
            ),
            configCacheReportPointer = "file:///tmp/configuration-cache-report.html"
        )

        val output = build.toOutputString()

        assertContains(output, "Work:")
        assertContains(output, "configuration: 10/10 completed")
        assertContains(output, "dependency-resolution: 3/3 completed")
        assertContains(output, "task-execution: 35/40 completed")
        assertFalse(output.contains("Task Origins:"))
        assertFalse(output.contains("org.jetbrains.kotlin.jvm: 30"))
        assertFalse(output.contains("_unknown: 10"))
        assertContains(output, "Configuration Cache Report: file:///tmp/configuration-cache-report.html")
    }

    @Test
    fun `toOutputString omits task origins and pointer when absent`() {
        val build = createBuild(
            phaseCounts = mapOf("configuration" to PhaseCount(0, 0))
        )

        val output = build.toOutputString()

        assertFalse(output.contains("Task Origins:"))
        assertFalse(output.contains("Configuration Cache Report:"))
    }

    @Test
    fun `getTasksOutput includes task origins in TASKS list output`() {
        val buildManager = mockk<BuildManager>(relaxed = true)
        val tools = GradleBuildLookupTools(buildManager)

        val build = createBuild(
            taskResults = mapOf(
                ":app:compile" to TaskResult(":app:compile", BuildComponentOutcome.SUCCESS, 1.0.seconds, null, "org.jetbrains.kotlin.jvm"),
                ":app:other" to TaskResult(":app:other", BuildComponentOutcome.SUCCESS, 1.0.seconds, null, null)
            ),
            taskOriginAggregation = mapOf(
                "org.jetbrains.kotlin.jvm" to 1,
                "_unknown" to 1
            )
        )

        val output = tools.getTasksOutput(build, GradleBuildLookupTools.QueryBuildArgs(buildId = build.id, kind = GradleBuildLookupTools.QueryKind.TASKS))

        assertContains(output, "Task Origins:")
        assertContains(output, "org.jetbrains.kotlin.jvm: 1")
        assertContains(output, "_unknown: 1")
    }

    @Test
    fun `getTasksOutput omits task origins when aggregation is empty`() {
        val buildManager = mockk<BuildManager>(relaxed = true)
        val tools = GradleBuildLookupTools(buildManager)

        val build = createBuild(
            taskResults = mapOf(
                ":app:compile" to TaskResult(":app:compile", BuildComponentOutcome.SUCCESS, 1.0.seconds, null, "org.jetbrains.kotlin.jvm")
            ),
            taskOriginAggregation = emptyMap()
        )

        val output = tools.getTasksOutput(build, GradleBuildLookupTools.QueryBuildArgs(buildId = build.id, kind = GradleBuildLookupTools.QueryKind.TASKS))

        assertFalse(output.contains("Task Origins:"))
    }

    @Test
    fun `toOutputString never contains Task Origins even when aggregation present, but TASKS query does`() {
        val build = createBuild(
            taskOriginAggregation = mapOf("org.gradle.api.tasks.compile" to 2)
        )
        val base = build.toOutputString()
        assertFalse(base.contains("Task Origins:"))

        val buildManager = mockk<BuildManager>(relaxed = true)
        val tools = GradleBuildLookupTools(buildManager)
        val tasksOutput = tools.getTasksOutput(build, GradleBuildLookupTools.QueryBuildArgs(buildId = build.id, kind = GradleBuildLookupTools.QueryKind.TASKS))
        assertContains(tasksOutput, "Task Origins:")
    }

    @Test
    fun `getTasksOutput prints Reason for skipped tasks and omits for success and up-to-date`() {
        val buildManager = mockk<BuildManager>(relaxed = true)
        val tools = GradleBuildLookupTools(buildManager)

        val taskResults = mapOf(
            ":app:upToDate" to TaskResult(":app:upToDate", BuildComponentOutcome.UP_TO_DATE, 1.0.seconds, null, "org.gradle.api.tasks.compile"),
            ":app:skipped" to TaskResult(":app:skipped", BuildComponentOutcome.SKIPPED, 0.seconds, null, "org.gradle.build.init", "OnlyIf / disabled"),
            ":app:success" to TaskResult(":app:success", BuildComponentOutcome.SUCCESS, 1.0.seconds, "Compile output", "org.jetbrains.kotlin.jvm")
        )
        val build = createBuild(taskResults = taskResults)

        val output = tools.getTasksOutput(build, GradleBuildLookupTools.QueryBuildArgs(buildId = build.id, kind = GradleBuildLookupTools.QueryKind.TASKS))

        assertContains(output, ":app:upToDate")
        assertContains(output, ":app:skipped")
        assertContains(output, ":app:success")
    }

    @Test
    fun `getTasksOutput prints Reason line when querying a single skipped task`() {
        val buildManager = mockk<BuildManager>(relaxed = true)
        val tools = GradleBuildLookupTools(buildManager)

        val taskResults = mapOf(
            ":app:skipped" to TaskResult(":app:skipped", BuildComponentOutcome.SKIPPED, 0.seconds, null, "org.gradle.build.init", "OnlyIf / disabled"),
            ":app:noSource" to TaskResult(":app:noSource", BuildComponentOutcome.NO_SOURCE, 0.seconds, null, "org.gradle.api.tasks", "NO-SOURCE")
        )
        val build = createBuild(taskResults = taskResults)

        val skippedOutput = tools.getTasksOutput(build, GradleBuildLookupTools.QueryBuildArgs(buildId = build.id, kind = GradleBuildLookupTools.QueryKind.TASKS, query = ":app:skipped"))
        assertContains(skippedOutput, "Reason: OnlyIf / disabled")

        val noSourceOutput = tools.getTasksOutput(build, GradleBuildLookupTools.QueryBuildArgs(buildId = build.id, kind = GradleBuildLookupTools.QueryKind.TASKS, query = ":app:noSource"))
        assertContains(noSourceOutput, "Reason: NO-SOURCE")
    }

    @Test
    fun `getTasksOutput omits Reason line for a success task`() {
        val buildManager = mockk<BuildManager>(relaxed = true)
        val tools = GradleBuildLookupTools(buildManager)

        val taskResults = mapOf(
            ":app:success" to TaskResult(":app:success", BuildComponentOutcome.SUCCESS, 1.0.seconds, "Compile output", "org.jetbrains.kotlin.jvm")
        )
        val build = createBuild(taskResults = taskResults)

        val output = tools.getTasksOutput(build, GradleBuildLookupTools.QueryBuildArgs(buildId = build.id, kind = GradleBuildLookupTools.QueryKind.TASKS, query = ":app:success"))
        assertFalse(output.contains("Reason:"))
    }
}