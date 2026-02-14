package dev.rnett.gradle.mcp.gradle.build

import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import kotlin.time.Duration

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

@Serializable
@JvmInline
@Description("The ID of a Gradle failure, used to identify the failure when looking up more information.")
value class FailureId(val id: String)

data class GradleBuildScan(
    val url: String,
    val id: String,
    val develocityInstance: String
) {
    companion object {
        fun fromUrl(url: String): GradleBuildScan {
            val fixed = url.replace("https://gradle.com/s/", "https://scans.gradle.com/s/")
            return GradleBuildScan(fixed, fixed.substringAfter("/s/"), fixed.substringBefore("/s/"))
        }
    }
}

enum class TaskOutcome {
    SUCCESS, FAILED, SKIPPED, UP_TO_DATE, FROM_CACHE, NO_SOURCE
}

enum class TestOutcome {
    PASSED, FAILED, SKIPPED
}