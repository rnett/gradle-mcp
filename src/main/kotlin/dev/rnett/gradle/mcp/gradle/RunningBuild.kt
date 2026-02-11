package dev.rnett.gradle.mcp.gradle

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.CancellationTokenSource
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration
import kotlin.time.Instant

@Serializable
enum class BuildStatus {
    RUNNING, SUCCESSFUL, FAILED, CANCELLED
}

interface IRunningBuild<T> : Build {
    val projectRoot: Path
    val logBuffer: StringBuffer
    val cancellationTokenSource: CancellationTokenSource
    val result: CompletableDeferred<GradleResult<T>>
    val logLines: SharedFlow<String>
    val completedTasks: SharedFlow<String>
    val completedTaskPaths: Set<String>
    val status: BuildStatus
    val endTime: Instant?

    fun stop()
}

data class RunningBuild<T>(
    override val id: BuildId,
    override val args: GradleInvocationArguments,
    override val startTime: Instant,
    override val projectRoot: Path,
    override val logBuffer: StringBuffer = StringBuffer(),
    override val cancellationTokenSource: CancellationTokenSource,
    private val backgroundBuildManager: BackgroundBuildManager,
    override val result: CompletableDeferred<GradleResult<T>> = CompletableDeferred()
) : IRunningBuild<T> {
    val problemsAccumulator = Build.ProblemsAccumulator()
    override val problems: List<ProblemAggregation> get() = problemsAccumulator.aggregate()
    override val problemAggregations: Map<ProblemSeverity, List<ProblemAggregation>> get() = problemsAccumulator.aggregateBySeverity()
    private val indexer = Build.FailureIndexer()
    override val testResults: Build.TestResults
        get() = Build.TestResults(
            testResultsInternal.results().passed.map { with(Build.Companion) { it.toModel(indexer, TestOutcome.PASSED) } }.toSet(),
            testResultsInternal.results().skipped.map { with(Build.Companion) { it.toModel(indexer, TestOutcome.SKIPPED) } }.toSet(),
            testResultsInternal.results().failed.map { with(Build.Companion) { it.toModel(indexer, TestOutcome.FAILED) } }.toSet(),
        )
    override val buildFailures: List<Build.Failure>? get() = null
    val testResultsInternal = DefaultGradleProvider.TestCollector(true, true)
    override val publishedScans = ConcurrentLinkedQueue<GradleBuildScan>()
    override val taskResults = ConcurrentHashMap<String, Build.TaskResult>()
    val taskOutputsAccumulator = ConcurrentHashMap<String, StringBuilder>()
    override val taskOutputs: Map<String, String> get() = taskOutputsAccumulator.mapValues { it.value.toString() }
    override var taskOutputCapturingFailed: Boolean = false

    override val isRunning: Boolean get() = status == BuildStatus.RUNNING
    override val isSuccessful: Boolean?
        get() = when (status) {
            BuildStatus.RUNNING -> null
            BuildStatus.SUCCESSFUL -> true
            BuildStatus.FAILED -> false
            BuildStatus.CANCELLED -> false
        }

    private val _logLines = MutableSharedFlow<String>(replay = 1)
    override val logLines: SharedFlow<String> = _logLines.asSharedFlow()

    private val _completedTasks = MutableSharedFlow<String>(replay = 1)
    override val completedTasks: SharedFlow<String> = _completedTasks.asSharedFlow()

    private val _taskPaths: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    override val completedTaskPaths: Set<String> get() = _taskPaths.toSet()

    override var status: BuildStatus = BuildStatus.RUNNING
        private set
    override var endTime: Instant? = null
        private set

    override val consoleOutput: CharSequence get() = logBuffer

    fun toResult(exception: org.gradle.tooling.GradleConnectionException? = null, finalTaskResults: Map<String, Build.TaskResult>? = null): BuildResult {
        return Build.build<Nothing>(
            args = args,
            buildId = id,
            console = consoleOutput,
            scans = publishedScans.toList(),
            taskOutputs = taskOutputs,
            testResults = testResultsInternal.results(),
            problems = problems,
            exception = exception,
            taskOutputCapturingFailed = taskOutputCapturingFailed,
            taskResults = finalTaskResults ?: taskResults.mapValues { (path, result) ->
                if (result.consoleOutput == null) {
                    result.copy(consoleOutput = taskOutputs[path])
                } else {
                    result
                }
            }
        )
    }

    suspend fun awaitFinished(): GradleResult<T> = result.await()

    override fun stop() {
        cancellationTokenSource.cancel()
    }

    internal fun updateStatus(gradleResult: GradleResult<T>? = null, exception: Throwable? = null) {
        if (gradleResult != null) {
            val finalStatus = when {
                gradleResult.value.exceptionOrNull() is BuildCancelledException -> BuildStatus.CANCELLED
                gradleResult.buildResult.isSuccessful == true -> BuildStatus.SUCCESSFUL
                else -> BuildStatus.FAILED
            }
            this.status = finalStatus
            this.result.complete(gradleResult)
        } else if (exception != null) {
            val finalStatus = if (exception is BuildCancelledException) BuildStatus.CANCELLED else BuildStatus.FAILED
            this.status = finalStatus
            this.result.completeExceptionally(exception)
        }

        if (this.status != BuildStatus.RUNNING) {
            this.endTime = kotlin.time.Clock.System.now()
            backgroundBuildManager.removeBuild(id)
        }
    }

    private var lastLine: String? = null
    internal fun addLogLine(line: String) {
        lastLine = line
        logBuffer.append(line).append(System.lineSeparator())
        _logLines.tryEmit(line)
    }

    internal fun replaceLastLogLine(oldLine: String, newLine: String) {
        if (lastLine == oldLine || lastLine == "ERR: $oldLine") {
            val toRemove = (lastLine ?: "") + System.lineSeparator()
            logBuffer.setLength(logBuffer.length - toRemove.length)
        }
        addLogLine(newLine)
    }

    internal fun addTaskResult(taskPath: String, outcome: TaskOutcome, duration: Duration, consoleOutput: String?) {
        taskResults[taskPath] = Build.TaskResult(taskPath, outcome, duration, consoleOutput)
        _taskPaths.add(taskPath)
        _completedTasks.tryEmit(taskPath)
    }

    internal fun addTaskCompleted(taskPath: String) {
        if (!taskResults.containsKey(taskPath)) {
            _taskPaths.add(taskPath)
            _completedTasks.tryEmit(taskPath)
        }
    }

    internal fun addTaskOutput(taskPath: String, output: String) {
        taskOutputsAccumulator.getOrPut(taskPath) { StringBuilder() }.appendLine(output)
    }
}
