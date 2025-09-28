package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.GradleResult
import dev.rnett.gradle.mcp.gradle.GradleScanTosAcceptRequest
import dev.rnett.gradle.mcp.gradle.runBuildAndGetOutput
import dev.rnett.gradle.mcp.mcp.McpContext
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.modelcontextprotocol.kotlin.sdk.LoggingLevel
import kotlinx.serialization.Serializable
import org.gradle.tooling.model.Model


@Serializable
@Description("Additional arguments to configure the Gradle process.")
data class GradleInvocationArguments(
    @Description("Additional environment variables to set for the Gradle process. Optional.")
    val additionalEnvVars: Map<String, String> = emptyMap(),
    @Description("Additional system properties to set for the Gradle process. Optional.")
    val additionalSystemProps: Map<String, String> = emptyMap(),
    @Description("Additional JVM arguments to set for the Gradle process. Optional.")
    val additionalJvmArgs: List<String> = emptyList(),
    @Description("Additional arguments for the Gradle process. Optional.")
    val additionalArguments: List<String> = emptyList(),
    @Description("Whether to attempt to publish a Develocity Build Scan by using the '--scan' argument. Optional, defaults to false. Using Build Scans is the best way to investigate failures, especially if you have access to the Develocity MCP server.")
    val publishScan: Boolean = false,
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


    val allAdditionalArguments = additionalArguments + (if (publishScan && "--scan" !in additionalArguments) listOf("--scan") else emptyList())

    companion object {
        val DEFAULT = GradleInvocationArguments()
    }
}

fun GradleInvocationArguments?.orDefault(): GradleInvocationArguments = this ?: GradleInvocationArguments.DEFAULT

@JvmInline
@Serializable
@Description("The path of the Gradle project's root, where the gradlew script and settings.gradle(.kts) files are located")
value class GradleProjectRoot(val projectRoot: String)

@JvmInline
@Serializable
@Description("The Gradle project path, e.g. :project-a:subproject-b. ':' is the root project.")
value class GradleProjectPath(val projectPath: String)

//TODO have a "remember acceptance" option?
@PublishedApi
context(ctx: McpContext)
internal suspend fun askForScansTos(tosAcceptRequest: GradleScanTosAcceptRequest): Boolean {
    return ctx.elicitUnit(tosAcceptRequest.fullMessage, GradleScanTosAcceptRequest.TIMEOUT).isAccepted
}

context(ctx: McpContext)
suspend inline fun <reified T : Model> GradleProvider.getBuildModel(
    projectRoot: String,
    invocationArgs: GradleInvocationArguments,
): GradleResult<T> {
    return getBuildModel(
        projectRoot,
        T::class,
        invocationArgs,
        { askForScansTos(it) },
        stdoutLineHandler = { ctx.emitLoggingNotification("gradle-build", LoggingLevel.notice, it) },
        stderrLineHandler = { ctx.emitLoggingNotification("gradle-build", LoggingLevel.error, it) },
    )
}


context(ctx: McpContext)
suspend inline fun GradleProvider.runBuildAndGetOutput(
    projectRoot: String,
    invocationArgs: GradleInvocationArguments,
    includeFailures: Boolean,
): BuildResult {
    return runBuildAndGetOutput(
        projectRoot,
        invocationArgs,
        includeFailures,
        { askForScansTos(it) },
        stdoutLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.notice, it)
        },
        stderrLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.error, it)
        }
    )
}