package dev.rnett.gradle.mcp.dependencies.model

import kotlinx.serialization.Serializable

@Serializable
data class ProjectManifest(
    val sessionId: String,
    val timestamp: String,
    val dependencies: List<ManifestDependency>
)

@Serializable
data class ManifestDependency(
    val id: String,
    val hash: String,
    val relativePath: String,
    val isDiffOnly: Boolean = false
)