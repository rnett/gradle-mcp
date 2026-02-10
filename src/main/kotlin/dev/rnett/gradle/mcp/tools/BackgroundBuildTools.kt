package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildStatus
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

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
        gradle.runBuild(
            it.projectRoot.resolve(),
            GradleInvocationArguments(additionalArguments = it.commandLine, publishScan = it.scan) + it.invocationArguments,
            tosAccepter = { false }
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
        val active = gradle.backgroundBuildManager.listBuilds()
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
        val maxTailLines: Int? = 20,
        @Description("Wait for the build to complete or for the waitFor regex to be seen in the output, for up to this many seconds.")
        val wait: Double? = null,
        @Description("A regex to wait for in the output. If seen, the wait will short-circuit, and all matching lines will be returned.")
        val waitFor: String? = null,
        @Description("A task path to wait for. If seen, the wait will short-circuit.")
        val waitForTask: String? = null,
        @Description("If true, only look for waitFor or waitForTask matches emitted after this call. Only applies if wait and (waitFor or waitForTask) are also provided.")
        val afterCall: Boolean = false
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val getBackgroundBuildStatus by tool<GetStatusArgs, String>(
        "background_build_get_status",
        """
            |Returns the detailed status of a background build, including its current status and the recent console output.
            |For completed builds, it returns a summary of the result.
            |
            |Arguments:
            | - buildId: The build ID of the build to look up.
            | - maxTailLines: The maximum number of lines of console output to return. Defaults to 20.
            | - wait: Wait for the build to complete or for the waitFor regex to be seen in the output, or for waitForTask to complete, for up to this many seconds.
            | - waitFor: A regex to wait for in the output. If seen, the wait will short-circuit, and all matching lines will be returned.
            | - waitForTask: A task path to wait for. If seen, the wait will short-circuit.
            | - afterCall: If true, only look for waitFor or waitForTask matches emitted after this call. Only applies if wait and (waitFor or waitForTask) are also provided.
            |
            | Use the other `lookup_*` tools for more detailed information about both running and completed builds, such as test results or build failures.
        """.trimMargin()
    ) {
        val buildId = it.buildId ?: throw IllegalArgumentException("buildId is required")

        val running = gradle.backgroundBuildManager.getBuild(buildId)
        val startLines = if (it.afterCall && running != null) running.consoleOutput.count { it == '\n' } else 0
        if (it.wait != null) {
            val waitForRegex = it.waitFor?.toRegex()
            val waitForTask = it.waitForTask



            withTimeoutOrNull(it.wait.seconds) {
                if (running != null) {
                    coroutineScope {
                        select {
                            onTimeout(it.wait.seconds) {}
                            if (waitForRegex != null) {
                                if (!it.afterCall && waitForRegex.containsMatchIn(running.logBuffer)) {
                                    launch { }.onJoin {}
                                } else {
                                    async {
                                        running.logLines.firstOrNull { line ->
                                            waitForRegex.containsMatchIn(line)
                                        }
                                    }.onAwait { }
                                }
                            }
                            if (waitForTask != null) {
                                if (!it.afterCall && running.completedTaskPaths.contains(waitForTask)) {
                                    launch { }.onJoin {}
                                } else {
                                    async {
                                        running.completedTasks.firstOrNull { taskPath ->
                                            taskPath == waitForTask
                                        }
                                    }.onAwait { }
                                }
                            }
                            running.result.onJoin { }
                        }
                    }
                }
            }
        }

        if (running != null && running.status == BuildStatus.RUNNING) {
            return@tool buildString {
                appendLine("BUILD IN PROGRESS")
                appendLine("Build ID: ${running.id}")
                appendLine("Status: ${running.status}")
                appendLine("Start Time: ${running.startTime}")
                appendLine("Duration: ${Clock.System.now() - running.startTime}")
                appendLine("Command line: ${running.args.renderCommandLine()}")
                appendLine()

                val waitForRegex = it.waitFor?.toRegex()
                if (waitForRegex != null) {
                    val matchingLines = running.logBuffer.lines().drop(startLines).filter { line -> line.isNotBlank() && waitForRegex.containsMatchIn(line) }
                    if (matchingLines.isNotEmpty()) {
                        appendLine("Matching lines for '${it.waitFor}':")
                        matchingLines.forEach { appendLine("    $it") }
                        appendLine()
                    } else {
                        appendLine("No matched lines - build completed or wait timed out")
                    }
                }

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

        val completed = gradle.buildResults.getResult(buildId)
            ?: throw IllegalArgumentException("Unknown or expired build ID: $buildId")

        return@tool buildString {
            appendLine("BUILD COMPLETED")
            val waitForRegex = it.waitFor?.toRegex()
            if (waitForRegex != null) {
                val matchingLines = completed.consoleOutput.lines().filter { waitForRegex.containsMatchIn(it) }
                if (matchingLines.isNotEmpty()) {
                    appendLine("Matching lines for '${it.waitFor}':")
                    matchingLines.forEach { appendLine(it) }
                    appendLine()
                }
            }
            append(completed.toOutputString())
        }
    }

    val stopBackgroundBuild by tool<BuildIdArgs, String>(
        "background_build_stop",
        "Requests that an active background build be stopped. Use `background_build_list` to see active builds."
    ) {
        val buildId = it.buildId ?: throw IllegalArgumentException("buildId is required")
        val running = gradle.backgroundBuildManager.getBuild(buildId)
            ?: throw IllegalArgumentException("Build $buildId is not running or not found")

        running.stop()
        "Build $buildId stop requested"
    }
}
