package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.modelcontextprotocol.kotlin.sdk.Annotations
import io.modelcontextprotocol.kotlin.sdk.Role
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

class GradleExecutionTools(
    val gradle: GradleProvider,
) : McpServerComponent() {
    @Serializable
    data class ExecuteCommandArgs(
        val projectRoot: GradleProjectRoot,
        @Description("The Gradle command to run. Will be ran as if it had been passed directly to './gradlew'")
        val commandLine: List<String>,
        @Description("Whether to run with the --scan argument to publish a build scan. Requires a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false.")
        val scan: Boolean = false,
        @Description("Whether to include failure information in the result, if the build fails. Defaults to false. The information can be helpful in diagnosing failures, but is very verbose.")
        val includeFailureInformation: Boolean = false,
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    @OptIn(ExperimentalTime::class)
    val executeCommand by tool<ExecuteCommandArgs, BuildResult>(
        "run_gradle_command",
        """
            |Runs a Gradle command in the given project, just as if the command line had been passed directly to './gradlew'.
            |The console output is included in the result. Show this to the user, as if they had ran the command themselves.
            |Can publish a Develocity Build Scan if requested. This is the preferred way to diagnose issues, using something like the Develocity MCP server.
        """.trimMargin(),
    ) {
        val result = gradle.runBuildAndGetOutput(
            it.projectRoot.projectRoot,
            GradleInvocationArguments(additionalArguments = it.commandLine, publishScan = it.scan) + it.invocationArguments,
            it.includeFailureInformation
        )

        addAdditionalContent(TextContent(result.output, Annotations(listOf(Role.user), null, 1.0)))

        if (result is BuildResult.BuildFailure) {
            isError = true
        }

        result
    }

}