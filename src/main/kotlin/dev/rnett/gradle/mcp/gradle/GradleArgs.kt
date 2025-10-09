package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.tools.*
import io.github.smiley4.schemakenerator.core.annotations.*
import kotlinx.serialization.*
import org.gradle.tooling.model.*
import kotlin.io.path.*

fun ProjectIdentifier.matches(root: GradleProjectRoot, projectPath: GradleProjectPath): Boolean =
    buildIdentifier.rootDir.toPath().absolute() == GradlePathUtils.getRootProjectPath(root) && this.projectPath == projectPath.path

@Serializable
@Description("Additional arguments to configure the Gradle process.")
data class GradleInvocationArguments(
    @Description("Additional environment variables to set for the Gradle process. Optional. The process inherits the MCP server's env vars unless `doNotInheritEnvVars` is set to true. Note that the MCP server may not have the same env vars as the MCP Host - you may need to pass sone.")
    val additionalEnvVars: Map<String, String> = emptyMap(),
    @Description("Additional system properties to set for the Gradle process. Optional. No system properties are inherited from the MCP server.")
    val additionalSystemProps: Map<String, String> = emptyMap(),
    @Description("Additional JVM arguments to set for the Gradle process. Optional.")
    val additionalJvmArgs: List<String> = emptyList(),
    @Description("Additional arguments for the Gradle process. Optional.")
    val additionalArguments: List<String> = emptyList(),
    @Description("Whether to attempt to publish a Develocity Build Scan by using the '--scan' argument. Optional, defaults to false. Using Build Scans is the best way to investigate failures, especially if you have access to the Develocity MCP server. Publishing build scans to scans.gradle.com requires the MCP client to support elicitation.")
    val publishScan: Boolean = false,
    @Description("Defaults to true. If false, will not inherit env vars from the MCP server.")
    val doNotInheritEnvVars: Boolean = false,
) {
    operator fun plus(other: GradleInvocationArguments): GradleInvocationArguments {
        return GradleInvocationArguments(
            additionalEnvVars = additionalEnvVars + other.additionalEnvVars,
            additionalSystemProps = additionalSystemProps + other.additionalSystemProps,
            additionalJvmArgs = additionalJvmArgs + other.additionalJvmArgs,
            additionalArguments = additionalArguments + other.additionalArguments,
            publishScan = publishScan || other.publishScan
        )
    }

    @Transient
    val allAdditionalArguments = additionalArguments + (if (publishScan && "--scan" !in additionalArguments) listOf("--scan") else emptyList())

    companion object {
        val DEFAULT = GradleInvocationArguments()
    }
}

@JvmInline
@Serializable
@Description("The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.")
value class GradleProjectRoot(val projectRoot: String)

@JvmInline
@Serializable
@Description("The Gradle project path, e.g. :project-a:subproject-b. ':' is the root project.  Defaults to ':'")
@Example(":")
@Example(":my-project")
@Example(":my-project:subproject")
value class GradleProjectPath(private val projectPath: String) {
    companion object {
        val DEFAULT = GradleProjectPath(":")
    }

    val path get() = ':' + projectPath.trim(':')

    val isRootProject get() = projectPath.isBlank() || projectPath == ":"

    fun taskPath(task: String): String = buildString {
        append(path)
        if (!isRootProject)
            append(':')
        append(task.trimStart(':'))
    }

    override fun toString(): String {
        return path
    }
}