package dev.rnett.gradle.mcp.gradle.build

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.utils.EnvProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskOperationResult
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.time.Clock

class BuildResultIntelligenceTest {

    private fun TestScope.createRunningBuild(): RunningBuild {
        return RunningBuild(
            id = BuildId("test-id"),
            args = GradleInvocationArguments.DEFAULT,
            startTime = Clock.System.now(),
            projectRoot = Path("."),
            cancellationTokenSource = GradleConnector.newCancellationTokenSource(),
            scope = this.backgroundScope
        )
    }

    // --- classifyPhaseName ---

    @Test
    fun `classifyPhaseName trims and matches case-insensitively`() {
        val tracker = BuildProgressTracker(mockk<BuildProgressInfoProvider>(relaxed = true))

        assertEquals("configuration", tracker.classifyPhaseName("  configuration  "))
        assertEquals("configuration", tracker.classifyPhaseName("CONFIGURATION"))
        assertEquals("configuration", tracker.classifyPhaseName("configure foo"))
        assertEquals("configuration", tracker.classifyPhaseName("Project configuration"))

        assertEquals("dependency-resolution", tracker.classifyPhaseName("resolve dependencies"))
        assertEquals("dependency-resolution", tracker.classifyPhaseName("Dependency resolution"))
        assertEquals("dependency-resolution", tracker.classifyPhaseName("Resolving dependency resolution"))

        assertEquals("task-execution", tracker.classifyPhaseName("task execution"))
        assertEquals("task-execution", tracker.classifyPhaseName("EXECUTE TASKS"))
        assertEquals("task-execution", tracker.classifyPhaseName("Running tasks"))
    }

    @Test
    fun `classifyPhaseName applies configuration precedence over overlap`() {
        val tracker = BuildProgressTracker(mockk<BuildProgressInfoProvider>(relaxed = true))
        // Overlap should resolve to the first matching bucket: configuration wins.
        assertEquals("configuration", tracker.classifyPhaseName("configuration and task execution"))
    }

    @Test
    fun `classifyPhaseName returns null for unmatched names`() {
        val tracker = BuildProgressTracker(mockk<BuildProgressInfoProvider>(relaxed = true))
        assertNull(tracker.classifyPhaseName("GENERIC_STUFF"))
        assertNull(tracker.classifyPhaseName(""))
        assertNull(tracker.classifyPhaseName("   "))
    }

    // --- computePhaseCounts ---

    @Test
    fun `computePhaseCounts emits all three buckets defaulting to zero`() {
        val tracker = BuildProgressTracker(mockk<BuildProgressInfoProvider>(relaxed = true))
        val counts = tracker.computePhaseCounts()
        // Assert exactly the three buckets, all 0/0.
        assertEquals(setOf("configuration", "dependency-resolution", "task-execution"), counts.keys)
        assertEquals(PhaseCount(0, 0), counts["configuration"])
        assertEquals(PhaseCount(0, 0), counts["dependency-resolution"])
        assertEquals(PhaseCount(0, 0), counts["task-execution"])
    }

    @Test
    fun `computePhaseCounts aggregates repeated retained states by summing`() {
        val tracker = BuildProgressTracker(mockk<BuildProgressInfoProvider>(relaxed = true))

        // Two distinct configuration phases each with 5 total / 3 completed.
        tracker.onPhaseStart("CONFIGURATION", 5)
        repeat(3) { tracker.onItemFinish() }
        tracker.onPhaseFinish("CONFIGURATION")

        tracker.onPhaseStart("CONFIGURATION", 5)
        repeat(3) { tracker.onItemFinish() }
        tracker.onPhaseFinish("CONFIGURATION")

        val counts = tracker.computePhaseCounts()
        assertEquals(PhaseCount(10, 6), counts["configuration"])
        assertEquals(PhaseCount(0, 0), counts["dependency-resolution"])
        assertEquals(PhaseCount(0, 0), counts["task-execution"])
    }

    @Test
    fun `computePhaseCounts classifies by bucket and ignores unmatched`() {
        val tracker = BuildProgressTracker(mockk<BuildProgressInfoProvider>(relaxed = true))

        tracker.onPhaseStart("RUN_MAIN_TASKS", 4)
        repeat(2) { tracker.onItemFinish() }
        tracker.onPhaseFinish("RUN_MAIN_TASKS")

        // Unmatched phase name must be ignored.
        tracker.onPhaseStart("SOME_GARBAGE_PHASE", 9)
        tracker.onPhaseFinish("SOME_GARBAGE_PHASE")

        val counts = tracker.computePhaseCounts()
        assertEquals(PhaseCount(0, 0), counts["configuration"])
        assertEquals(PhaseCount(0, 0), counts["dependency-resolution"])
        assertEquals(PhaseCount(4, 2), counts["task-execution"])
    }

