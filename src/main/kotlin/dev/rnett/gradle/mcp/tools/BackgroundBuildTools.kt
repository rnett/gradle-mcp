package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BackgroundBuildManager
import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildResults
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import kotlin.time.Clock

class BackgroundBuildTools(
    val gradle: GradleProvider,
) : McpServerComponent("Background Build Tools", "Tools for running and managing Gradle builds in the background.") {

    val runCommandBackground by tool<GradleExecutionTools.ExecuteCommandArgs, BuildId>(
        "background_run_gradle_command",
        """
            |Starts a Gradle command in the background. Returns the BuildId immediately.
            |Always prefer using this tool over invoking Gradle via the command line or shell.
            |Use `background_build_get_status` to monitor the build's progress and see the console output.
            |Once the build is complete, use the `lookup_*` tools to get detailed results, just like a foreground build.
        """.trimMargin()
    ) {
        gradle.doBuildBackground(
            it.projectRoot,
            GradleInvocationArguments(additionalArguments = it.commandLine, publishScan = it.scan) + it.invocationArguments,
        ).id
    }

    @Serializable
    class EmptyInput

    val listBackgroundBuilds by tool<EmptyInput, String>(
        "background_build_list",
        """
            |Returns a list of all active background builds.
            |The returned BuildIds can be used with `background_build_get_status`, `background_build_stop`, and the `lookup_*` tools.
        """.trimMargin()
    ) {
        val active = BackgroundBuildManager.listBuilds()
            .sortedByDescending { it.id.timestamp }

        if (active.isEmpty()) {
            return@tool "No active background builds."
        }

        buildString {
            appendLine("BuildId | Command line | Seconds ago | Status")
            active.forEach {
                val secondsAgo = it.id.timestamp.minus(Clock.System.now()).inWholeSeconds
                append(it.id).append(" | ")
                append(it.args.renderCommandLine()).append(" | ")
                append(secondsAgo).append("s ago | ")
                appendLine(it.status)
            }
        }
    }

    @Serializable
    data class BuildIdArgs(
        @Description("The build ID of the build to look up.")
        val buildId: BuildId? = null
    )

    @Serializable
    data class GetStatusArgs(
        val buildId: BuildId? = null,
        val maxTailLines: Int? = 20
    )

    val getBackgroundBuildStatus by tool<GetStatusArgs, String>(
        "background_build_get_status",
        """
            |Returns the detailed status of a background build, including its current status and the recent console output.
            |For completed builds, it returns a summary of the result.
            |
            |Arguments:
            | - buildId: The build ID of the build to look up.
            | - maxTailLines: The maximum number of lines of console output to return. Defaults to 20.
            |
            |Use the other `lookup_*` tools for more detailed information about completed builds, such as test results or build failures.
        """.trimMargin()
    ) {
        val buildId = it.buildId ?: throw IllegalArgumentException("buildId is required")
        val running = BackgroundBuildManager.getBuild(buildId)
        if (running != null) {
            return@tool buildString {
                appendLine("BUILD IN PROGRESS")
                appendLine("Build ID: ${running.id}")
                appendLine("Status: ${running.status}")
                appendLine("Start Time: ${running.startTime}")
                appendLine("Duration: ${Clock.System.now() - running.startTime}")
                appendLine("Command line: ${running.args.renderCommandLine()}")
                appendLine()
                val log = running.logBuffer
                var lineCount = 0
                var lastIndex = log.length
                val maxTailLines = it.maxTailLines ?: 20
                while (lineCount < maxTailLines && lastIndex > 0) {
                    val nextNewLine = log.lastIndexOf('\n', lastIndex - 2)
                    if (nextNewLine == -1) {
                        lastIndex = 0
                    } else {
                        lastIndex = nextNewLine + 1
                    }
                    lineCount++
                }

                appendLine("Console output: (last $lineCount lines shown)")
                append(log.substring(lastIndex))
            }
        }

        val completed = BuildResults.getResult(buildId)
            ?: throw IllegalArgumentException("Unknown or expired build ID: $buildId")

        return@tool "BUILD COMPLETED\n" + completed.toOutputString()
    }

    val stopBackgroundBuild by tool<BuildIdArgs, String>(
        "background_build_stop",
        "Requests that an active background build be stopped. Use `background_build_list` to see active builds."
    ) {
        val buildId = it.buildId ?: throw IllegalArgumentException("buildId is required")
        val running = BackgroundBuildManager.getBuild(buildId)
            ?: throw IllegalArgumentException("Build $buildId is not running or not found")

        running.stop()
        "Build $buildId stop requested"
    }
}
