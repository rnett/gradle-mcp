package dev.rnett.gradle.mcp.gradle

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.events.problems.ProblemAggregation

data class GradleResult<out T>(
    val buildResult: BuildResult,
    val value: Result<T>
) {
    companion object {
        fun <T> build(
            buildId: BuildId,
            console: String,
            scans: List<GradleBuildScan>,
            problems: List<ProblemAggregation>,
            results: GradleProvider.TestCollector.Results,
            exception: GradleConnectionException?,
            outcome: Result<T>
        ): GradleResult<T> {
            return GradleResult(
                BuildResult.build(buildId, console, scans, results, problems, exception),
                outcome
            )
        }
    }
}


fun <T> GradleResult<T>.throwFailure(): T = value.getOrThrow()