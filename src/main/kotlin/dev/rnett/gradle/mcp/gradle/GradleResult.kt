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
            taskOutputs: Map<String, String>,
            problems: List<ProblemAggregation>,
            results: DefaultGradleProvider.TestCollector.Results,
            exception: GradleConnectionException?,
            outcome: Result<T>,
            taskOutputCapturingFailed: Boolean = false,
            taskResults: Map<String, BuildResult.TaskResult> = emptyMap()
        ): GradleResult<T> {
            return GradleResult(
                BuildResult.build(
                    args = args,
                    buildId = buildId,
                    console = console,
                    scans = scans,
                    taskOutputs = taskOutputs,
                    testResults = results,
                    problems = problems,
                    exception = exception,
                    taskOutputCapturingFailed = taskOutputCapturingFailed,
                    taskResults = taskResults
                ),
                outcome
            )
        }
    }
}


fun <T> GradleResult<T>.throwFailure(): Pair<BuildId, T> = value.getOrThrow().let { buildResult.id to it }