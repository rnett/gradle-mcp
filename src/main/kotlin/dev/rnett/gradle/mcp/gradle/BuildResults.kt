package dev.rnett.gradle.mcp.gradle

import com.github.benmanes.caffeine.cache.Caffeine
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

object BuildResults {
    @OptIn(ExperimentalAtomicApi::class)
    private val latest = AtomicReference<BuildResult?>(null)
    private val store = Caffeine.newBuilder()
        .expireAfterAccess(10.minutes.toJavaDuration())
        .maximumSize(1000)
        .build<BuildId, BuildResult>()


    @OptIn(ExperimentalAtomicApi::class)
    fun storeResult(result: BuildResult) {
        latest.store(result)
        store.put(result.id, result)
    }

    fun getResult(buildId: BuildId): BuildResult? {
        return store.getIfPresent(buildId)
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun require(buildId: BuildId?): BuildResult {
        if(buildId == null)
            return latest.load() ?: error("No latest result - this MCP server has not ran any builds in the last 10m")
        return getResult(buildId) ?: throw IllegalArgumentException("Unknown or expired build ID: $buildId")
    }

    @OptIn(ExperimentalTime::class, ExperimentalAtomicApi::class)
    fun latest(limit: Int = 1): List<BuildResult> {
        val latest = latest.load()
        if (limit == 1) {
            return latest?.let { listOf(it) } ?: emptyList()
        }

        if (latest == null || limit < 1) return emptyList()

        return store.asMap().values.toList().sortedByDescending { it.id.timestamp }.take(limit)
    }

    operator fun get(buildId: BuildId): BuildResult? = getResult(buildId)
}