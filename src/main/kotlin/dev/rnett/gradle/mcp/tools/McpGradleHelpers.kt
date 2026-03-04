package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.GradleResult
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.mcp.McpContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class ProgressState(val progress: Double, val total: Double?, val message: String?)

context(ctx: McpContext)
@FlowPreview
suspend inline fun <T> withProgressEmissions(
    crossinline block: suspend (progress: (Double, Double?, String?) -> Unit) -> T
): T = coroutineScope {
    val channel = Channel<ProgressState>(4096)
    val job = launch {
        channel.consumeAsFlow().collect {
            ctx.emitProgressNotification(it.progress, it.total, it.message)
        }
    }
    try {
        block { progress, total, message ->
            channel.trySend(ProgressState(progress, total, message))
        }
    } finally {
        job.cancel()
    }
}

@OptIn(FlowPreview::class)
context(ctx: McpContext)
suspend inline fun GradleProvider.doBuild(
    projectRoot: GradleProjectRootInput,
    invocationArgs: GradleInvocationArguments,
): GradleResult<Unit> = withProgressEmissions { progress ->
    val root = projectRoot.resolve()
    lateinit var running: RunningBuild
    running = runBuild(
        root,
        invocationArgs.withInitScript(InitScriptNames.TASK_OUT),
        { ScansTosManager.askForScansTos(root, it) },
        stdoutLineHandler = { /* captured via RunningBuild.consoleOutput */ },
        stderrLineHandler = { /* captured via RunningBuild.consoleOutput */ },
        progressHandler = { p, total, msg ->
            val phasePrefix = when (running.currentPhase) {
                "CONFIGURE_ROOT_BUILD", "CONFIGURE_BUILD" -> "[CONFIGURING] "
                "RUN_MAIN_TASKS", "RUN_WORK" -> "[EXECUTING] "
                else -> ""
            }
            progress(p, total, phasePrefix + (msg ?: ""))
        }
    )
    val finished = running.awaitFinished()
    GradleResult(finished, Result.success(Unit))
}

