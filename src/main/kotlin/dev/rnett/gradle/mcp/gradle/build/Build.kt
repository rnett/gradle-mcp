package dev.rnett.gradle.mcp.gradle.build

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import kotlin.time.Instant

sealed interface Build {
    val status: BuildStatus
    val id: BuildId
    val args: GradleInvocationArguments
    val startTime: Instant get() = id.timestamp
    val consoleOutput: CharSequence
    val publishedScans: List<GradleBuildScan>
    val testResults: TestResults
    val problems: List<ProblemAggregation> get() = problemAggregations.values.flatten()
    val problemAggregations: Map<ProblemSeverity, List<ProblemAggregation>>
    val taskResults: Map<String, TaskResult>
    val taskOutputs: Map<String, String>
    val taskOutputCapturingFailed: Boolean

    val hasBuildFinished: Boolean get() = status is BuildOutcome
    val isRunning: Boolean get() = status is BuildStatus.Running
    suspend fun awaitFinished(): FinishedBuild

    val allTestFailures: Map<FailureId, Failure>
        get() = testResults.failed.asSequence().flatMap { it.failures.orEmpty().asSequence() }.flatMap { it.flatten() }.associateBy { it.id }

    fun getTaskOutput(taskPath: String, guess: Boolean = false): String? {
        val taskResult = taskResults[taskPath]
        if (taskResult?.consoleOutput != null) return taskResult.consoleOutput.trim()

        val taskOutput = taskOutputs[taskPath]
        if (taskOutput != null) return taskOutput.trim()

        if (!guess && !taskOutputCapturingFailed) {
            val hasCapturedSomething = taskResults.isNotEmpty() || taskOutputs.isNotEmpty()
            if (hasCapturedSomething) return null
        }

        val lines = consoleOutputLines
        val taskHeader = "> Task $taskPath"
        val startIndex = lines.indexOfFirst { it.startsWith(taskHeader) }
        if (startIndex == -1) return null

        val buildEnd = lines.drop(startIndex + 1).indexOfFirst {
            it.startsWith("> Task") || it.startsWith("BUILD SUCCESSFUL") || it.startsWith("BUILD FAILED") || it.startsWith("BUILD CANCELLED")
        }

        return if (buildEnd == -1) {
            lines.drop(startIndex + 1).joinToString("\n")
        } else {
            lines.subList(startIndex + 1, startIndex + 1 + buildEnd).joinToString("\n")
        }.trim()
    }

    val consoleOutputLines: List<String> get() = consoleOutput.lines()

}

sealed interface BuildStatus {
    data object Running : BuildStatus
}

sealed interface BuildOutcome : BuildStatus {
    data object Success : BuildOutcome
    data object Canceled : BuildOutcome
    data class Failed(val failures: List<Failure>) : BuildOutcome
}

val BuildStatus.hasFinished: Boolean get() = this is BuildOutcome

val BuildOutcome.failuresIfFailed: List<Failure>? get() = if (this is BuildOutcome.Failed) failures else null

interface FinishedBuild : Build {
    override val status: BuildStatus
        get() = outcome
    val outcome: BuildOutcome
    val finishTime: Instant

    val allBuildFailures: Map<FailureId, Failure>
        get() = outcome.failuresIfFailed.orEmpty().asSequence().flatMap { it.flatten() }.associateBy { it.id }.minus(allTestFailures.keys)

    val allFailures: Map<FailureId, Failure>
        get() = allTestFailures + allBuildFailures

    override suspend fun awaitFinished(): FinishedBuild = this
}

private data class FrozenBuild(
    override val id: BuildId,
    override val args: GradleInvocationArguments,
    override val consoleOutput: CharSequence,
    override val publishedScans: List<GradleBuildScan>,
    override val testResults: TestResults,
    override val problemAggregations: Map<ProblemSeverity, List<ProblemAggregation>>,
    override val taskResults: Map<String, TaskResult> = emptyMap(),
    override val taskOutputs: Map<String, String> = emptyMap(),
    override val taskOutputCapturingFailed: Boolean = false,
    override val outcome: BuildOutcome,
    override val finishTime: Instant
) : FinishedBuild {
    override val consoleOutputLines by lazy { consoleOutput.lines() }
}

internal fun FinishedBuild.freeze(): FinishedBuild = FrozenBuild(
    id = id,
    args = args,
    consoleOutput = consoleOutput,
    publishedScans = publishedScans,
    testResults = testResults,
    problemAggregations = problemAggregations,
    taskResults = taskResults,
    taskOutputs = taskOutputs,
    taskOutputCapturingFailed = taskOutputCapturingFailed,
    outcome = outcome,
    finishTime = finishTime
)

fun FinishedBuild(
    id: BuildId,
    args: GradleInvocationArguments,
    consoleOutput: CharSequence,
    publishedScans: List<GradleBuildScan>,
    testResults: TestResults,
    problemAggregations: Map<ProblemSeverity, List<ProblemAggregation>>,
    taskResults: Map<String, TaskResult> = emptyMap(),
    taskOutputs: Map<String, String> = emptyMap(),
    taskOutputCapturingFailed: Boolean = false,
    outcome: BuildOutcome,
    finishTime: Instant
): FinishedBuild = FrozenBuild(
    id = id,
    args = args,
    consoleOutput = consoleOutput,
    publishedScans = publishedScans,
    testResults = testResults,
    problemAggregations = problemAggregations,
    taskResults = taskResults,
    taskOutputs = taskOutputs,
    taskOutputCapturingFailed = taskOutputCapturingFailed,
    outcome = outcome,
    finishTime = finishTime
)