    @Test
    fun `dependency resolution bucket is zero when no such phase observed`() {
        val tracker = BuildProgressTracker(mockk<BuildProgressInfoProvider>(relaxed = true))

        tracker.onPhaseStart("CONFIGURATION", 3)
        repeat(3) { tracker.onItemFinish() }
        tracker.onPhaseFinish("CONFIGURATION")

        tracker.onPhaseStart("RUN_MAIN_TASKS", 2)
        repeat(2) { tracker.onItemFinish() }
        tracker.onPhaseFinish("RUN_MAIN_TASKS")

        val counts = tracker.computePhaseCounts()
        assertEquals(PhaseCount(0, 0), counts["dependency-resolution"])
        assertEquals(PhaseCount(3, 3), counts["configuration"])
        assertEquals(PhaseCount(2, 2), counts["task-execution"])
    }

    @Test
    fun `computePhaseCounts captures a frozen snapshot from completed history`() = runTest {
        val build = createRunningBuild()
        build.progressTracker.onPhaseStart("CONFIGURATION", 2)
        repeat(2) { build.progressTracker.onItemFinish() }
        build.progressTracker.onPhaseFinish("CONFIGURATION")

        val counts = build.progressTracker.computePhaseCounts()
        assertEquals(PhaseCount(2, 2), counts["configuration"])

        // A subsequent phase finish must not retroactively mutate the already frozen counts.
        build.progressTracker.onPhaseStart("RUN_MAIN_TASKS", 1)
        build.progressTracker.onItemFinish()
        build.progressTracker.onPhaseFinish("RUN_MAIN_TASKS")

        // The earlier snapshot holds the configuration phase value; recompute reflects the addition.
        assertEquals(PhaseCount(2, 2), counts["configuration"])
        val updated = build.progressTracker.computePhaseCounts()
        assertEquals(PhaseCount(2, 2), updated["configuration"])
        assertEquals(PhaseCount(1, 1), updated["task-execution"])
    }

    // --- taskOriginAggregation ---

    @Test
    fun `taskOriginAggregation groups by provenance and lumps absent under _unknown`() = runTest {
        val build = createRunningBuild()
        build.addTaskResult(":a", BuildComponentOutcome.SUCCESS, kotlin.time.Duration.ZERO, null, "org.jetbrains.kotlin.jvm")
        build.addTaskResult(":b", BuildComponentOutcome.SUCCESS, kotlin.time.Duration.ZERO, null, "org.jetbrains.kotlin.jvm")
        build.addTaskResult(":c", BuildComponentOutcome.SUCCESS, kotlin.time.Duration.ZERO, null, "build file 'build.gradle.kts'")
        build.addTaskResult(":d", BuildComponentOutcome.SUCCESS, kotlin.time.Duration.ZERO, null, null)

        val aggregation = build.taskOriginAggregation
        assertEquals(2, aggregation["org.jetbrains.kotlin.jvm"])
        assertEquals(1, aggregation["build file 'build.gradle.kts'"])
        assertEquals(1, aggregation["_unknown"])
        // Sum of all values equals the total completed task count.
        assertEquals(4, aggregation.values.sum())
    }

    @Test
    fun `taskOriginAggregation omits _unknown when every task has provenance`() = runTest {
        val build = createRunningBuild()
        build.addTaskResult(":a", BuildComponentOutcome.SUCCESS, kotlin.time.Duration.ZERO, null, "plugin-a")
        build.addTaskResult(":b", BuildComponentOutcome.SUCCESS, kotlin.time.Duration.ZERO, null, "plugin-b")

        val aggregation = build.taskOriginAggregation
        assertEquals(false, "_unknown" in aggregation)
        assertEquals(2, aggregation.values.sum())
    }

