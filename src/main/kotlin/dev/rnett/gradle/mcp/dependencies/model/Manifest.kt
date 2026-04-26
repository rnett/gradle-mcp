package dev.rnett.gradle.mcp.dependencies.model

import kotlinx.serialization.Serializable

@Serializable
data class ProjectManifest(
    val sessionId: String,
    val timestamp: String,
    val dependencies: List<ManifestDependency>,
    val failedDependencies: List<String> = emptyList()
)

@Serializable
data class ManifestDependency(
    val id: String,
    val hash: String,
    val relativePath: String,
    /**
     * Reserved for a future provider-level optimization. When true, this dep's session-view link
     * points to `normalized-target/` (platform-specific files only) rather than `normalized/`.
     * **All current search providers ignore this flag** — they receive a `Map<Path, Boolean>` but
     * extract only `.keys`. Deduplication of common-sibling results is handled by the filesystem
     * existence filter in `IndexService.search`.
     */
    val isDiffOnly: Boolean = false
)