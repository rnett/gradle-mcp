package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class ParallelProgressTracker(
    val phase: ProgressReporter,
    val total: Double,
    val initialMessage: String = "Processing dependencies"
) {
    private val completedCounter = AtomicInt(0)
    private val activeTasks = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val lastActivity = AtomicReference<String?>(null)

    fun onStart(depId: String) {
        activeTasks.add(depId)
        report()
    }

    fun onComplete(depId: String) {
        completedCounter.addAndFetch(1)
        activeTasks.remove(depId)
        report()
    }

    fun reportMessage(m: String) {
        lastActivity.store(m)
        report()
    }

    private fun report() {
        val completed = completedCounter.load()
        val currentMessage = lastActivity.load() ?: initialMessage
        val inProgressCount = activeTasks.size
        val message = if (inProgressCount > 0) "$currentMessage ($inProgressCount in progress)" else currentMessage
        phase.report(completed.toDouble(), total, message)
    }
}