    @Test
    fun `taskOriginAggregation sum equals total completed task count`() = runTest {
        val build = createRunningBuild()
        build.addTaskResult(":a", BuildComponentOutcome.SUCCESS, kotlin.time.Duration.ZERO, null, "org.gradle.api.tasks.compile")
        build.addTaskResult(":b", BuildComponentOutcome.SUCCESS, kotlin.time.Duration.ZERO, null, "org.gradle.api.tasks.compile")
        build.addTaskResult(":c", BuildComponentOutcome.SUCCESS, kotlin.time.Duration.ZERO, null, null)
        build.addTaskResult(":d", BuildComponentOutcome.SUCCESS, kotlin.time.Duration.ZERO, null, null)
        build.addTaskResult(":e", BuildComponentOutcome.SUCCESS, kotlin.time.Duration.ZERO, null, "org.jetbrains.kotlin.jvm")

        assertEquals(build.taskResults.size, build.taskOriginAggregation.values.sum())
    }

    // --- TaskResult reason mapping in BuildExecutionService.handleTaskFinish ---

    @Test
    fun `handleTaskFinish maps task results to expected reasons`() = runTest {
        val service = DefaultBuildExecutionService(
            envProvider = mockk<EnvProvider>(relaxed = true),
            initScriptProvider = mockk(relaxed = true),
            daemonJvmSelector = mockk(relaxed = true)
        )
        val handleTaskFinish = DefaultBuildExecutionService::class.java.getDeclaredMethod(
            "handleTaskFinish", TaskFinishEvent::class.java, RunningBuild::class.java
        ).apply { isAccessible = true }

        fun makeResult(outcome: BuildComponentOutcome): TaskOperationResult = when (outcome) {
            BuildComponentOutcome.FROM_CACHE -> {
                val r = mockk<TaskSuccessResult>(relaxed = true)
                every { r.isFromCache } returns true
                every { r.isUpToDate } returns false
                r
            }
            BuildComponentOutcome.UP_TO_DATE -> {
                val r = mockk<TaskSuccessResult>(relaxed = true)
                every { r.isFromCache } returns false
                every { r.isUpToDate } returns true
                r
            }
            BuildComponentOutcome.NO_SOURCE -> {
                val r = mockk<TaskSkippedResult>(relaxed = true)
                every { r.skipMessage } returns "NO-SOURCE"
                r
            }
            BuildComponentOutcome.SKIPPED -> {
                val r = mockk<TaskSkippedResult>(relaxed = true)
                every { r.skipMessage } returns "OnlyIf / disabled"
                r
            }
            BuildComponentOutcome.FAILED -> mockk<TaskFailureResult>(relaxed = true)
            BuildComponentOutcome.CANCELLED -> mockk<TaskOperationResult>(relaxed = true)
            else -> mockk<TaskSuccessResult>(relaxed = true)
        }

        fun run(outcome: BuildComponentOutcome, taskPath: String = ":task"): RunningBuild {
            val build = createRunningBuild()
            val taskFinishEvent = mockk<TaskFinishEvent>(relaxed = true)
            val descriptor = mockk<TaskOperationDescriptor>(relaxed = true)
            every { descriptor.taskPath } returns taskPath
            every { descriptor.originPlugin } returns null
            every { taskFinishEvent.descriptor } returns descriptor
            every { taskFinishEvent.eventTime } returns 0L
            every { taskFinishEvent.result } returns makeResult(outcome)
            handleTaskFinish.invoke(service, taskFinishEvent, build)
            return build
        }

        // SUCCESS -> null reason
        var build = run(BuildComponentOutcome.SUCCESS)
        assertNull(build.taskResults[":task"]!!.reason)
        assertEquals(BuildComponentOutcome.SUCCESS, build.taskResults[":task"]!!.outcome)

        // FAILED -> null reason
        build = run(BuildComponentOutcome.FAILED)
        assertNull(build.taskResults[":task"]!!.reason)
        assertEquals(BuildComponentOutcome.FAILED, build.taskResults[":task"]!!.outcome)

        // CANCELLED -> null reason
        build = run(BuildComponentOutcome.CANCELLED)
        assertNull(build.taskResults[":task"]!!.reason)

        // FROM_CACHE -> null reason; outcome enum is already sufficient
        build = run(BuildComponentOutcome.FROM_CACHE)
        assertNull(build.taskResults[":task"]!!.reason)
        assertEquals(BuildComponentOutcome.FROM_CACHE, build.taskResults[":task"]!!.outcome)

        // UP_TO_DATE -> null reason
        build = run(BuildComponentOutcome.UP_TO_DATE)
        assertNull(build.taskResults[":task"]!!.reason)
        assertEquals(BuildComponentOutcome.UP_TO_DATE, build.taskResults[":task"]!!.outcome)

        // NO_SOURCE -> verbatim skipMessage reason ("NO-SOURCE")
        build = run(BuildComponentOutcome.NO_SOURCE)
        assertEquals("NO-SOURCE", build.taskResults[":task"]!!.reason)
        assertEquals(BuildComponentOutcome.NO_SOURCE, build.taskResults[":task"]!!.outcome)

        // SKIPPED -> verbatim skipMessage reason, no "SKIPPED: " prefix
        build = run(BuildComponentOutcome.SKIPPED)
        assertEquals("OnlyIf / disabled", build.taskResults[":task"]!!.reason)
        assertEquals(BuildComponentOutcome.SKIPPED, build.taskResults[":task"]!!.outcome)
    }

