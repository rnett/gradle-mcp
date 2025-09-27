package dev.rnett.gradle.mcp

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.mono
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import kotlin.coroutines.cancellation.CancellationException

suspend fun Publisher<Void>.join() = awaitFirstOrNull()

private val logger = LoggerFactory.getLogger("dev.rnett.gradle.mcp.McpTools")

fun <R> toolCall(block: suspend () -> R): Mono<R> = mono {
    try {
        block()
    } catch (e: Throwable) {
        logger.error("Error while executing MCP tool", e)
        throw e
    }
}

inline fun <R> runCatchingExceptCancellation(block: () -> R): Result<R> = runCatching {
    block()
}.apply {
    if (exceptionOrNull() is CancellationException)
        throw exceptionOrNull()!!
}