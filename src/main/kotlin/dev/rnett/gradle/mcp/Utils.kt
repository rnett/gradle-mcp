package dev.rnett.gradle.mcp

import kotlin.coroutines.cancellation.CancellationException


inline fun <R> runCatchingExceptCancellation(block: () -> R): Result<R> = runCatching {
    block()
}.apply {
    if (exceptionOrNull() is CancellationException)
        throw exceptionOrNull()!!
}