package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import kotlinx.coroutines.FlowPreview

@OptIn(FlowPreview::class)
context(progressReporter: ProgressReporter)
suspend inline fun GradleProvider.doBuild(
    projectRoot: GradleProjectRootInput,
    invocationArgs: GradleInvocationArguments
): FinishedBuild {
    progressReporter.report(0.0, 1.0, "Starting Gradle build...")
    val root = projectRoot.resolve()
    val running = runBuild(
        root,
        invocationArgs.withInitScript(InitScriptNames.TASK_OUT),
        stdoutLineHandler = { /* captured via RunningBuild.consoleOutput */ },
        stderrLineHandler = { /* captured via RunningBuild.consoleOutput */ },
        progress = progressReporter
    )
    return running.awaitFinished()
}
