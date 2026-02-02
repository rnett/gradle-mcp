package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.tools.GradlePathUtils
import dev.rnett.gradle.mcp.utils.EnvHelper
import dev.rnett.gradle.mcp.utils.EnvProvider
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Example
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.gradle.tooling.model.ProjectIdentifier
import kotlin.io.path.absolute

fun ProjectIdentifier.matches(root: GradleProjectRoot, projectPath: GradleProjectPath): Boolean =
    buildIdentifier.rootDir.toPath().absolute() == GradlePathUtils.getRootProjectPath(root) && this.projectPath == projectPath.path

@Serializable
@Description("Additional arguments to configure the Gradle process.")
data class GradleInvocationArguments(
    @Description("Additional environment variables to set for the Gradle process. Optional.")
    val additionalEnvVars: Map<String, String> = emptyMap(),
    @Description("Additional system properties to set for the Gradle process. Optional. No system properties are inherited from the MCP server.")
    val additionalSystemProps: Map<String, String> = emptyMap(),
    @Description("Additional JVM arguments to set for the Gradle process. Optional.")
    val additionalJvmArgs: List<String> = emptyList(),
    @Description("Additional arguments for the Gradle process. Optional.")
    val additionalArguments: List<String> = emptyList(),
    @Description("Whether to attempt to publish a Develocity Build Scan by using the '--scan' argument. Optional, defaults to false. Using Build Scans is the best way to investigate failures, especially if you have access to the Develocity MCP server. Publishing build scans to scans.gradle.com requires the MCP client to support elicitation.")
    val publishScan: Boolean = false,
    @Description("Where to get the environment variables from to pass to Gradle. Defaults to INHERIT. SHELL starts a new shell process and queries its env vars.")
    val envSource: EnvSource = EnvSource.INHERIT,
) {
    operator fun plus(other: GradleInvocationArguments): GradleInvocationArguments {
        return GradleInvocationArguments(
            additionalEnvVars = additionalEnvVars + other.additionalEnvVars,
            additionalSystemProps = additionalSystemProps + other.additionalSystemProps,
            additionalJvmArgs = additionalJvmArgs + other.additionalJvmArgs,
            additionalArguments = additionalArguments + other.additionalArguments,
            publishScan = publishScan || other.publishScan,
            envSource = if (other.envSource != EnvSource.INHERIT) other.envSource else envSource
        )
    }

    fun renderCommandLine(envProvider: EnvProvider = EnvHelper): String = buildString {
        actualEnvVars(envProvider).forEach { (k, v) ->
            append("$k=$v ")
        }
        if (additionalJvmArgs.isNotEmpty() || additionalSystemProps.isNotEmpty()) {
            append("java ")
            additionalJvmArgs.forEach { a ->
                append("$a ")
            }
            additionalSystemProps.forEach { (k, v) ->
                append("-D$k=$v ")
            }
        }
        append("gradle ")
        allAdditionalArguments.forEach { a ->
            append(a).append(" ")
        }
    }.trim()

    fun actualEnvVars(envProvider: EnvProvider = EnvHelper): Map<String, String> {
        val base = when (envSource) {
            EnvSource.NONE -> emptyMap()
            EnvSource.INHERIT -> envProvider.getInheritedEnvironment()
            EnvSource.SHELL -> envProvider.getShellEnvironment()
        }
        if (additionalEnvVars.isEmpty()) return base

        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        if (!isWindows) return base + additionalEnvVars

        val result = base.toMutableMap()
        additionalEnvVars.forEach { (k, v) ->
            val existingKey = result.keys.find { it.equals(k, ignoreCase = true) }
            if (existingKey != null) {
                result.remove(existingKey)
            }
            result[k] = v
        }
        return result
    }

    @Transient
    val allAdditionalArguments = additionalArguments + (if (publishScan && "--scan" !in additionalArguments) listOf("--scan") else emptyList())

    companion object {
        val DEFAULT = GradleInvocationArguments()
    }
}

@Serializable
enum class EnvSource {
    NONE,
    INHERIT,
    SHELL
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