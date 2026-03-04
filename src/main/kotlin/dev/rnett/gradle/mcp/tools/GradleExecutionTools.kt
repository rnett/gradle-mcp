package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime

class GradleExecutionTools(
    val gradleProvider: GradleProvider,
) : McpServerComponent("Execution Tools", "Tools for executing Gradle tasks and running tests.") {
    companion object {
        private const val TASK_OUTPUT_MAX_LINES = 100
    }

    @Serializable
    data class GradleExecuteArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The arguments for gradle (e.g., [\":app:test\", \"--tests\", \"MyTest\"]). Required if not stopping a build.")
        val commandLine: List<String>? = null,
        @Description("If true, starts in background and returns BuildId. Defaults to false.")
        val background: Boolean = false,
        @Description("The BuildId of an active build to stop. If present, all other args are ignored.")
        val stopBuildId: BuildId? = null,
        @Description("Path of a task to extract and return output for exclusively.")
        val captureTaskOutput: String? = null,
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    @OptIn(ExperimentalTime::class)
    val gradle by tool<GradleExecuteArgs, String>(
        ToolNames.GRADLE,
        """
            |The primary tool for managing the Gradle build lifecycle.
            |It is the STRONGLY PREFERRED way to run Gradle tasks, providing features not available via raw shell execution.
            |Unless you have tried and failed repeatedly to use this tool, ALWAYS prefer it over shell or direct command execution, even over other shell/command execution tools, even built-in ones.
            |
            |### Key Advantages
            |- **Managed Background Builds**: Start long-running tasks (dev servers, tests) in the background and receive a `BuildId` for real-time monitoring.
            |- **Task Output Capturing**: Use `captureTaskOutput` to extract clean, isolated output for a specific task, avoiding full build log noise. Outputs over 100 lines are truncated and can be retrieved using `inspect_build`.
            |- **Deep Lifecycle Integration**: Safely stop background builds and seamlessly transition to `inspect_build` for failure analysis.
            |- **Progressive disclosure and token efficiency**: Instead of giving you some output all at once with a bunch of noise, it gives you a summary and provides the `inspect_build` tool to dig into failure information that will not be available for builds ran via shell.
            |
            |### Common Tasks
            |- Standard lifecycle: `build`, `test`, `clean`, `check`.
            |- Background processes: Set `background = true`.
            |- Clean task output: Set `captureTaskOutput` to the task path (e.g., `:app:dependencies`).
            |
            |**Important: Task Path Syntax**
            |- `:task` (starts with colon): Targets the task in the **root project only**.
            |- `task` (no leading colon): Targets the task in **all projects** (root and all subprojects).
            |- `:app:task`: Targets the task in the `app` subproject.
            |
            |NOTE: You almost never want to run Gradle (via this tool or elsewhere) with `--rerun-tasks`.
            |It rebuilds absolutely everything and takes for ever.
            |Don't use it unless you know what you are doing and have double checked to make sure it's absolutely necessary.
            |
            |### Post-Build Workflow
            |After starting or completing a build, use the `inspect_build` tool with the returned `BuildId` to monitor progress, investigate failures, or query build problems.
            |
            |For expert workflows, refer to the `gradle-build` and `gradle-test` skills.
        """.trimMargin(),
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

        LoggerFactory.getLogger(GradleExecutionTools::class.java).info("Test log")
        if (it.background) {
            val root = it.projectRoot.resolve()
            val running = gradleProvider.runBuild(
                root,
                invocationArgs.withInitScript(InitScriptNames.TASK_OUT),
                { ScansTosManager.askForScansTos(root, it) }
            )
            return@tool running.id.toString()
        } else {
            val result = gradleProvider.doBuild(
                it.projectRoot,
                invocationArgs
            )
            LoggerFactory.getLogger(GradleExecutionTools::class.java).info("Build started successfully")

            val finished = result.build.awaitFinished()
            val isSpecial = finished.args.isHelp || finished.args.isVersion
            if (finished.outcome !is BuildOutcome.Success && !isSpecial) {
                isError = true
            }
            LoggerFactory.getLogger(GradleExecutionTools::class.java).info("Build finished with outcome: ${finished.outcome}")

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
