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
