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
        @Description("Capturing and returning output for a specific task path (e.g., ':app:dependencies') exclusively. This is highly token-efficient as it eliminates all non-task console noise. Output over 100 lines will be truncated; use `inspect_build` for full logs. DO NOT use this for tests; ALWAYS use `inspect_build` with `testName` and `mode=\"details\"` for isolated, untruncated individual test output and stack traces.")
        val captureTaskOutput: String? = null,
        @Description("Applying additional advanced invocation arguments for the Gradle process.")
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    @OptIn(ExperimentalTime::class)
    val gradle by tool<GradleExecuteArgs, String>(
        ToolNames.GRADLE,
        """
            |ALWAYS use this tool to execute Gradle builds, tasks, and tests instead of raw shell commands.
            |This tool provides a managed environment with high-resolution feedback, authoritative background orchestration, and surgical task output capturing.
            |
            |### Task Execution Best Practices
            |
            |1.  **Foreground is Preferred**: Foreground execution is STRONGLY PREFERRED for most tasks as it provides superior progressive disclosure.
            |2.  **Use Background Surgically**: Use `background=true` for long-running servers or when you explicitly intend to perform other tasks in parallel.
            |3.  **Task Output Capturing**: Use `captureTaskOutput=":path:to:task"` for a clean, task-specific view of the console output.
            |4.  **DO NOT use for individual tests**: For individual test failures and stack traces, ALWAYS use `${ToolNames.INSPECT_BUILD}` with `testName` and `mode="details"`. `captureTaskOutput` will be incomplete and lack test-specific diagnostics.
            |
            |### Wait & Progress Monitoring
            |After starting a build, use `${ToolNames.INSPECT_BUILD}` with the returned `BuildId` to monitor progress or perform deep-dive failure diagnostics.
            |
            |Note: Avoid `--rerun-tasks` (which reruns ALL tasks) unless investigating broad cache issues. Prefer `--rerun` for individual tasks. Use `invocationArguments: { envSource: "SHELL" }` if Gradle isn't finding expected environment variables (e.g., JDKs).
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
                invocationArgs.withInitScript(InitScriptNames.TASK_OUT)
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
                            appendLine("... (Task output truncated to last $TASK_OUTPUT_MAX_LINES lines. Use `${ToolNames.INSPECT_BUILD}(buildId=\"${finished.id}\", taskPath=\"${it.captureTaskOutput}\", mode=\"details\")` for the full task output or use `consoleTail=true` for the full console output)")
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
