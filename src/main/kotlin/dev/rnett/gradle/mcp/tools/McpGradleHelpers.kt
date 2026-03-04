package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.GradleResult
import dev.rnett.gradle.mcp.mcp.McpContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

context(ctx: McpContext)
suspend inline fun <T> withProgressEmissions(
    crossinline block: suspend ((String) -> Unit) -> T
): T = coroutineScope {
    val channel = Channel<String>(Channel.UNLIMITED)
    val job = launch {
        channel.consumeAsFlow().sample(500.milliseconds).collect {
            ctx.emitProgressNotification(0.0, 0.0, it)
        }
    }
    try {
        block { line ->
            channel.trySend(line)
        }
    } finally {
        job.cancel()
    }
}

context(ctx: McpContext)
suspend inline fun GradleProvider.doBuild(
    projectRoot: GradleProjectRootInput,
    invocationArgs: GradleInvocationArguments,
): GradleResult<Unit> = withProgressEmissions { emit ->
    val root = projectRoot.resolve()
    val running = runBuild(
        root,
        invocationArgs.withInitScript(InitScriptNames.TASK_OUT),
        { ScansTosManager.askForScansTos(root, it) },
        stdoutLineHandler = emit,
        stderrLineHandler = { emit("ERR: ${it}") },
    )
    val finished = running.awaitFinished()
    GradleResult(finished, Result.success(Unit))
}

