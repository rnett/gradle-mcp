package dev.rnett.gradle.mcp.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object Concurrency {
    val DEFAULT_IO_CONCURRENCY = Runtime.getRuntime().availableProcessors()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend inline fun <T> Iterable<T>.parallelForEach(
    concurrency: Int = Concurrency.DEFAULT_IO_CONCURRENCY,
    semaphore: Semaphore? = null,
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend (T) -> Unit
) {
    asFlow().parallelForEach(concurrency, semaphore, context, block)
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend inline fun <T> Flow<T>.parallelForEach(
    concurrency: Int = Concurrency.DEFAULT_IO_CONCURRENCY,
    semaphore: Semaphore? = null,
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend (T) -> Unit
) {
    flatMapMerge(concurrency) { item ->
        flow {
            if (semaphore != null) {
                semaphore.withPermit {
                    block(item)
                }
            } else {
                block(item)
            }
            emit(Unit)
        }.flowOn(context)
    }.collect()
}
