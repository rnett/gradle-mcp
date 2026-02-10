package dev.rnett.gradle.mcp.gradle

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import kotlin.time.Instant

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

data class BuildResult(
    override val id: BuildId,
    override val args: GradleInvocationArguments,
    override val consoleOutput: CharSequence,
    override val publishedScans: List<GradleBuildScan>,
    override val testResults: Build.TestResults,
    override val buildFailures: List<Build.Failure>?,
    override val problemAggregations: Map<ProblemSeverity, List<ProblemAggregation>>,
    override val taskResults: Map<String, Build.TaskResult> = emptyMap(),
    override val taskOutputs: Map<String, String> = emptyMap(),
    override val taskOutputCapturingFailed: Boolean = false
) : Build {
    override val consoleOutputLines by lazy { consoleOutput.lines() }
    override val startTime: Instant get() = id.timestamp
    override val problems: List<ProblemAggregation> get() = problemAggregations.values.flatten()
    override val isRunning: Boolean get() = false
    override val isSuccessful: Boolean get() = buildFailures == null
}