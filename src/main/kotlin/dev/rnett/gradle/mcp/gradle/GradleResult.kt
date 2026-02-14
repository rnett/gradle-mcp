package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.build.Build

data class GradleResult<out T>(
    val build: Build,
    val value: Result<T>
)


fun <T> GradleResult<T>.throwFailure(): Pair<BuildId, T> = value.getOrThrow().let { build.id to it }