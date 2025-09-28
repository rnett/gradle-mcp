package dev.rnett.gradle.mcp.gradle

import kotlin.time.Duration.Companion.minutes

data class GradleScanTosAcceptRequest(val fullMessage: String, val host: String, val tosUrl: String) {
    companion object {
        val TIMEOUT = 10.minutes
    }
}