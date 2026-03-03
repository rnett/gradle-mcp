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
    val gradle: GradleProvider,
) : McpServerComponent("Execution Tools", "Tools for executing Gradle tasks and running tests.") {
    @Serializable
    data class GradleExecuteArgs(
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The arguments for gradlew (e.g., [\":app:test\", \"--tests\", \"MyTest\"]). Required if not stopping a build.")
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
    val gradlew by tool<GradleExecuteArgs, String>(
        ToolNames.GRADLEW,
        """
            |The primary tool for managing the Gradle build lifecycle. It can start builds (foreground/background) and stop active background builds.
            |
            |Use this tool for:
            |- Running any Gradle task (e.g., `build`, `test`, `clean`).
            |- Starting long-running background processes like development servers.
            |- Stopping active background builds using their `BuildId`.
            |- Getting clean output from a specific task using `captureTaskOutput`.
            |
            |For detailed workflows on background monitoring and failure analysis, refer to the `gradle-build` skill.
        """.trimMargin(),
    ) {
        if (it.stopBuildId != null) {
            val build = gradle.buildManager.getBuild(it.stopBuildId) as? RunningBuild
                ?: throw IllegalArgumentException("Build ${it.stopBuildId} is not running or not found")
            build.stop()
            return@tool "Build ${it.stopBuildId} stoped"
        }

        val commandLine = it.commandLine
            ?: throw IllegalArgumentException("commandLine is required when not stopping a build.")

        val invocationArgs = it.invocationArguments.copy(additionalArguments = commandLine)

        if (it.background) {
            val root = it.projectRoot.resolve()
            val running = gradle.runBuild(
                root,
                invocationArgs.withInitScript(InitScriptNames.TASK_OUT),
                { ScansTosManager.askForScansTos(root, it) },
                stdoutLineHandler = {
                    emitProgressNotification(0.0, 0.0, it)
                }
            )
            return@tool running.id.toString()
        } else {
            val result = gradle.doBuild(
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
                    appendLine(
                        finished.getTaskOutput(it.captureTaskOutput, true)
                            ?: "Task output for ${it.captureTaskOutput} not found in console output. Build result:\n${finished.toOutputString()}"
                    )
                }
            }

            return@tool finished.toOutputString()
        }
    }
}
