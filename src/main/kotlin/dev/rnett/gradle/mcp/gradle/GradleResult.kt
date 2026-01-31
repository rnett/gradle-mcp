package dev.rnett.gradle.mcp.gradle

import org.gradle.tooling.GradleConnectionException

data class GradleResult<out T>(
    val buildResult: BuildResult,
    val value: Result<T>
) {
    companion object {
        fun <T> build(
            args: GradleInvocationArguments,
            buildId: BuildId,
            console: CharSequence,
            scans: List<GradleBuildScan>,
            problems: List<ProblemAggregation>,
            results: DefaultGradleProvider.TestCollector.Results,
            exception: GradleConnectionException?,
            outcome: Result<T>
        ): GradleResult<T> {
            return GradleResult(
                BuildResult.build(args, buildId, console, scans, results, problems, exception),
                outcome
            )
        }
    }
}


fun <T> GradleResult<T>.throwFailure(): Pair<BuildId, T> = value.getOrThrow().let { buildResult.id to it }