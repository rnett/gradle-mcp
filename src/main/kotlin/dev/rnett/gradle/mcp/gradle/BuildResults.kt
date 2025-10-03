package dev.rnett.gradle.mcp.gradle

import com.github.benmanes.caffeine.cache.Caffeine
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

object BuildResults {
    private val store = Caffeine.newBuilder()
        .expireAfterAccess(10.minutes.toJavaDuration())
        .maximumSize(1000)
        .build<BuildId, BuildResult>()


    fun storeResult(result: BuildResult) {
        store.put(result.id, result)
    }

    fun getResult(buildId: BuildId): BuildResult? {
        return store.getIfPresent(buildId)
    }

    fun require(buildId: BuildId): BuildResult = getResult(buildId) ?: throw IllegalArgumentException("Unknown or expired build ID: $buildId")

    operator fun get(buildId: BuildId): BuildResult? = getResult(buildId)
}