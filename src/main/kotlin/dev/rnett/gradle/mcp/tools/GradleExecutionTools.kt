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
        @Description("Gradle CLI args. ':task' (root), 'task' (all projects), ':app:task'. Required if not stopping.")
        val commandLine: List<String>? = null,
        @Description("Start build in background; returns BuildId. Use only for servers or parallel work.")
        val background: Boolean = false,
        @Description("Stop an active background build by BuildId; all other args are ignored.")
        val stopBuildId: BuildId? = null,
        @Description("Isolated output for a specific task path. DO NOT use for tests; use inspect_build with testName.")
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
            |   - **DO NOT use Task Output Capturing for tests**: Use `${ToolNames.INSPECT_BUILD}` with `testName` and `mode="details"`.
            |
            |After starting a build, use `${ToolNames.INSPECT_BUILD}` with the returned `BuildId` to monitor progress or diagnose failures.
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
