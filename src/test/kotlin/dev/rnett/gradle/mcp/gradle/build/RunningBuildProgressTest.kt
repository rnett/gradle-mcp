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
            id = BuildId.newId(),
            args = GradleInvocationArguments.DEFAULT,
            startTime = Clock.System.now(),
            projectRoot = Path("."),
            cancellationTokenSource = GradleConnector.newCancellationTokenSource()
        )
    }

    @Test
    fun `verifies progress message without tests`() {
        val build = createRunningBuild()
        build.onPhaseStart("RUN_MAIN_TASKS", 1)
        build.addActiveOperation(":test")

        assertEquals("[EXECUTING] :test", build.getProgressMessage())
    }

    @Test
    fun `verifies progress message with multiple operations`() {
        val build = createRunningBuild()
        build.onPhaseStart("RUN_MAIN_TASKS", 2)
        build.addActiveOperation(":test")
        build.addActiveOperation(":other")

        assertEquals("[EXECUTING] :test and 1 others", build.getProgressMessage())
    }

    @Test
    fun `verifies progress message with test summary`() {
        val build = createRunningBuild()
        build.onPhaseStart("RUN_MAIN_TASKS", 1)
        build.addActiveOperation(":test")

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

        assertEquals("[EXECUTING] :test (2 passed)", build.getProgressMessage())
    }

    @Test
    fun `verifies complex test summary formatting`() {
        val build = createRunningBuild()
        build.onPhaseStart("RUN_MAIN_TASKS", 1)
        build.addActiveOperation(":test")

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

        assertEquals("[EXECUTING] :test (2 passed, 1 failed, 1 skipped)", build.getProgressMessage())
    }

    @Test
    fun `verifies progress message with sub-task progress`() {
        val build = createRunningBuild()
        build.onPhaseStart("RUN_MAIN_TASKS", 1)
        build.handleProgressLine("SOURCE_RESOLUTION", "TOTAL: 10")
        build.handleProgressLine("SOURCE_RESOLUTION", "1/10: com.example:lib")

        assertEquals("[EXECUTING] SOURCE_RESOLUTION: com.example:lib (1/10)", build.getProgressMessage())
    }

    @Test
    fun `verifies progress message with percentage and sub-task`() {
        val build = createRunningBuild()
        build.onPhaseStart("RUN_MAIN_TASKS", 1)
        build.handleProgressLine("SOURCE_RESOLUTION", "TOTAL: 10")
        build.handleProgressLine("SOURCE_RESOLUTION", "1/10: com.example:lib")
        build.setSubStatus("Downloading lib.jar", 0.45)

        assertEquals("[EXECUTING] SOURCE_RESOLUTION: com.example:lib (1/10) (Downloading lib.jar - 45%)", build.getProgressMessage())
    }
}
