package dev.rnett.gradle.mcp.gradle

import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
class GradleConnectionConfiguration(val maxConnections: Int, val ttl: Duration) {
}