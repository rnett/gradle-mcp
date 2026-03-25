package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections

class ParallelProgressTrackerTest {

    @Test
    fun `test parallel focus switching`() = runTest {
        val messages = Collections.synchronizedList(mutableListOf<String>())
        val reporter = ProgressReporter { _, _, m -> if (m != null) messages.add(m) }
        val tracker = ParallelProgressTracker(reporter, 100.0)

        val jobs = List(10) { i ->
            launch {
                val depId = "dep-$i"
                tracker.onStart(depId)
                repeat(10) { j ->
                    tracker.reportMessage(depId, "Message $j for $depId")
                }
                tracker.onComplete(depId)
            }
        }
        jobs.joinAll()

        assertTrue(messages.isNotEmpty())
        assertTrue(messages.all { it.contains("(") && it.contains(")") })
    }

    @Test
    fun `test single dependency progress`() {
        val messages = mutableListOf<String>()
        val reporter = ProgressReporter { _, _, m -> if (m != null) messages.add(m) }
        val tracker = ParallelProgressTracker(reporter, 100.0, "Initial")

        tracker.onStart("dep1")
        assertEquals("Processing dep1 (0/100) (1 in progress)", messages.last())

        tracker.reportMessage("dep1", "Doing things")
        assertEquals("Doing things (0/100) (1 in progress)", messages.last())

        tracker.onComplete("dep1")
        assertEquals("Initial (1/100)", messages.last())
    }

    @Test
    fun `test focus switching with multiple dependencies`() {
        val messages = mutableListOf<String>()
        val reporter = ProgressReporter { _, _, m -> if (m != null) messages.add(m) }
        val tracker = ParallelProgressTracker(reporter, 100.0, "Initial")

        tracker.onStart("dep1")
        val msg1 = messages.last()
        assertTrue(msg1.contains("1 in progress") && msg1.contains("Processing dep1"))

        tracker.reportMessage("dep1", "dep1 stuff")
        val msg2 = messages.last()
        assertTrue(msg2.contains("1 in progress") && msg2.contains("dep1 stuff"))

        tracker.onStart("dep2")
        val msg3 = messages.last()
        // Focus should still be dep1
        assertTrue(msg3.contains("2 in progress") && msg3.contains("dep1 stuff"))

        tracker.reportMessage("dep2", "dep2 stuff")
        // Message shouldn't update to dep2's message since dep1 is focus
        val msg4 = messages.last()
        assertTrue(msg4.contains("2 in progress") && msg4.contains("dep1 stuff"))

        // Complete dep1, focus should switch to dep2
        tracker.onComplete("dep1")
        val msg5 = messages.last()
        assertTrue(msg5.contains("1 in progress") && msg5.contains("dep2 stuff"))

        tracker.onComplete("dep2")
        assertEquals("Initial (2/100)", messages.last())
    }
}
