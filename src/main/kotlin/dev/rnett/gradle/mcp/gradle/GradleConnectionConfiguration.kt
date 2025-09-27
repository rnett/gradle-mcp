package dev.rnett.gradle.mcp.gradle

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gradle")
class GradleConnectionConfiguration(val maxConnections: Int, val ttl: java.time.Duration) {
}