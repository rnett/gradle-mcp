package dev.rnett.gradle.mcp.gradle.build

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemId
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import dev.rnett.gradle.mcp.gradle.toId
import dev.rnett.gradle.mcp.mapToSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.Failure
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.events.problems.FileLocation
import org.gradle.tooling.events.problems.Location
import org.gradle.tooling.events.problems.PluginIdLocation
import org.gradle.tooling.events.problems.Problem
import org.gradle.tooling.events.problems.TaskPathLocation
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.Path
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
) : Build {
    val totalItems = AtomicLong(0)
    val completedItems = AtomicLong(0)
    var currentPhase: String? = null
        private set

    private val _activeOperations: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile
    var lastFinishedOperation: String? = null
        private set

    @Volatile
    var currentSubStatus: String? = null
        private set

    @Volatile
    var subStatusProgress: Double? = null
        private set

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
    val passedTests: Int get() = testResultsInternal.passedCount

    /**
     * The number of tests that have failed so far.
     */
    val failedTests: Int get() = testResultsInternal.failedCount

    /**
     * The number of tests that have been skipped so far.
     */
    val skippedTests: Int get() = testResultsInternal.skippedCount

    /**
     * The total number of tests detected so far (passed + failed + skipped + cancelled + in progress).
     */
    val totalTests: Int get() = testResultsInternal.totalCount

    private val _logLines = MutableSharedFlow<String>(replay = 10, extraBufferCapacity = 500, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val logLines: SharedFlow<String> = _logLines.asSharedFlow()

    private val _completingTasks = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 100, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val completingTasks: SharedFlow<String> = _completingTasks.asSharedFlow()

    private val _taskPaths: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    val completedTaskPaths: Set<String> get() = _taskPaths.toSet()

    override var status: BuildStatus = BuildStatus.Running
        private set

    override val consoleOutput: CharSequence get() = logBuffer

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

    private var lastLine: String? = null
    internal fun addLogLine(line: String) {
        lastLine = line
        logBuffer.append(line).append(System.lineSeparator())
        _logLines.tryEmit(line)
    }

    internal fun replaceLastLogLine(oldLine: String, newLine: String) {
        if (lastLine == oldLine || lastLine == "STDERR: $oldLine") {
            val toRemove = (lastLine ?: "") + System.lineSeparator()
            logBuffer.setLength(logBuffer.length - toRemove.length)
        }
        addLogLine(newLine)
    }

    val subTaskTotal = AtomicLong(0)
    val subTaskCompleted = AtomicLong(0)

    @Volatile
    var subTaskMessage: String? = null
        private set

    internal fun handleProgressLine(category: String, text: String) {
        val totalMatch = Regex("TOTAL: (\\d+)").matchEntire(text)
        if (totalMatch != null) {
            subTaskTotal.set(totalMatch.groupValues[1].toLong())
            subTaskCompleted.set(0)
            subTaskMessage = "Starting $category"
        } else {
            val progressMatch = Regex("(\\d+)/(\\d+): (.*)").matchEntire(text)
            if (progressMatch != null) {
                subTaskCompleted.set(progressMatch.groupValues[1].toLong())
                subTaskTotal.set(progressMatch.groupValues[2].toLong())
                val detail = progressMatch.groupValues[3]
                subTaskMessage = "$category: $detail"
            }
        }
    }

    internal fun addTaskResult(taskPath: String, outcome: TaskOutcome, duration: Duration, consoleOutput: String?) {
        taskResults[taskPath] = TaskResult(taskPath, outcome, duration, consoleOutput)
        _taskPaths.add(taskPath)
        _completingTasks.tryEmit(taskPath)
    }

    internal fun addTaskCompleted(taskPath: String) {
        if (!taskResults.containsKey(taskPath)) {
            _taskPaths.add(taskPath)
            _completingTasks.tryEmit(taskPath)
        }
    }

    internal fun addTaskOutput(taskPath: String, output: String) {
        taskOutputsAccumulator.getOrPut(taskPath) { StringBuffer() }.appendLine(output)
    }

    internal fun onPhaseStart(phase: String, total: Int) {
        currentPhase = phase
        totalItems.set(total.toLong())
        completedItems.set(0)
    }

    internal fun onItemFinish() {
        completedItems.incrementAndGet()
    }

    internal fun addActiveOperation(operation: String) {
        _activeOperations.add(operation)
    }

    internal fun removeActiveOperation(operation: String) {
        _activeOperations.remove(operation)
        lastFinishedOperation = operation
    }

    internal fun setSubStatus(status: String?, progress: Double? = null) {
        currentSubStatus = status
        subStatusProgress = progress
    }

    fun getProgressMessage(): String {
        val active = _activeOperations.toList()
        val phasePrefix = when {
            currentPhase?.contains("CONFIGUR") == true -> "[CONFIGURING] "
            currentPhase?.contains("EXECUT") == true || currentPhase?.contains("RUN") == true -> "[EXECUTING] "
            else -> ""
        }

        val subTaskMsg = if (subTaskMessage != null && subTaskTotal.get() > 0) {
            val completed = subTaskCompleted.get()
            val total = subTaskTotal.get()
            "$subTaskMessage ($completed/$total)"
        } else null

        val baseMessage = when {
            subTaskMsg != null -> subTaskMsg
            active.isNotEmpty() -> {
                val lead = active.first()
                if (active.size > 1) {
                    "$lead and ${active.size - 1} others"
                } else {
                    lead
                }
            }

            lastFinishedOperation != null -> "Finished $lastFinishedOperation"
            else -> currentPhase ?: "Running build"
        }

        val statusSuffix = buildString {
            if (currentSubStatus != null) {
                append(currentSubStatus)
                if (subStatusProgress != null) {
                    val percent = (subStatusProgress!! * 100).toInt()
                    append(" - $percent%")
                }
            }
        }.takeIf { it.isNotEmpty() }

        val fullMessage = if (statusSuffix != null) {
            "$baseMessage ($statusSuffix)"
        } else {
            baseMessage
        }

        return phasePrefix + fullMessage + getTestSummary()
    }

    private fun getTestSummary(): String {
        if (totalTests == 0) return ""

        val passed = passedTests
        val failed = failedTests
        val skipped = skippedTests

        val parts = mutableListOf<String>()
        if (passed > 0) parts += "$passed passed"
        if (failed > 0) parts += "$failed failed"
        if (skipped > 0) parts += "$skipped skipped"

        return if (parts.isNotEmpty()) " (${parts.joinToString(", ")})" else ""
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

data class FailureContent(
    val message: String?,
    val description: String?,
    val causes: Set<FailureContent>,
    val problemAggregations: Map<ProblemSeverity, List<ProblemAggregation>>
)

class FailureIndexer {
    private val indexes = mutableMapOf<FailureContent, FailureId>()

    @OptIn(ExperimentalUuidApi::class)
    fun index(content: FailureContent): FailureId = indexes.getOrPut(content) { FailureId(Uuid.random().toString()) }

    fun withIndex(content: FailureContent): dev.rnett.gradle.mcp.gradle.build.Failure = Failure(index(content), content.message, content.description, content.causes.map { withIndex(it) }, content.problemAggregations)
}

internal class ProblemsAccumulator {
    private val definitions = mutableMapOf<ProblemId, ProblemAggregation.ProblemDefinition>()
    private val problems = mutableMapOf<ProblemId, MutableSet<ProblemAggregation.ProblemOccurence>>()

    fun add(problem: ProblemAggregation) {
        definitions.putIfAbsent(problem.definition.id, problem.definition)
        problems.getOrPut(problem.definition.id) { mutableSetOf() }.addAll(problem.occurences)
    }

    fun add(problem: org.gradle.tooling.events.problems.ProblemAggregation) {
        add(problem.toModel())
    }

    fun add(problem: Problem) {
        add(problem.toModel())
    }

    fun aggregate(): List<ProblemAggregation> = definitions.map { (id, definition) ->
        ProblemAggregation(definition, problems[id].orEmpty().toList())
    }

    fun aggregateBySeverity(): Map<ProblemSeverity, List<ProblemAggregation>> = aggregate().groupBy { it.definition.severity }

    @Suppress("UNNECESSARY_SAFE_CALL")
    fun org.gradle.tooling.events.problems.ProblemAggregation.toModel(): ProblemAggregation {
        val aggregation = this
        return ProblemAggregation(
            definition = ProblemAggregation.ProblemDefinition(
                id = definition.id.toId(),
                displayName = definition.id.displayName,
                severity = when (definition.severity.severity) {
                    0 -> ProblemSeverity.ADVICE
                    1 -> ProblemSeverity.WARNING
                    2 -> ProblemSeverity.ERROR
                    else -> ProblemSeverity.OTHER
                },
                documentationLink = definition.documentationLink?.url
            ),
            occurences = aggregation.problemContext.map {
                ProblemAggregation.ProblemOccurence(
                    details = it.details?.details,
                    originLocations = it.originLocations.mapNotNull { it.toDescriptorString() },
                    contextualLocations = it.contextualLocations.mapNotNull { it.toDescriptorString() },
                    potentialSolutions = it.solutions.map { it.solution }
                )
            }
        )
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    fun Problem.toModel(): ProblemAggregation = ProblemAggregation(
        definition = ProblemAggregation.ProblemDefinition(
            id = definition.id.toId(),
            displayName = definition.id.displayName,
            severity = when (definition.severity.severity) {
                0 -> ProblemSeverity.ADVICE
                1 -> ProblemSeverity.WARNING
                2 -> ProblemSeverity.ERROR
                else -> ProblemSeverity.OTHER
            },
            documentationLink = definition.documentationLink?.url
        ),
        occurences = listOf(
            ProblemAggregation.ProblemOccurence(
                details = details?.details,
                originLocations = originLocations.mapNotNull { it.toDescriptorString() },
                contextualLocations = contextualLocations.mapNotNull { it.toDescriptorString() },
                potentialSolutions = solutions.map { it.solution }
            )
        )
    )

    fun Location.toDescriptorString(): String? = when (this) {
        is FileLocation -> "File: ${Path(path)}"
        is TaskPathLocation -> "Task: buildTreePath"
        is PluginIdLocation -> "Plugin: $pluginId"
        else -> null
    }
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

@OptIn(ExperimentalUuidApi::class)
private fun Failure.toContent(): FailureContent {
    val problemsAccumulator = ProblemsAccumulator()
    problems.forEach { problemsAccumulator.add(it) }
    return FailureContent(
        message,
        description,
        causes.mapToSet { it.toContent() },
        problemsAccumulator.aggregateBySeverity()
    )
}
