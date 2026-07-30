package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.build.FinishedBuild

data class GradleResult<out T>(
    val build: FinishedBuild,
    val value: Result<T>
)
