package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update

@OptIn(ExperimentalAtomicApi::class)
class ParallelProgressTracker(
    val phase: ProgressReporter,
    val total: Double,
    val initialMessage: String = "Processing dependencies"
) {
    private val completedCounter = AtomicInt(0)
    private val activeTasks = AtomicReference(setOf<String>())
    private val focusDependency = AtomicReference<String?>(null)
    private val messages = AtomicReference(mapOf<String, String>())
    private val progressPercent = AtomicReference(mapOf<String, Double>())

    /**
     * Updates the currently focused dependency using a lock-free compare-and-set loop.
     * Selects the next focus predictably by sorting active tasks.
     */
    private fun ensureFocus() {
        val currentFocus = focusDependency.load()
        val currentTasks = activeTasks.load()
        if (currentFocus != null && currentTasks.contains(currentFocus)) {
            return
        }

        val nextFocus = currentTasks.sorted().firstOrNull()
        focusDependency.compareAndSet(currentFocus, nextFocus)
    }

    fun onStart(depId: String) {
        activeTasks.update { it + depId }
        ensureFocus()
        report()
    }

    fun onComplete(depId: String, count: Int = 1) {
        val wasActive = depId in activeTasks.load()
        activeTasks.update { it - depId }
        if (wasActive) {
            completedCounter.addAndFetch(count)
        }
        ensureFocus()
        messages.update { it - depId }
        progressPercent.update { it - depId }
        report()
    }

    fun reportMessage(depId: String, m: String) {
        reportProgress(depId, 0.0, null, m)
    }

    fun reportProgress(depId: String, p: Double, t: Double?, m: String?) {
        if (m != null) {
            messages.update { it + (depId to m) }
        }
        if (t != null && t > 0) {
            val fraction = (p / t).coerceIn(0.0, 1.0)
            if (fraction >= 1.0) {
                onComplete(depId, count = 1)
                return
            }
            progressPercent.update { it + (depId to fraction) }
        }

        if (focusDependency.load() == depId) {
            report()
        }
    }

    private fun report() {
        val completed = completedCounter.load()
        val currentFocus = focusDependency.load()
        val currentMessage = if (currentFocus != null) {
            messages.load()[currentFocus] ?: "Processing $currentFocus"
        } else {
            initialMessage
        }

        val currentTasks = activeTasks.load()
        val inProgressCount = currentTasks.size
        val inProgressContribution = progressPercent.load().values.sum()

        val totalFormatted = if (total > 0 && total.isFinite()) " ($completed/${total.toInt()})" else ""
        val inProgressFormatted = if (inProgressCount > 0) " ($inProgressCount in progress)" else ""

        val message = "$currentMessage$totalFormatted$inProgressFormatted"

        // Use a weighted average for the global progress bar: 
        // (completed + sum of in-progress percentages) / total
        val globalProgress = (completed + inProgressContribution).coerceAtMost(total)
        phase.report(globalProgress, total, message)
    }
}
