package dev.rnett.gradle.mcp.gradle

data class GradleResult<out T>(
    val buildResult: Build,
    val value: Result<T>
)


fun <T> GradleResult<T>.throwFailure(): Pair<BuildId, T> = value.getOrThrow().let { buildResult.id to it }