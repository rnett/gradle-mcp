package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.LatestStableGradleVersion
import dev.rnett.gradle.mcp.LatestStableGradleVersion.Source

/** Runtime note for the MCP server instructions: never asserts a fallback value is the live latest. */
fun latestStableGradleVersionInstructionsLine(resolution: LatestStableGradleVersion): String = when (resolution.source) {
    Source.FETCHED_LIVE -> "The latest Gradle version is **${resolution.version}**."
    Source.BUNDLED_FALLBACK -> "The latest Gradle version is **${resolution.version}** (this server's bundled Gradle version)."
}
