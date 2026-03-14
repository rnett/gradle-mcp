package dev.rnett.gradle.mcp.gradle.build

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.events.test.source.TestSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.time.Clock

class RunningBuildProgressTest {

    private fun createRunningBuild(): RunningBuild {
        return RunningBuild(
            id = BuildId("test-id"),
            args = GradleInvocationArguments.DEFAULT,
            startTime = Clock.System.now(),
            projectRoot = Path("."),
            cancellationTokenSource = GradleConnector.newCancellationTokenSource()
        )
    }

    @Test
    fun `verifies progress message without tests`() {
        val build = createRunningBuild()
        build.progressTracker.onPhaseStart("RUN_MAIN_TASKS", 1)
        build.progressTracker.addActiveOperation(":test")

        assertEquals("[EXECUTING] :test", build.progressTracker.getProgressMessage())
    }

    @Test
    fun `verifies progress message with multiple operations improved format`() {
        val build = createRunningBuild()
        build.progressTracker.onPhaseStart("RUN_MAIN_TASKS", 2)
        build.progressTracker.addActiveOperation(":test")
        build.progressTracker.addActiveOperation(":other")

        assertEquals("[EXECUTING] :test (+1 other task)", build.progressTracker.getProgressMessage())
    }

    @Test
    fun `verifies progress message with many operations improved format`() {
        val build = createRunningBuild()
        build.progressTracker.onPhaseStart("RUN_MAIN_TASKS", 3)
        build.progressTracker.addActiveOperation(":test")
        build.progressTracker.addActiveOperation(":other1")
        build.progressTracker.addActiveOperation(":other2")

        assertEquals("[EXECUTING] :test (+2 other tasks)", build.progressTracker.getProgressMessage())
    }

    @Test
    fun `verifies sub-status clearing when operation finishes`() {
        val build = createRunningBuild()
        build.progressTracker.onPhaseStart("RUN_MAIN_TASKS", 1)
        build.progressTracker.addActiveOperation(":test")
        build.progressTracker.setSubStatus("Downloading", 0.5, ":test")

        assertEquals("[EXECUTING] :test (Downloading - 50%)", build.progressTracker.getProgressMessage())

        build.progressTracker.removeActiveOperation(":test")
        assertEquals("[EXECUTING] Finished :test", build.progressTracker.getProgressMessage())
        // Sub-status should be cleared because it was associated with :test
    }

    @Test
    fun `verifies progress message with test summary`() {
        val build = createRunningBuild()
        build.progressTracker.onPhaseStart("RUN_MAIN_TASKS", 1)
        build.progressTracker.addActiveOperation(":test")

        val collector = build.testResultsInternal

        // Helper to create a dummy Success result
        fun successEvent(name: String) = object : org.gradle.tooling.events.test.TestFinishEvent {
            override fun getEventTime(): Long = 1000
            override fun getDisplayName(): String = "Test $name finished"
            override fun getDescriptor() = object : org.gradle.tooling.events.test.JvmTestOperationDescriptor {
                override fun getDisplayName(): String = name
                override fun getName(): String = name
                override fun getParent() = null
                override fun getClassName(): String = "com.example.Test"
                override fun getMethodName(): String = name
                override fun getSuiteName(): String? = null
                override fun getJvmTestKind(): org.gradle.tooling.events.test.JvmTestKind = org.gradle.tooling.events.test.JvmTestKind.ATOMIC
                override fun getSource(): TestSource? = null
                override fun getTestDisplayName(): String = name
            }

            override fun getResult() = object : org.gradle.tooling.events.test.TestSuccessResult {
                override fun getStartTime(): Long = 0
                override fun getEndTime(): Long = 100
            }
        }

        collector.statusChanged(successEvent("test1"))
        collector.statusChanged(successEvent("test2"))

        assertEquals("[EXECUTING] :test (pass: 2)", build.progressTracker.getProgressMessage())
    }

    @Test
    fun `verifies complex test summary formatting`() {
        val build = createRunningBuild()
        build.progressTracker.onPhaseStart("RUN_MAIN_TASKS", 1)
        build.progressTracker.addActiveOperation(":test")

        val collector = build.testResultsInternal

        fun finishEvent(name: String, success: Boolean, skipped: Boolean = false) = object : org.gradle.tooling.events.test.TestFinishEvent {
            override fun getEventTime(): Long = 1000
            override fun getDisplayName(): String = name
            override fun getDescriptor() = object : org.gradle.tooling.events.test.JvmTestOperationDescriptor {
                override fun getDisplayName(): String = name
                override fun getName(): String = name
                override fun getParent() = null
                override fun getClassName(): String = "com.example.Test"
                override fun getMethodName(): String = name
                override fun getSuiteName(): String? = null
                override fun getJvmTestKind(): org.gradle.tooling.events.test.JvmTestKind = org.gradle.tooling.events.test.JvmTestKind.ATOMIC
                override fun getSource(): TestSource? = null
                override fun getTestDisplayName(): String = name
            }

            override fun getResult() = when {
                skipped -> object : org.gradle.tooling.events.test.TestSkippedResult {
                    override fun getStartTime(): Long = 0
                    override fun getEndTime(): Long = 100
                }

                success -> object : org.gradle.tooling.events.test.TestSuccessResult {
                    override fun getStartTime(): Long = 0
                    override fun getEndTime(): Long = 100
                }

                else -> object : org.gradle.tooling.events.test.TestFailureResult {
                    override fun getStartTime(): Long = 0
                    override fun getEndTime(): Long = 100
                    override fun getFailures(): List<org.gradle.tooling.Failure> = emptyList()
                }
            }
        }

        collector.statusChanged(finishEvent("t1", true))
        collector.statusChanged(finishEvent("t2", true))
        collector.statusChanged(finishEvent("t3", false))
        collector.statusChanged(finishEvent("t4", false, true))

        assertEquals("[EXECUTING] :test (pass: 2, fail: 1, skip: 1)", build.progressTracker.getProgressMessage())
    }

    @Test
    fun `verifies progress message with sub-task progress`() {
        val build = createRunningBuild()
        build.progressTracker.onPhaseStart("RUN_MAIN_TASKS", 1)
        build.progressTracker.handleProgressLine("SOURCE_RESOLUTION", "TOTAL: 10")
        build.progressTracker.handleProgressLine("SOURCE_RESOLUTION", "1/10: com.example:lib")

        assertEquals("[EXECUTING] SOURCE_RESOLUTION: com.example:lib (1/10)", build.progressTracker.getProgressMessage())
    }

    @Test
    fun `verifies progress updates even with unknown totalItems`() {
        val build = createRunningBuild()

        // Configuration phase starts but we don't have a total yet (e.g. Gradle < 7.6 or missed event)
        build.progressTracker.onPhaseStart("CONFIGURATION", 0)
        build.progressTracker.addActiveOperation(":project1")

        // Progress should be 0
        assertEquals(0.0, build.progressTracker.getProgressValue())

        // Project 1 finishes
        build.progressTracker.removeActiveOperation(":project1")
        build.progressTracker.onItemFinish()

        // With the fix, it should be 1 / (1 + 1) = 0.5
        assertEquals(0.5, build.progressTracker.getProgressValue())

        // Project 2 starts
        build.progressTracker.addActiveOperation(":project2")
        assertEquals(0.5, build.progressTracker.getProgressValue())

        // Project 2 finishes
        build.progressTracker.removeActiveOperation(":project2")
        build.progressTracker.onItemFinish()

        // 2 / (2 + 1) = 0.666...
        assertEquals(2.0 / 3.0, build.progressTracker.getProgressValue())
    }
}
