package dev.rnett.gradle.mcp.gradle.build

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Capping progress at 99% to avoid jumping to 100% while the build is still running.
 */
internal const val MAX_ITEM_PROGRESS = 0.99

/**
 * Represents the progress of a running build.
 */
data class BuildProgress(val progress: Double, val message: String)

/**
 * Interface providing information needed for progress tracking and formatting.
 */
interface BuildProgressInfoProvider {
    val passedTests: Int
    val failedTests: Int
    val skippedTests: Int
    val totalTests: Int
}

/**
 * Tracks and calculates progress for a running build.
 */
class BuildProgressTracker(private val infoProvider: BuildProgressInfoProvider) {
    private val lock = Any()

    private data class PhaseState(var name: String, var totalItems: Long, var completedItems: Long = 0)

    private val phaseStack = ConcurrentLinkedDeque<PhaseState>()

    private val _progress = MutableSharedFlow<BuildProgress>(
        replay = 1,
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val progress: SharedFlow<BuildProgress> = _progress.asSharedFlow()

    private val formatter = BuildProgressFormatter(this, infoProvider)

    /**
     * The total number of items in the current top-most phase.
     */
    val totalItems: Long get() = synchronized(lock) { phaseStack.peekLast()?.totalItems ?: 0L }

    /**
     * The number of completed items in the current top-most phase.
     */
    val completedItems: Long get() = synchronized(lock) { phaseStack.peekLast()?.completedItems ?: 0L }

    /**
     * The name of the current active phase.
     */
    val currentPhase: String? get() = synchronized(lock) { phaseStack.peekLast()?.name }

    private val _activeOperations: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    val activeOperations: List<String> get() = _activeOperations.toList()

    @Volatile
    var lastFinishedOperation: String? = null
        private set

    internal data class SubStatus(val status: String?, val progress: Double?)

    private val _subStatuses = ConcurrentHashMap<String, SubStatus>()

    @Volatile
    internal var activeStatusOperation: String? = null

    internal data class SubTaskProgress(val total: Long, val completed: Long, val message: String?)

    @Volatile
    internal var subTaskProgress = SubTaskProgress(0, 0, null)

    companion object {
        private val SUB_TASK_TOTAL_REGEX = Regex("TOTAL: ([0-9]+)")
        private val SUB_TASK_PROGRESS_REGEX = Regex("([0-9]+)/([0-9]+): (.*)")
    }

    internal fun emitProgress(isFinish: Boolean = false) {
        val value = getProgressValue(isFinish)
        val message = formatter.formatMessage()
        _progress.tryEmit(BuildProgress(value, message))
    }

    private fun isSameLogicalPhase(old: String?, new: String): Boolean {
        if (old == null) return false
        if (old == new) return true

        val isConfig = { s: String -> s == "CONFIGURE_ROOT_BUILD" || s == "CONFIGURE_BUILD" || s.contains("CONFIGUR", ignoreCase = true) }
        val isRun = { s: String -> s == "RUN_MAIN_TASKS" || s == "RUN_WORK" || s.contains("EXECUT", ignoreCase = true) || s.contains("RUN", ignoreCase = true) }

        if (isConfig(old) && isConfig(new)) return true
        if (isRun(old) && isRun(new)) return true

        return false
    }

    internal fun onPhaseStart(phase: String, total: Int) = synchronized(lock) {
        val top = phaseStack.peekLast()
        if (isSameLogicalPhase(top?.name, phase)) {
            if (total > 0) {
                top!!.totalItems = total.toLong()
                if (top.name == "CONFIGURATION" || top.name == "EXECUTION") {
                    top.name = phase
                }
            }
        } else {
            phaseStack.addLast(PhaseState(phase, total.toLong()))
        }
        emitProgress()
    }

    internal fun onPhaseFinish(phase: String) = synchronized(lock) {
        val it = phaseStack.descendingIterator()
        while (it.hasNext()) {
            val state = it.next()
            if (isSameLogicalPhase(state.name, phase)) {
                it.remove()
                break
            }
        }
        emitProgress(true)
    }

    internal fun onItemFinish() = synchronized(lock) {
        phaseStack.peekLast()?.let { it.completedItems++ }
        emitProgress()
    }

    internal fun addActiveOperation(operation: String) = synchronized(lock) {
        _activeOperations.add(operation)
        if (activeStatusOperation == null) activeStatusOperation = operation
        emitProgress()
    }

    internal fun removeActiveOperation(operation: String) = synchronized(lock) {
        _activeOperations.remove(operation)
        lastFinishedOperation = operation
        _subStatuses.remove(operation)
        if (activeStatusOperation == operation) {
            activeStatusOperation = _subStatuses.keys().asSequence().firstOrNull() ?: _activeOperations.sorted().firstOrNull()
        }
        emitProgress()
    }

    internal fun setSubStatus(status: String?, progress: Double? = null, operationPath: String? = null) = synchronized(lock) {
        val op = operationPath ?: activeStatusOperation ?: "unknown"
        _subStatuses[op] = SubStatus(status, progress)
        if (operationPath != null || activeStatusOperation == null) activeStatusOperation = op
        emitProgress()
    }

    internal fun getSubStatus(operation: String): SubStatus? = _subStatuses[operation]

    internal fun handleProgressLine(category: String, text: String) {
        val totalMatch = SUB_TASK_TOTAL_REGEX.matchEntire(text)
        if (totalMatch != null) {
            subTaskProgress = SubTaskProgress(totalMatch.groupValues[1].toLong(), 0, "Starting $category")
        } else {
            val progressMatch = SUB_TASK_PROGRESS_REGEX.matchEntire(text)
            if (progressMatch != null) {
                val completed = progressMatch.groupValues[1].toLong()
                val total = progressMatch.groupValues[2].toLong()
                val detail = progressMatch.groupValues[3]
                subTaskProgress = SubTaskProgress(total, completed, "$category: $detail")
            }
        }
        emitProgress()
    }

    fun getProgressValue(isFinish: Boolean = false): Double = synchronized(lock) {
        if (isFinish) return 1.0
        val subTask = subTaskProgress
        val subTaskProgressValue = if (subTask.total > 0) subTask.completed.toDouble() / subTask.total else null
        val total = totalItems
        if (total <= 0) return (subTaskProgressValue ?: 0.0).coerceAtMost(MAX_ITEM_PROGRESS)
        val completed = completedItems
        val subStatus = activeStatusOperation?.let { _subStatuses[it] }
        val currentItemProgress = (subStatus?.progress ?: subTaskProgressValue ?: 0.0).coerceAtMost(MAX_ITEM_PROGRESS)
        val progress = (completed + currentItemProgress) / total
        return progress.coerceAtMost(MAX_ITEM_PROGRESS)
    }

    fun getProgressMessage(): String = formatter.formatMessage()
}
