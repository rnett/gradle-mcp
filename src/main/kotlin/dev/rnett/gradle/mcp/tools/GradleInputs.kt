package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.expandPath
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
@Description("Absolute path to Gradle project root (parent of gradlew and settings.gradle). Defaults to GRADLE_MCP_PROJECT_ROOT when omitted.")
value class GradleProjectRootInput(val projectRoot: String? = null) {
    companion object {
        val DEFAULT = GradleProjectRootInput(null)
    }
}

fun GradleProjectRootInput.resolve(): GradleProjectRoot =
    resolve(System.getenv("GRADLE_MCP_PROJECT_ROOT"))

internal fun GradleProjectRootInput.resolve(envRoot: String?): GradleProjectRoot {
    val selectedRoot = projectRoot?.takeIf { it.isNotBlank() }
        ?: envRoot?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException(
            "No Gradle project root configured. Provide projectRoot or set GRADLE_MCP_PROJECT_ROOT."
        )
    return GradleProjectRoot(selectedRoot.expandPath())
}
