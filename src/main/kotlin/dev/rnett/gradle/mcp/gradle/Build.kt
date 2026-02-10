package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.mapToSet
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface Build {
    val id: BuildId
    val args: GradleInvocationArguments
    val startTime: Instant
    val consoleOutput: CharSequence
    val publishedScans: Collection<GradleBuildScan>
    val testResults: TestResults
    val problems: List<ProblemAggregation>
    val problemAggregations: Map<ProblemSeverity, List<ProblemAggregation>>
    val taskResults: Map<String, TaskResult>
    val taskOutputs: Map<String, String>
    val taskOutputCapturingFailed: Boolean

    val isRunning: Boolean
    val isSuccessful: Boolean?

    val buildFailures: List<Failure>?

    val allTestFailures: Map<FailureId, Failure>
        get() = testResults.failed.asSequence().flatMap { it.failures.orEmpty().asSequence() }.flatMap { it.flatten() }.associateBy { it.id }

    val allBuildFailures: Map<FailureId, Failure>
        get() = buildFailures.orEmpty().asSequence().flatMap { it.flatten() }.associateBy { it.id }.minus(allTestFailures.keys)

    val allFailures: Map<FailureId, Failure>
        get() = allTestFailures + allBuildFailures

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

    @Serializable
    data class TaskResult(
        val path: String,
        val outcome: TaskOutcome,
        val duration: Duration,
        val consoleOutput: String?
    )

    @Serializable
    data class TestResults(
        val passed: Set<TestResult>,
        val skipped: Set<TestResult>,
        val failed: Set<TestResult>,
    ) {
        val totalCount = passed.size + skipped.size + failed.size
        val isEmpty = totalCount == 0
        val all by lazy {
            sequence {
                yieldAll(passed)
                yieldAll(skipped)
                yieldAll(failed)
            }
        }
    }

    @Serializable
    data class TestResult(
        val testName: String,
        val consoleOutput: String?,
        val executionDuration: Duration,
        val failures: List<Failure>?,
        val status: TestOutcome
    )

    @Serializable
    data class Failure(
        val id: FailureId,
        val message: String?,
        val description: String?,
        val causes: List<Failure>,
        val problemAggregations: Map<ProblemSeverity, List<ProblemAggregation>>
    ) {
        val problems: List<ProblemAggregation> get() = problemAggregations.values.flatten()
        fun flatten(): Sequence<Failure> = sequence {
            yield(this@Failure)
            yieldAll(causes.asSequence().flatMap { it.flatten() })
        }

        fun writeFailureTree(sb: StringBuilder, prefix: String = "") {
            sb.appendLine(prefix + "${id.id} - ${message ?: "No message"}")
            description?.prependIndent("$prefix  ")?.let { sb.appendLine(it) }

            if (problems.isNotEmpty()) {
                sb.appendLine("$prefix  Problems:")
                problems.forEach { sb.appendLine("$prefix    ${it.definition.id}") }
            }

            if (causes.isNotEmpty()) {
                sb.appendLine("$prefix  Causes:")
                causes.forEach { it.writeFailureTree(sb, "$prefix    ") }
            }
        }
    }

    data class FailureContent(
        val message: String?,
        val description: String?,
        val causes: Set<FailureContent>,
        val problemAggregations: Map<ProblemSeverity, List<ProblemAggregation>>,
    )

    class FailureIndexer {
        private val indexes = mutableMapOf<FailureContent, FailureId>()

        @OptIn(ExperimentalUuidApi::class)
        fun index(content: FailureContent): FailureId = indexes.getOrPut(content) { FailureId(Uuid.random().toString()) }

        fun withIndex(content: FailureContent): Failure = Failure(index(content), content.message, content.description, content.causes.map { withIndex(it) }, content.problemAggregations)
    }

    class ProblemsAccumulator {
        private val definitions = mutableMapOf<ProblemId, ProblemAggregation.ProblemDefinition>()
        private val problems = mutableMapOf<ProblemId, MutableSet<ProblemAggregation.ProblemOccurence>>()

        fun add(problem: ProblemAggregation) {
            definitions.putIfAbsent(problem.definition.id, problem.definition)
            problems.getOrPut(problem.definition.id) { mutableSetOf() }.addAll(problem.occurences)
        }

        fun add(problem: org.gradle.tooling.events.problems.ProblemAggregation) {
            add(problem.toModel())
        }

        fun add(problem: org.gradle.tooling.events.problems.Problem) {
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
        fun org.gradle.tooling.events.problems.Problem.toModel(): ProblemAggregation = ProblemAggregation(
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

        fun org.gradle.tooling.events.problems.Location.toDescriptorString(): String? = when (this) {
            is org.gradle.tooling.events.problems.FileLocation -> "File: ${kotlin.io.path.Path(path)}"
            is org.gradle.tooling.events.problems.TaskPathLocation -> "Task: buildTreePath"
            is org.gradle.tooling.events.problems.PluginIdLocation -> "Plugin: $pluginId"
            else -> null
        }
    }

    companion object {

        fun <T> build(
            args: GradleInvocationArguments,
            buildId: BuildId,
            console: CharSequence,
            scans: List<GradleBuildScan>,
            taskOutputs: Map<String, String>,
            testResults: DefaultGradleProvider.TestCollector.Results,
            problems: List<ProblemAggregation>,
            exception: org.gradle.tooling.GradleConnectionException?,
            taskOutputCapturingFailed: Boolean = false,
            taskResults: Map<String, TaskResult> = emptyMap(),
        ): BuildResult {

            val indexer = FailureIndexer()

            val modelTestResults = testResults.let {
                TestResults(
                    it.passed.mapToSet { it.toModel(indexer, TestOutcome.PASSED) },
                    it.skipped.mapToSet { it.toModel(indexer, TestOutcome.SKIPPED) },
                    it.failed.mapToSet { it.toModel(indexer, TestOutcome.FAILED) },
                )
            }

            val buildFailures = exception?.failures?.mapToSet { indexer.withIndex(it.toContent()) }

            return BuildResult(
                id = buildId,
                args = args,
                consoleOutput = console,
                publishedScans = scans,
                testResults = modelTestResults,
                buildFailures = buildFailures?.toList(),
                problemAggregations = problems.asSequence().groupBy { it.definition.severity },
                taskOutputs = taskOutputs,
                taskOutputCapturingFailed = taskOutputCapturingFailed,
                taskResults = taskResults
            )
        }

        fun DefaultGradleProvider.TestCollector.Result.toModel(indexer: FailureIndexer, status: TestOutcome): TestResult {
            return TestResult(
                testName,
                output,
                duration,
                failures?.map { indexer.withIndex(it.toContent()) },
                status
            )
        }

        @OptIn(ExperimentalUuidApi::class)
        fun org.gradle.tooling.Failure.toContent(): FailureContent {
            val problemsAccumulator = ProblemsAccumulator()
            problems.forEach { problemsAccumulator.add(it) }
            return FailureContent(
                message,
                description,
                causes.mapToSet { it.toContent() },
                problemsAccumulator.aggregateBySeverity()
            )
        }
    }
}
