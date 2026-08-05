package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.LatestStableGradleVersion
import dev.rnett.gradle.mcp.LatestStableGradleVersion.Source

/** Runtime note, rendered with provenance: never asserts a fallback value is the live latest. */
fun latestStableGradleVersionNote(resolution: LatestStableGradleVersion): String = when (resolution.source) {
    Source.FETCHED_LIVE ->
        "The latest stable Gradle version is **${resolution.version}** " +
            "(resolved at server startup from https://services.gradle.org/versions/current)."
    Source.BUNDLED_FALLBACK ->
        "The latest stable Gradle version could not be verified at server startup " +
            "(https://services.gradle.org/versions/current was unreachable); the newest version " +
            "this server knows of is **${resolution.version}**, the Gradle version it was built " +
            "against — newer versions may exist."
}

/** Static note for generated docs: describes the startup policy, claims no check outcome. */
fun latestStableGradleVersionNoteForDocs(version: String): String =
    "At server startup this server resolves the latest stable Gradle version from " +
        "https://services.gradle.org/versions/current and reports it here; if that check fails, " +
        "the Gradle version the server was built against (currently **$version**) is reported instead."
