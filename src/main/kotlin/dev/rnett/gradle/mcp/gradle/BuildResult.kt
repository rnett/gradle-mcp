package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.mapToSet
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.gradle.tooling.GradleConnectionException
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@JvmInline
@Description("The ID of a Gradle failure, used to identify the failure when looking up more information.")
value class FailureId(val id: String)

@Serializable
@Description("A reference to a Develocity Build Scan")
data class GradleBuildScan(
    @Description("The URL of the Build Scan. Can be used to view it.")
    val url: String,
    @Description("The Build Scan's ID")
    val id: String,
    @Description("The URL of the Develocity instance the Build Scan is located on")
    val develocityInstance: String
) {
    companion object {
        fun fromUrl(url: String): GradleBuildScan {
            val fixed = url.replace("https://gradle.com/s/", "https://scans.gradle.com/s/")
            return GradleBuildScan(fixed, fixed.substringAfter("/s/"), fixed.substringBefore("/s/"))
        }
    }
}

@Serializable
@Description("The outcome of a test.")
enum class TestOutcome {
    PASSED, FAILED, SKIPPED
}

data class BuildResult(
    val id: BuildId,
    val args: GradleInvocationArguments,
    val consoleOutput: CharSequence,
    val publishedScans: List<GradleBuildScan>,
    val testResults: TestResults,
    val buildFailures: List<Failure>?,
    val problems: Map<ProblemSeverity, List<ProblemAggregation>>
) {
    val consoleOutputLines by lazy { consoleOutput.lines() }
    val isSuccessful: Boolean = buildFailures == null

    val allTestFailures by lazy {
        testResults.failed.asSequence().flatMap { it.failures.orEmpty().asSequence() }.flatMap { it.flatten() }.associateBy { it.id }
    }
    val allBuildFailures by lazy { buildFailures.orEmpty().asSequence().flatMap { it.flatten() }.associateBy { it.id }.minus(allTestFailures.keys) }
    val allFailures by lazy { allTestFailures + allBuildFailures }

    data class Failure(
        val id: FailureId,
        val message: String?,
        val description: String?,
        val causes: List<Failure>,
        val problems: List<ProblemId>,
    ) {
        fun flatten(): Sequence<Failure> = sequence {
            yield(this@Failure)
            yieldAll(causes.asSequence().flatMap { it.flatten() })
        }

        fun writeFailureTree(sb: StringBuilder, prefix: String = "") {
            sb.appendLine(prefix + "${id.id} - ${message ?: "No message"}")
            description?.prependIndent("$prefix  ")?.let { sb.appendLine(it) }

            if (problems.isNotEmpty()) {
                sb.appendLine("$prefix  Problems:")
                problems.forEach { sb.appendLine("$prefix    ${it.id}") }
            }

            if (causes.isNotEmpty()) {
                sb.appendLine("$prefix  Causes:")
                causes.forEach { it.writeFailureTree(sb, "$prefix    ") }
            }
        }
    }

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

    data class TestResult(
        val testName: String,
        val consoleOutput: String?,
        val executionDuration: Duration,
        val failures: List<Failure>?,
        val status: TestOutcome
    )

    private data class FailureContent(
        val message: String?,
        val description: String?,
        val causes: Set<FailureContent>,
        val problems: List<ProblemId>,
    )

    private class FailureIndexer {
        private val indexes = mutableMapOf<FailureContent, FailureId>()

        @OptIn(ExperimentalUuidApi::class)
        fun index(content: FailureContent): FailureId = indexes.getOrPut(content) { FailureId(Uuid.random().toString()) }

        fun withIndex(content: FailureContent): Failure = Failure(index(content), content.message, content.description, content.causes.map { withIndex(it) }, content.problems)
    }

    companion object {

        fun build(
            args: GradleInvocationArguments,
            buildId: BuildId,
            console: CharSequence,
            scans: List<GradleBuildScan>,
            testResults: DefaultGradleProvider.TestCollector.Results,
            problems: List<ProblemAggregation>,
            exception: GradleConnectionException?
        ): BuildResult {

            val indexer = FailureIndexer()

            val modelTestResults = testResults.let {
                BuildResult.TestResults(
                    it.passed.mapToSet { it.toModel(indexer, TestOutcome.PASSED) },
                    it.skipped.mapToSet { it.toModel(indexer, TestOutcome.SKIPPED) },
                    it.failed.mapToSet { it.toModel(indexer, TestOutcome.FAILED) },
                )
            }

            val buildFailures = exception?.failures?.mapToSet { indexer.withIndex(it.toContent()) }

            return BuildResult(
                buildId,
                args,
                console,
                scans,
                modelTestResults,
                buildFailures?.toList(),
                problems.asSequence().groupBy { it.definition.severity }
            )
        }


        @OptIn(ExperimentalUuidApi::class)
        private fun org.gradle.tooling.Failure.toContent(): BuildResult.FailureContent {
            return BuildResult.FailureContent(
                message,
                description,
                causes.mapToSet { it.toContent() },
                problems.map { it.definition.id.toId() }
            )
        }


        private fun DefaultGradleProvider.TestCollector.Result.toModel(indexer: FailureIndexer, status: TestOutcome): BuildResult.TestResult {
            return BuildResult.TestResult(
                testName,
                output,
                duration,
                failures?.map { indexer.withIndex(it.toContent()) },
                status
            )
        }
    }
}