package dev.rnett.gradle.mcp.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object Concurrency {
    val DEFAULT_IO_CONCURRENCY = Runtime.getRuntime().availableProcessors()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend inline fun <T> Iterable<T>.unorderedParallelForEach(
    concurrency: Int = Concurrency.DEFAULT_IO_CONCURRENCY,
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend (T) -> Unit
) {
    asFlow().unorderedParallelForEach(concurrency, context, block)
}

/**
 * Maps an [Iterable] in parallel. Note that the order of the resulting list is **not** preserved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend inline fun <T, R> Iterable<T>.unorderedParallelMap(
    concurrency: Int = Concurrency.DEFAULT_IO_CONCURRENCY,
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend (T) -> R
): List<R> {
    return asFlow().flatMapMerge(concurrency) { item ->
        flow {
            emit(block(item))
        }.flowOn(context)
    }.toList()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend inline fun <T> Flow<T>.unorderedParallelForEach(
    concurrency: Int = Concurrency.DEFAULT_IO_CONCURRENCY,
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend (T) -> Unit
) {
    flatMapMerge(concurrency) { item ->
        flow {
            block(item)
            emit(Unit)
        }.flowOn(context)
    }.collect()
}
