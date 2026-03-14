package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.GradleResult
import dev.rnett.gradle.mcp.mcp.McpContext
import kotlinx.coroutines.FlowPreview
import org.slf4j.LoggerFactory

@PublishedApi
internal val LOGGER = LoggerFactory.getLogger("dev.rnett.gradle.mcp.tools.McpGradleHelpers")

@OptIn(FlowPreview::class)
context(ctx: McpContext)
suspend inline fun GradleProvider.doBuild(
    projectRoot: GradleProjectRootInput,
    invocationArgs: GradleInvocationArguments
): GradleResult<Unit> {
    ctx.progressReporter.report(0.0, 1.0, "Starting Gradle build...")
    val root = projectRoot.resolve()
    val running = runBuild(
        root,
        invocationArgs.withInitScript(InitScriptNames.TASK_OUT),
        stdoutLineHandler = { /* captured via RunningBuild.consoleOutput */ },
        stderrLineHandler = { /* captured via RunningBuild.consoleOutput */ },
        progress = ctx.progressReporter
    )
    val finished = running.awaitFinished()
    return GradleResult(finished, Result.success(Unit))
}
