package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.build.FinishedBuild

data class GradleResult<out T>(
    val build: FinishedBuild,
    val value: Result<T>
)


fun <T> GradleResult<T>.throwFailure(): Pair<BuildId, T> = value.getOrThrow().let { build.id to it }