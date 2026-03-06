package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

class GradleExecutionTools(
    val gradleProvider: GradleProvider
) : McpServerComponent("Execution Tools", "Tools for executing Gradle tasks and running tests.") {
    companion object {
        private const val TASK_OUTPUT_MAX_LINES = 100
    }

    @Serializable
    data class GradleExecuteArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The arguments for gradle. Syntax: ':task' (root only), 'task' (all projects), or ':app:task'. Required if not stopping a build.")
        val commandLine: List<String>? = null,
        @Description("Setting to true starts the build in the background and returns a managed BuildId immediately. Use ONLY for persistent tasks (e.g., servers) or when you explicitly intend to perform other tasks in parallel. Foreground is STRONGLY PREFERRED for most tasks as it provides superior progressive disclosure.")
        val background: Boolean = false,
        @Description("Terminating an active background build by providing its BuildId. If provided, all other arguments are ignored.")
        val stopBuildId: BuildId? = null,
        @Description("Capturing and returning output for a specific task path (e.g., ':app:dependencies') exclusively. This is highly token-efficient as it eliminates all non-task console noise. Output over 100 lines will be truncated; use `inspect_build` for full logs.")
        val captureTaskOutput: String? = null,
        @Description("Applying additional advanced invocation arguments for the Gradle process.")
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    @OptIn(ExperimentalTime::class)
    val gradle by tool<GradleExecuteArgs, String>(
        ToolNames.GRADLE,
        """
            |ALWAYS use this tool to execute Gradle builds, tasks, and tests instead of raw shell commands.
            |It provides a managed environment with high-resolution feedback, background execution, and isolated task output capturing (`captureTaskOutput`), which is vastly superior and more token-efficient than parsing standard console logs.
            |For deep diagnostics after a build, ALWAYS use `${ToolNames.INSPECT_BUILD}` with the returned `BuildId`.
            |Note: Avoid `--rerun-tasks` unless investigating cache issues.
        """.trimMargin()
    ) {
        if (it.stopBuildId != null) {
            val build = gradleProvider.buildManager.getBuild(it.stopBuildId) as? RunningBuild
                ?: throw IllegalArgumentException("Build ${it.stopBuildId} is not running or not found")
            build.stop()
            return@tool "Build ${it.stopBuildId} stopped"
        }

        val commandLine = it.commandLine
            ?: throw IllegalArgumentException("commandLine is required when not stopping a build.")

        val invocationArgs = it.invocationArguments.copy(additionalArguments = commandLine)

        if (it.background) {
            val root = it.projectRoot.resolve()

            @OptIn(kotlinx.coroutines.FlowPreview::class)
            val running = gradleProvider.runBuild(
                root,
                invocationArgs.withInitScript(InitScriptNames.TASK_OUT),
                progressHandler = { p, total, msg ->
                    // For background builds, we don't have an easy way to emit progress notifications 
                    // via the current tool call result, but DefaultGradleProvider will still update 
                    // the RunningBuild state which can be inspected via inspect_build.
                }
            )
            return@tool running.id.toString()
        } else {
            val result = gradleProvider.doBuild(
                it.projectRoot,
                invocationArgs
            )

            val finished = result.build.awaitFinished()
            val isSpecial = finished.args.isHelp || finished.args.isVersion
            if (finished.outcome !is BuildOutcome.Success && !isSpecial) {
                isError = true
            }

            if (isSpecial) {
                return@tool finished.toOutputString()
            }

            if (it.captureTaskOutput != null) {
                return@tool buildString {
                    if (finished.taskOutputCapturingFailed) {
                        appendLine("Task output capturing failed. Task output may be incomplete or interleaved with other tasks.\n")
                    }
                    val taskOut = finished.getTaskOutput(it.captureTaskOutput, true)
                    if (taskOut != null) {
                        val lines = taskOut.lines()
                        if (lines.size > TASK_OUTPUT_MAX_LINES) {
                            appendLine("... (Task output truncated to last $TASK_OUTPUT_MAX_LINES lines. Use `${ToolNames.INSPECT_BUILD}` tool with buildId ${finished.id} and the console mode for full output)")
                            appendLine()
                            append(lines.takeLast(TASK_OUTPUT_MAX_LINES).joinToString("\n"))
                        } else {
                            append(taskOut)
                        }
                    } else {
                        append("Task output for ${it.captureTaskOutput} not found in console output. Build result:\n${finished.toOutputString()}")
                    }
                }
            }

            return@tool finished.toOutputString()
        }
    }
}
