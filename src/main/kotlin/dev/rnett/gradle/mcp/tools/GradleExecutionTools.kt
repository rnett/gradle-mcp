package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds
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

    @OptIn(ExperimentalTime::class)
    val gradle by tool<GradleExecuteArgs, String>(
        ToolNames.GRADLE,
        """
            |Executes Gradle builds and tasks with background orchestration, task output capturing, and progressive feedback; ALWAYS use instead of raw shell `./gradlew`.
            |
            |### Task Execution
            |- **Foreground** (default): STRONGLY PREFERRED; provides progressive output.
            |- **Background** (`background=true`): Use only for persistent tasks (servers) or parallel work.
            |- **Task Output Capturing** (`captureTaskOutput=":path:to:task"`): Returns clean task-specific output.
            |   - **DO NOT use Task Output Capturing for tests**: Use `${ToolNames.QUERY_BUILD}` with `kind="TESTS"` and `query="FullTestName"`.
            |
            |After starting a build, use `${ToolNames.QUERY_BUILD}` or `${ToolNames.WAIT_BUILD}` with the returned `BuildId` to monitor progress or diagnose failures.
            |Note: Prefer `--rerun` (single task) over `--rerun-tasks` (all tasks, even included builds). Use `invocationArguments: { envSource: "SHELL" }` if env vars (e.g., JDKs) aren't found.
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

        val invocationArgs = it.invocationArguments.copy(additionalArguments = commandLine + it.invocationArguments.additionalArguments)

        if (it.background) {
            progressReporter.report(0.0, 1.0, "Starting background Gradle build...")
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

            val finished = if (it.captureTaskOutput != null) {
                withTimeoutOrNull(10.seconds) {
                    result.build.awaitFinished()
                } ?: run {
                    progressReporter.report(
                        0.0,
                        1.0,
                        "Build is taking a while to complete. Note that `captureTaskOutput` will only return results AFTER the build finishes. For long-running tasks like `run`, consider using `background=true` instead."
                    )
                    result.build.awaitFinished()
                }
            } else {
                result.build.awaitFinished()
            }

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
                        if (status == "currently running" || (status == "not found in executed tasks" && (commandLine.contains(captureTaskOutput) || commandLine.any { it.endsWith(captureTaskOutput) }))) {
                            appendLine("If this is a long-running task like `run`, it will not appear in the finished task list as it never completes.")
                        }
                        appendLine()
                        appendLine("Build result summary:")
                        append(finished.toOutputString())
                    }
                }
            }

            return@tool finished.toOutputString()
        }
    }
}
