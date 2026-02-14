package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.build.Build
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.gradle.build.freeze
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalAtomicApi::class)
class BuildManager : AutoCloseable {
    private val builds = ConcurrentHashMap<BuildId, Build>()
    private val lastAccess = ConcurrentHashMap<BuildId, Instant>()
    private val latestFinished = AtomicReference<FinishedBuild?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            while (isActive) {
                delay(1.minutes)
                cleanUp()
            }
        }
    }

    private fun cleanUp() {
        val now = kotlin.time.Clock.System.now()
        val expired = lastAccess.filter { (id, access) ->
            val build = builds[id]
            (build == null || build.hasBuildFinished) && (now - access) > 30.minutes
        }.keys

        expired.forEach { stopAndRemove(it) }

        val stoppedBuilds = lastAccess.keys
            .mapNotNull { id -> builds[id]?.let { id to it } }
            .filter { it.second.hasBuildFinished }
            .sortedByDescending { lastAccess[it.first]!! }

        if (stoppedBuilds.size > 1000) {
            stoppedBuilds.drop(1000).forEach { stopAndRemove(it.first) }
        }
    }

    private fun updateAccess(id: BuildId) {
        lastAccess[id] = kotlin.time.Clock.System.now()
    }

    fun registerBuild(build: RunningBuild) {
        builds[build.id] = build
        updateAccess(build.id)
    }

    fun storeResult(result: FinishedBuild) {
        val frozen = result.freeze()
        builds[frozen.id] = frozen
        latestFinished.store(frozen)
        updateAccess(frozen.id)
    }

    fun getBuild(id: BuildId): Build? {
        val build = builds[id]
        if (build != null) {
            updateAccess(id)
        }
        return build
    }

    fun listRunningBuilds(): List<RunningBuild> {
        return builds.values.filterIsInstance<RunningBuild>().filter { it.isRunning }
    }

    fun stopAndRemove(id: BuildId) {
        val build = builds.remove(id)
        lastAccess.remove(id)
        if (build is RunningBuild && build.isRunning) {
            build.stop()
        }
    }

    fun require(buildId: BuildId?): Build {
        if (buildId == null)
            return latestFinished.load()
                ?: error("No latest result - this MCP server has not ran any builds that completed in the last 30m or that have been evicted")
        return getBuild(buildId) ?: throw IllegalArgumentException("Unknown or expired build ID: $buildId")
    }

    fun latestFinished(limit: Int = 1): List<FinishedBuild> {
        if (limit == 1) {
            return listOfNotNull(latestFinished.load())
        }

        if (limit < 1) return emptyList()

        return builds.values.filterIsInstance<FinishedBuild>()
            .sortedByDescending { it.id.timestamp }
            .take(limit)
    }

    override fun close() {
        builds.values.filterIsInstance<RunningBuild>().forEach { it.stop() }
        scope.cancel()
    }
}
