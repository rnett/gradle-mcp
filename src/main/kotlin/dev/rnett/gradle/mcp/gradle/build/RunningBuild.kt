package dev.rnett.gradle.mcp.gradle.build

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnectionException
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Represents a running Gradle build.
 */
class RunningBuild(
    override val id: BuildId,
    override val args: GradleInvocationArguments,
    override val startTime: Instant,
    val projectRoot: Path,
    val logBuffer: StringBuffer = StringBuffer(),
    val cancellationTokenSource: CancellationTokenSource
) : Build, BuildProgressInfoProvider {

    /**
     * The progress tracker for this running build.
     */
    val progressTracker = BuildProgressTracker(this)

    /**
     * The total number of items (e.g., tasks) in the current phase.
     */
    val totalItems get() = progressTracker.totalItems

    /**
     * The number of completed items in the current phase.
     */
    val completedItems get() = progressTracker.completedItems

    /**
     * The name of the current active phase.
     */
    val currentPhase get() = progressTracker.currentPhase

    internal val problemsAccumulator = ProblemsAccumulator()
    override val problems: List<ProblemAggregation> get() = problemsAccumulator.aggregate()
    override val problemAggregations: Map<ProblemSeverity, List<ProblemAggregation>> get() = problemsAccumulator.aggregateBySeverity()
    private val indexer = FailureIndexer()
    internal val testResultsInternal = TestCollector(true, true, cancellationTokenSource.token())
    override val testResults: TestResults
        get() {
            val results = testResultsInternal.results(Clock.System.now().toEpochMilliseconds())
            val inProgressOutcome = if (status == BuildStatus.Running) TestOutcome.IN_PROGRESS else TestOutcome.CANCELLED
            return TestResults(
                results.passed.map { it.toModel(indexer, TestOutcome.PASSED) }.toSet(),
                results.skipped.map { it.toModel(indexer, TestOutcome.SKIPPED) }.toSet(),
                results.failed.map { it.toModel(indexer, TestOutcome.FAILED) }.toSet(),
                results.cancelled.map { it.toModel(indexer, TestOutcome.CANCELLED) }.toSet() +
                        results.inProgress.map { it.toModel(indexer, inProgressOutcome) }.toSet()
            )
        }
    val publishedScansInternal = ConcurrentLinkedQueue<GradleBuildScan>()
    override val publishedScans: List<GradleBuildScan> get() = publishedScansInternal.toList()
    override val taskResults = ConcurrentHashMap<String, TaskResult>()
    val taskOutputsAccumulator = ConcurrentHashMap<String, StringBuffer>()
    override val taskOutputs: Map<String, String> get() = taskOutputsAccumulator.mapValues { it.value.toString() }
    override var taskOutputCapturingFailed: Boolean = false

    /**
     * The number of tests that have passed so far.
     */
    override val passedTests: Int get() = testResultsInternal.passedCount

    /**
     * The number of tests that have failed so far.
     */
    override val failedTests: Int get() = testResultsInternal.failedCount

    /**
     * The number of tests that have been skipped so far.
     */
    override val skippedTests: Int get() = testResultsInternal.skippedCount

    /**
     * The total number of tests detected so far (passed + failed + skipped + cancelled + in progress).
     */
    override val totalTests: Int get() = testResultsInternal.totalCount

    private val _logLines = MutableSharedFlow<String>(replay = 10, extraBufferCapacity = 500, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val logLines: SharedFlow<String> = _logLines.asSharedFlow()

    private val _completingTasks = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 100, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val completingTasks: SharedFlow<String> = _completingTasks.asSharedFlow()

    private val _taskPaths: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    val completedTaskPaths: Set<String> get() = _taskPaths.toSet()

    @Volatile
    override var status: BuildStatus = BuildStatus.Running
        private set

    override val consoleOutput: CharSequence get() = logBuffer.toString()

    private val finishedBuildDeferred = CompletableDeferred<FinishedBuild>()

    override suspend fun awaitFinished(): FinishedBuild {
        return finishedBuildDeferred.await()
    }

    private fun toFinishedBuild(exception: GradleConnectionException? = null): FinishedBuild {
        val buildFailures = exception?.failures?.map { indexer.withIndex(it.toContent()) }.orEmpty()
        val outcome = when {
            exception is BuildCancelledException -> BuildOutcome.Canceled
            buildFailures.isNotEmpty() -> BuildOutcome.Failed(buildFailures)
            else -> BuildOutcome.Success
        }

        return RefFinishedBuild(this, Clock.System.now(), outcome)
    }

    fun finish(exception: GradleConnectionException? = null, store: (FinishedBuild) -> Unit): FinishedBuild {
        val finished = toFinishedBuild(exception)
        this.status = finished.outcome
        store(finished)
        finishedBuildDeferred.complete(finished)
        return finished
    }

    fun stop() {
        if (hasBuildFinished) return
        testResultsInternal.isCancelled = true
        cancellationTokenSource.cancel()
    }

    internal fun addLogLine(line: String) {
        addLogLineInternal(line)
    }

    internal fun replaceLastLogLine(oldLine: String, newLine: String) {
        val suffix = oldLine + System.lineSeparator()
        val stderrSuffix = "STDERR: " + suffix

        val len = logBuffer.length
        if (len >= suffix.length) {
            val actual = logBuffer.substring(len - suffix.length)
            if (actual == suffix) {
                logBuffer.setLength(len - suffix.length)
            } else if (len >= stderrSuffix.length) {
                val actualStderr = logBuffer.substring(len - stderrSuffix.length)
                if (actualStderr == stderrSuffix) {
                    logBuffer.setLength(len - stderrSuffix.length)
                }
            }
        }
        addLogLineInternal(newLine)
    }

    private fun addLogLineInternal(line: String) {
        logBuffer.append(line).append(System.lineSeparator())
        _logLines.tryEmit(line)
    }

    internal fun addTaskResult(taskPath: String, outcome: TaskOutcome, duration: Duration, consoleOutput: String?) {
        taskResults[taskPath] = TaskResult(taskPath, outcome, duration, consoleOutput)
        _taskPaths.add(taskPath)
        _completingTasks.tryEmit(taskPath)
        progressTracker.emitProgress()
    }

    internal fun addTaskCompleted(taskPath: String) {
        if (!taskResults.containsKey(taskPath)) {
            _taskPaths.add(taskPath)
            _completingTasks.tryEmit(taskPath)
            progressTracker.emitProgress()
        }
    }

    internal fun addTaskOutput(taskPath: String, output: String) {
        taskOutputsAccumulator.getOrPut(taskPath) { StringBuffer() }.appendLine(output)
    }

}

private class RefFinishedBuild(val runningBuild: RunningBuild, override val finishTime: Instant, override val outcome: BuildOutcome) : FinishedBuild, Build by runningBuild {
    override suspend fun awaitFinished(): FinishedBuild {
        return this
    }

    override val taskResults: Map<String, TaskResult> = runningBuild.taskResults.mapValues { (path, result) ->
        if (result.consoleOutput == null) {
            result.copy(consoleOutput = runningBuild.taskOutputs[path])
        } else {
            result
        }
    }
    override val status: BuildStatus
        get() = runningBuild.status
}

private fun TestCollector.Result.toModel(indexer: FailureIndexer, status: TestOutcome): TestResult {
    return TestResult(
        testName,
        output,
        duration,
        failures?.map { indexer.withIndex(it.toContent()) },
        status,
        metadata,
        attachments.map { TestResult.Attachment(it.file, it.mediaType) }
    )
}
