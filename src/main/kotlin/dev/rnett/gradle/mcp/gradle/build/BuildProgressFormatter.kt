package dev.rnett.gradle.mcp.gradle.build

/**
 * Handles formatting of build progress messages.
 */
class BuildProgressFormatter(
    private val tracker: BuildProgressTracker,
    private val infoProvider: BuildProgressInfoProvider
) {

    /**
     * Constructs a human-readable progress message based on the current state of the [tracker].
     */
    fun formatMessage(): String {
        val active = tracker.activeOperations
        val phase = tracker.currentPhase
        val phasePrefix = when {
            phase?.contains("CONFIGUR", ignoreCase = true) == true -> "[CONFIGURING] "
            phase?.contains("EXECUT", ignoreCase = true) == true || phase?.contains("RUN", ignoreCase = true) == true -> "[EXECUTING] "
            else -> ""
        }

        val subTask = tracker.subTaskProgress
        val subTaskMsg = if (subTask.message != null && subTask.total > 0) {
            "${subTask.message} (${subTask.completed}/${subTask.total})"
        } else null

        val baseMessage = when {
            subTaskMsg != null -> subTaskMsg
            active.isNotEmpty() -> getActiveMessage(active)
            tracker.lastFinishedOperation != null -> "Finished ${tracker.lastFinishedOperation}"
            else -> phase ?: "Running build"
        }

        val statusSuffix = buildString {
            val op = tracker.activeStatusOperation
            val subStatus = op?.let { tracker.getSubStatus(it) }
            if (subStatus != null && subStatus.status != null) {
                append(subStatus.status)
                val progress = subStatus.progress
                if (progress != null) {
                    val percent = (progress * 100).toInt()
                    append(" - $percent%")
                }
            }
        }.takeIf { it.isNotEmpty() }

        val fullMessage = if (statusSuffix != null) {
            "$baseMessage ($statusSuffix)"
        } else {
            baseMessage
        }

        return phasePrefix + fullMessage + getTestSummary()
    }

    private fun getActiveMessage(active: List<String>): String {
        val leadCandidate = tracker.activeStatusOperation?.takeIf { it in active } ?: active.sorted().firstOrNull()
        val lead = leadCandidate ?: "Running"
        return if (active.size > 1) {
            val otherCount = active.size - 1
            val suffix = if (otherCount == 1) "task" else "tasks"
            "$lead (+$otherCount other $suffix)"
        } else {
            lead
        }
    }

    private fun getTestSummary(): String {
        val totalTests = infoProvider.totalTests
        if (totalTests == 0) return ""

        val passed = infoProvider.passedTests
        val failed = infoProvider.failedTests
        val skipped = infoProvider.skippedTests

        val parts = mutableListOf<String>()
        if (passed > 0) parts += "$passed passed"
        if (failed > 0) parts += "$failed failed"
        if (skipped > 0) parts += "$skipped skipped"

        return if (parts.isNotEmpty()) " (${parts.joinToString(", ")})" else ""
    }
}
