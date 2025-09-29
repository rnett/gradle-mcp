package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.GradleResult
import dev.rnett.gradle.mcp.gradle.GradleScanTosAcceptRequest
import dev.rnett.gradle.mcp.gradle.runBuildAndGetOutput
import dev.rnett.gradle.mcp.gradle.runTestsAndGetOutput
import dev.rnett.gradle.mcp.mcp.McpContext
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Example
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
    @Description("Whether to attempt to publish a Develocity Build Scan by using the '--scan' argument. Optional, defaults to false. Using Build Scans is the best way to investigate failures, especially if you have access to the Develocity MCP server. Publishing build scans to scans.gradle.com requires the MCP client to support elicitation.")
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

@JvmInline
@Serializable
@Description("The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.")
value class GradleProjectRoot(val projectRoot: String)

@JvmInline
@Serializable
@Description("The Gradle project path, e.g. :project-a:subproject-b. ':' is the root project.")
@Example(":")
@Example(":my-project")
@Example(":my-project:subproject")
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
        stdoutLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.notice, it)
            ctx.emitProgressNotification(0.0, 0.0, it)
        },
        stderrLineHandler = { ctx.emitLoggingNotification("gradle-build", LoggingLevel.error, it) },
    )
}


context(ctx: McpContext)
suspend inline fun GradleProvider.doBuild(
    projectRoot: String,
    captureFailedTestOutput: Boolean,
    captureAllTestOutput: Boolean,
    invocationArgs: GradleInvocationArguments,
    includeFailures: Boolean,
): BuildResult {
    return runBuildAndGetOutput(
        projectRoot,
        captureFailedTestOutput,
        captureAllTestOutput,
        invocationArgs,
        includeFailures,
        { askForScansTos(it) },
        stdoutLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.notice, it)
            ctx.emitProgressNotification(0.0, 0.0, it)
        },
        stderrLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.error, it)
        }
    )
}


context(ctx: McpContext)
suspend inline fun GradleProvider.doTests(
    projectRoot: String,
    testPatterns: Map<String, Set<String>>,
    captureFailedTestOutput: Boolean,
    captureAllTestOutput: Boolean,
    invocationArgs: GradleInvocationArguments,
    includeFailures: Boolean,
): BuildResult {
    return runTestsAndGetOutput(
        projectRoot,
        testPatterns,
        captureFailedTestOutput,
        captureAllTestOutput,
        invocationArgs,
        includeFailures,
        { askForScansTos(it) },
        stdoutLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.notice, it)
            ctx.emitProgressNotification(0.0, 0.0, it)
        },
        stderrLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.error, it)
        }
    )
}