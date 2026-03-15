package dev.rnett.gradle.mcp

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