    // --- configCacheReportPointer capture in BuildExecutionService ---

    @Test
    fun `captures configuration-cache report pointer from marker verbatim`() = runTest {
        val service = DefaultBuildExecutionService(
            envProvider = mockk<EnvProvider>(relaxed = true),
            initScriptProvider = mockk(relaxed = true),
            daemonJvmSelector = mockk(relaxed = true)
        )
        val build = createRunningBuild()

        val captureMarker = DefaultBuildExecutionService::class.java.getDeclaredMethod(
            "captureConfigCacheReportMarker", String::class.java, RunningBuild::class.java
        ).apply { isAccessible = true }
        captureMarker.invoke(service, "[MCP-CC-REPORT] file:///tmp/gradle/configuration-cache-report.html", build)

        assertEquals("file:///tmp/gradle/configuration-cache-report.html", build.configCacheReportPointer)
    }

    @Test
    fun `captures configuration-cache report pointer from fallback discovered line`() = runTest {
        val service = DefaultBuildExecutionService(
            envProvider = mockk<EnvProvider>(relaxed = true),
            initScriptProvider = mockk(relaxed = true),
            daemonJvmSelector = mockk(relaxed = true)
        )
        val build = createRunningBuild()

        val captureDiscovered = DefaultBuildExecutionService::class.java.getDeclaredMethod(
            "captureConfigCacheReportDiscoveredLine", String::class.java, RunningBuild::class.java
        ).apply { isAccessible = true }
        captureDiscovered.invoke(service, "See the complete report at file:///tmp/configuration-cache-report.html", build)

        assertEquals("file:///tmp/configuration-cache-report.html", build.configCacheReportPointer)
    }

    @Test
    fun `keeps configCacheReportPointer null when no report is captured`() = runTest {
        val service = DefaultBuildExecutionService(
            envProvider = mockk<EnvProvider>(relaxed = true),
            initScriptProvider = mockk(relaxed = true),
            daemonJvmSelector = mockk(relaxed = true)
        )
        val build = createRunningBuild()

        val captureMarker = DefaultBuildExecutionService::class.java.getDeclaredMethod(
            "captureConfigCacheReportMarker", String::class.java, RunningBuild::class.java
        ).apply { isAccessible = true }
        // Marker with an empty path must not set the pointer.
        captureMarker.invoke(service, "[MCP-CC-REPORT]   ", build)

        val captureDiscovered = DefaultBuildExecutionService::class.java.getDeclaredMethod(
            "captureConfigCacheReportDiscoveredLine", String::class.java, RunningBuild::class.java
        ).apply { isAccessible = true }
        captureDiscovered.invoke(service, "some unrelated message", build)

        assertNull(build.configCacheReportPointer)
    }

    @Test
    fun `keeps only the first captured configuration-cache report pointer`() = runTest {
        val service = DefaultBuildExecutionService(
            envProvider = mockk<EnvProvider>(relaxed = true),
            initScriptProvider = mockk(relaxed = true),
            daemonJvmSelector = mockk(relaxed = true)
        )
        val build = createRunningBuild()

        val captureMarker = DefaultBuildExecutionService::class.java.getDeclaredMethod(
            "captureConfigCacheReportMarker", String::class.java, RunningBuild::class.java
        ).apply { isAccessible = true }
        captureMarker.invoke(service, "[MCP-CC-REPORT] file:///first.html", build)
        captureMarker.invoke(service, "[MCP-CC-REPORT] file:///second.html", build)

        assertEquals("file:///first.html", build.configCacheReportPointer)
    }
}
