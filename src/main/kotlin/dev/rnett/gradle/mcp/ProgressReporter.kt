package dev.rnett.gradle.mcp

import kotlin.concurrent.atomics.AtomicReference

fun interface ProgressReporter {
    fun report(progress: Double, total: Double?, message: String?)

    companion object {
        val NONE = ProgressReporter { _, _, _ -> }
    }
}

fun ProgressReporter.withPhase(phase: String): ProgressReporter = ProgressReporter { progress, total, message ->
    val prefixedMessage = if (message != null) "[$phase] $message" else "[$phase]"
    this.report(progress, total, prefixedMessage)
}

fun ProgressReporter.withMessage(messageMapper: (String?) -> String?): ProgressReporter = ProgressReporter { progress, total, message ->
    this.report(progress, total, messageMapper(message))
}

fun ProgressReporter.subReporter(startFraction: Double, endFraction: Double, total: Double): ProgressReporter = ProgressReporter { progress, subTotal, message ->
    val subProgressFraction = if (subTotal != null && subTotal > 0.0) progress / subTotal else 0.0
    val overallProgress = startFraction + (subProgressFraction * (endFraction - startFraction))
    this.report(overallProgress, total, message)
}

private data class ProgressState(val progress: Double, val total: Double?, val message: String?)

fun ProgressReporter.monotonicallyIncreasing(onlyUpdateOnIncrease: Boolean = false): ProgressReporter {
    val state = AtomicReference(ProgressState(-1.0, null, "")) // use non-null empty string to allow first null message

    return ProgressReporter { progress, total, message ->
        var next: ProgressState? = null
        while (true) {
            val current = state.load()
            val nextProgress = maxOf(progress, current.progress)
            if (nextProgress == current.progress && total == current.total && message == current.message) {
                return@ProgressReporter
            }

            if (onlyUpdateOnIncrease && progress < current.progress) {
                return@ProgressReporter
            }

            val nextState = ProgressState(nextProgress, total, message)
            if (state.compareAndSet(current, nextState)) {
                next = nextState
                break
            }
        }

        this.report(next.progress, next.total, next.message)
    }
}
