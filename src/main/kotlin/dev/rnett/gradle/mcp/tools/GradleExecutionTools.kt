package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.mcp.ToolCallResult
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

class GradleExecutionTools(
    val gradleProvider: GradleProvider,
) : McpServerComponent("Execution Tools", "Tools for executing Gradle tasks and running tests.") {
    companion object {
        private const val TASK_OUTPUT_MAX_LINES = 100
    }

    override suspend fun close() {
        gradleProvider.close()
    }

    @Serializable
    data class GradleExecuteArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("Gradle CLI args. ':task' (root), 'task' (all projects), ':app:task'. Required if not stopping.")
        val commandLine: List<String>? = null,
        @Description("Start build in background; returns BuildId. Use only for servers or parallel work.")
        val background: Boolean = false,
        @Description("Stop an active background build by BuildId; all other args are ignored.")
        val stopBuildId: BuildId? = null,
        @Description("Isolated output for a specific task path. DO NOT use for tests; use query_build with kind=\"TESTS\" and query=\"FullTestName\".")
        val captureTaskOutput: String? = null,
        @Description("Additional advanced invocation arguments for the Gradle process.")
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    init {
        @OptIn(ExperimentalTime::class)
    tool<GradleExecuteArgs, String>(
        ToolNames.GRADLE,
        """
            |`./gradlew` replacement for Gradle task execution. Executes Gradle builds and tasks with background orchestration, task output capturing, and progressive feedback.
            |
            |### Task Execution
            |- **Foreground** (default): STRONGLY PREFERRED; provides progressive output.
            |- **Background** (`background=true`): Use only for persistent tasks (servers) or parallel work.
            |- **Task Output Capturing** (`captureTaskOutput=\":path:to:task\"`): Returns clean task-specific output.
            |   - **DO NOT use Task Output Capturing for tests**: Use `${ToolNames.QUERY_BUILD}` with `kind=\"TESTS\"` and `query=\"FullTestName\".
            |
            |After starting a build, use `${ToolNames.QUERY_BUILD}` or `${ToolNames.WAIT_BUILD}` with the returned `BuildId` for progress, failures, test results, and task output.
            |
            |Not for reading Gradle source code; use `gradleOwnSource`-based source tools instead.
            |Note: Prefer `--rerun` (single task) over `--rerun-tasks` (all tasks, even included builds). Use `invocationArguments: { envSource: \"SHELL\" }` if env vars (e.g., JDKs) aren't found.
        """.trimMargin()
    ) { it, progressReporter ->
        if (it.stopBuildId != null) {
            val build = gradleProvider.buildManager.getBuild(it.stopBuildId) as? RunningBuild
                ?: throw IllegalArgumentException("Build ${it.stopBuildId} is not running or not found")
            build.stop()
            return@tool ToolCallResult("Build ${it.stopBuildId} stopped")
        }

        val commandLine = it.commandLine
            ?: throw IllegalArgumentException("commandLine is required when not stopping a build.")

        val invocationArgs = it.invocationArguments.copy(additionalArguments = commandLine + it.invocationArguments.additionalArguments)

        if (it.background) {
            progressReporter.report(0.0, 1.0, "Starting background Gradle build...")
            val root = it.projectRoot.resolve()

            @OptIn(kotlinx.coroutines.FlowPreview::class)
            val running = gradleProvider.runBuild(
                root,
                invocationArgs.withInitScript(InitScriptNames.TASK_OUT).withInitScript(InitScriptNames.CC_REPORT)
            )
            return@tool ToolCallResult(running.id.toString())
        } else {
            val finished = with(progressReporter) {
                gradleProvider.doBuild(
                    it.projectRoot,
                    invocationArgs
                )
            }

            val isSpecial = finished.args.isHelp || finished.args.isVersion
            val isError = finished.outcome !is BuildOutcome.Success && !isSpecial

            if (isSpecial) {
                return@tool ToolCallResult(finished.toOutputString(), isError)
            }

            if (it.captureTaskOutput != null) {
                return@tool ToolCallResult(buildString {
                    if (finished.taskOutputCapturingFailed) {
                        appendLine("Task output capturing failed. Task output may be incomplete or interleaved with other tasks.\n")
                    }
                    val taskOut = finished.getTaskOutput(it.captureTaskOutput, true)
                    if (taskOut != null) {
                        val lines = taskOut.lines()
                        if (lines.size > TASK_OUTPUT_MAX_LINES) {
                            appendLine("... (Task output truncated to last $TASK_OUTPUT_MAX_LINES lines. Use `${ToolNames.QUERY_BUILD}(buildId=\"${finished.id}\", kind=\"TASKS\", query=\"${it.captureTaskOutput}\")` for the full task output or use `${ToolNames.QUERY_BUILD}(buildId=\"${finished.id}\", kind=\"CONSOLE\")` for the full console output)")
                            appendLine()
                            append(lines.takeLast(TASK_OUTPUT_MAX_LINES).joinToString("\n"))
                        } else {
                            append(taskOut)
                        }
                    } else {
                        val executedTasks = finished.taskResults.keys + finished.taskOutputs.keys
                        val status = if (finished.activeOperations.contains(it.captureTaskOutput)) "currently running"
                        else if (executedTasks.contains(it.captureTaskOutput)) "executed but output not captured"
                        else "not found in executed tasks"

                        appendLine("Task output for ${it.captureTaskOutput} $status.")
                        val captureTaskOutput = it.captureTaskOutput!!
                        if (status == "currently running" || (status == "not found in executed tasks" && (commandLine.contains(captureTaskOutput) || commandLine.any { it.endsWith(captureTaskOutput) })))
                        {
                            appendLine("If this is a long-running task like `run`, it will not appear in the finished task list as it never completes.")
                        }
                        appendLine()
                        appendLine("Build result summary:")
                        append(finished.toOutputString())
                    }
                }, isError)
            }

            return@tool ToolCallResult(finished.toOutputString(), isError)
        }
    }
    }
}
