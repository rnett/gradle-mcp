package dev.rnett.gradle.mcp.gradle

import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
class GradleConfiguration(
    val maxConnections: Int,
    val ttl: Duration,
    val allowPublicScansPublishing: Boolean
)