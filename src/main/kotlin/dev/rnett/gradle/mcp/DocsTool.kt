package dev.rnett.gradle.mcp

import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class DocsTool {
    @McpTool(
        name = "docs",
        description = "Get a link to the Gradle documentation",
    )
    fun getDocsTool(@McpToolParam(description = "The Gradle version to get documentation for. Uses the latest by default.", required = false) version: String?): Mono<String> = toolCall {
        if (version == null) return@toolCall docsUrl(null)

        val parts = version.trimStart('v').split(".")
        if (parts.size < 2 || parts.size > 3) {
            throw IllegalArgumentException("Expected a Gradle version with 2 or 3 dot-seperated numbers")
        }

        if (parts.any { it.toIntOrNull() == null }) {
            throw IllegalArgumentException("Gradle version must be 2 or 3 dot-seperated numbers")
        }

        docsUrl(version.trimStart('v'))
    }

    private fun docsUrl(version: String?) = "https://docs.gradle.org/${version ?: "current"}/userguide/userguide.html"
}