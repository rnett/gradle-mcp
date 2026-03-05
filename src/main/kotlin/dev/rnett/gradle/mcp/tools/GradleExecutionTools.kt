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
        @Description("The absolute path to the project root directory. Defaults to the current workspace root. Always provide this if you are working in a multi-root workspace to ensure the correct project is targeted.")
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The arguments for gradle (e.g., [\":app:test\", \"--tests\", \"MyTest\"]). This is the primary way to specify tasks and flags. Required if not stopping a build.")
        val commandLine: List<String>? = null,
        @Description("If true, starts the build in the background and returns a BuildId immediately. STRONGLY RECOMMENDED for long-running tasks like 'build', 'test', or 'bootRun' to maintain agent responsiveness.")
        val background: Boolean = false,
        @Description("The BuildId of an active background build to stop. If provided, all other arguments are ignored and the specified build is terminated.")
        val stopBuildId: BuildId? = null,
        @Description("The path of a specific task (e.g., ':app:dependencies') to capture and return output for exclusively. This is highly token-efficient as it filters out all non-task console noise. Output over $TASK_OUTPUT_MAX_LINES lines will be truncated; use 'inspect_build' for full logs.")
        val captureTaskOutput: String? = null,
        @Description("Additional advanced invocation arguments for the Gradle process.")
        val invocationArguments: GradleInvocationArguments = GradleInvocationArguments.DEFAULT
    )

    @OptIn(ExperimentalTime::class)
    val gradle by tool<GradleExecuteArgs, String>(
        ToolNames.GRADLE,
        """
            |The authoritative tool for managing the Gradle build lifecycle.
            |It is the STRONGLY PREFERRED way to execute any Gradle task, providing a managed environment with features that raw shell execution cannot match.
            |Unless the Tooling API is demonstrably insufficient for a specific edge case, ALWAYS prefer this tool over direct shell commands.
            |
            |### High-Performance Features
            |- **Managed Background Lifecycle**: Execute long-running builds, tests, or servers in the background. Receive a `BuildId` instantly, allowing you to monitor progress or perform other tasks while the build proceeds.
            |- **Precision Task Output Capturing**: Use `captureTaskOutput` to extract clean, isolated output for a single task. This is the most token-efficient way to read task results like `dependencies`, `help`, or `properties` as it eliminates all background console noise.
            |- **Surgical Build Control**: Seamlessly stop background processes using `stopBuildId` and transition to the `inspect_build` tool for deep post-mortem failure analysis.
            |- **Maximum Token Efficiency**: Provides concise summaries for foreground builds and rich, searchable metadata for background ones.
            |
            |### Common Usage Patterns
            |- **Standard Build**: `gradle(commandLine=["clean", "build"])`
            |- **Background Server**: `gradle(commandLine=[":app:bootRun"], background=true)`
            |- **Clean Dependency Report**: `gradle(commandLine=[":app:dependencies"], captureTaskOutput=":app:dependencies")`
            |
            |### Task Path Syntax Reference
            |- `:task` (leading colon): Targets the task in the **root project only**.
            |- `task` (no leading colon): Targets the task in **all projects** (root and all subprojects).
            |- `:app:task`: Targets the task in the `app` subproject.
            |
            |**Safety Note**: Avoid using `--rerun-tasks` unless absolutely necessary, as it bypasses all Gradle caching and significantly increases build time.
            |
            |### Post-Build Diagnostics
            |After a build finishes (or while a background build is running), use the `inspect_build` tool with the `BuildId` to:
            |- Retrieve detailed test failure trees and stack traces.
            |- Access specific test stdout/stderr (often missing from the general console).
            |- Tail build logs or wait for specific log patterns.
            |
            |For expert-level workflows, refer to the `gradle-build` and `gradle-test` skills.
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
