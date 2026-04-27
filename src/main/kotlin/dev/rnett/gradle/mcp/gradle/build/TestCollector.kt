@file:Suppress("UnstableApiUsage")

package dev.rnett.gradle.mcp.gradle.build

import org.gradle.tooling.CancellationToken
import org.gradle.tooling.Failure
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.test.Destination
import org.gradle.tooling.events.test.JvmTestKind
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.TestFailureResult
import org.gradle.tooling.events.test.TestFileAttachmentMetadataEvent
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestKeyValueMetadataEvent
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.TestOutputEvent
import org.gradle.tooling.events.test.TestSkippedResult
import org.gradle.tooling.events.test.TestStartEvent
import org.gradle.tooling.events.test.TestSuccessResult
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalAtomicApi::class)
internal val TestOperationDescriptor.isAtomic: Boolean
    get() = if (this is JvmTestOperationDescriptor) {
        this.jvmTestKind == JvmTestKind.ATOMIC
    } else {
        !this.displayName.contains("Test Executor") && !this.displayName.contains("Test Run")
    }

@OptIn(ExperimentalAtomicApi::class)
internal fun TestOperationDescriptor.getSuiteAndTestName(): Pair<String?, String> {
    if (this is JvmTestOperationDescriptor) {
        val cls = this.className
        val method = this.methodName
        if (cls != null && method != null) {
            return cls to method
        }
    }
    val name = this.displayName
    val lastDot = name.lastIndexOf('.')
    if (lastDot != -1 && lastDot != 0 && lastDot != name.length - 1) {
        return name.substring(0, lastDot) to name.substring(lastDot + 1)
    }
    return null to name
}

/**
 * Collects test results during a Gradle build execution.
 *
 * Performance Note: This collector uses concurrent collections and atomic counters instead of
 * coarse-grained synchronization. This improves throughput during massive parallel test execution.
 * As a result, test results reported by [results] or [totalCount] may be slightly out-of-date
 * or temporarily inconsistent during concurrent updates, which is acceptable for progress reporting.
 */
@OptIn(ExperimentalAtomicApi::class)
class TestCollector(
    val captureFailedTestOutput: Boolean,
    val captureAllTestOutput: Boolean,
    private val cancellationToken: CancellationToken? = null
) : ProgressListener {
    @Volatile
    var isCancelled: Boolean = false
    private val output = ConcurrentHashMap<TestOperationDescriptor, StringBuffer>()

    // Concurrent test results storage
    private val passed = ConcurrentLinkedQueue<Result>()
    private val skipped = ConcurrentLinkedQueue<Result>()
    private val failed = ConcurrentLinkedQueue<Result>()
    private val cancelled = ConcurrentLinkedQueue<Result>()
    private val inProgress = ConcurrentHashMap<TestOperationDescriptor, Long>() // Map of descriptor to start time

    private val metadata = ConcurrentHashMap<TestOperationDescriptor, ConcurrentHashMap<String, String>>()
    private val attachments = ConcurrentHashMap<TestOperationDescriptor, ConcurrentLinkedQueue<FileAttachment>>()

    // Unique name assignment
    private val testNames = ConcurrentHashMap<TestOperationDescriptor, Pair<String?, String>>()
    private val baseNameCounts = ConcurrentHashMap<String, AtomicInt>()

    private fun getOrAssignTestName(descriptor: TestOperationDescriptor): Pair<String?, String> {
        return testNames.computeIfAbsent(descriptor) {
            val (suite, baseTestName) = descriptor.getSuiteAndTestName()
            val fullName = if (suite != null) "$suite.$baseTestName" else baseTestName
            val count = baseNameCounts.computeIfAbsent(fullName) { AtomicInt(0) }.addAndFetch(1)
            val finalTestName = if (count > 1) "$baseTestName #$count" else baseTestName
            suite to finalTestName
        }
    }

    // Performance-friendly counters. Inconsistency is acceptable for progress updates.
    private val _passedCount = AtomicInt(0)
    private val _failedCount = AtomicInt(0)
    private val _skippedCount = AtomicInt(0)
    private val _cancelledCount = AtomicInt(0)
    private val _inProgressCount = AtomicInt(0)

    val passedCount get() = _passedCount.load()
    val failedCount get() = _failedCount.load()
    val skippedCount get() = _skippedCount.load()
    val cancelledCount get() = _cancelledCount.load()
    val inProgressCount get() = _inProgressCount.load()
    val totalCount get() = passedCount + failedCount + skippedCount + cancelledCount + inProgressCount

    data class FileAttachment(val file: Path, val mediaType: String?)

    data class Results(
        val passed: Set<Result>,
        val skipped: Set<Result>,
        val failed: Set<Result>,
        val cancelled: Set<Result>,
        val inProgress: Set<Result>
    )

    data class Result(
        val testName: String,
        val suiteName: String?,
        val taskPath: String?,
        val output: String?,
        val duration: Duration,
        val failures: List<Failure>?,
        val metadata: Map<String, String>,
        val attachments: List<FileAttachment>
    )

    override fun statusChanged(event: ProgressEvent) {
        when (event) {
            is TestStartEvent -> handleTestStart(event)
            is TestFinishEvent -> handleTestFinish(event)
            is TestOutputEvent -> handleTestOutput(event)
            is TestKeyValueMetadataEvent -> handleTestMetadata(event)
            is TestFileAttachmentMetadataEvent -> handleTestFileAttachment(event)
        }
    }

    private fun findTaskPath(descriptor: TestOperationDescriptor?): String? {
        var current: org.gradle.tooling.events.OperationDescriptor? = descriptor
        while (current != null) {
            if (current is org.gradle.tooling.events.task.TaskOperationDescriptor) {
                return current.taskPath
            }
            current = current.parent
        }
        return null
    }

    private fun handleTestStart(event: TestStartEvent) {
        val descriptor = event.descriptor
        inProgress[descriptor] = event.eventTime
        if (descriptor.isAtomic) {
            _inProgressCount.addAndFetch(1)
        }
    }

    private fun handleTestFinish(event: TestFinishEvent) {
        val descriptor = event.descriptor
        val wasInProgress = inProgress.remove(descriptor) != null
        if (wasInProgress && descriptor.isAtomic) {
            _inProgressCount.addAndFetch(-1)
        }

        val (suiteName, testName) = getOrAssignTestName(descriptor)
        val testResult = Result(
            testName,
            suiteName,
            findTaskPath(descriptor),
            null,
            (event.result.endTime - event.result.startTime).milliseconds,
            null,
            metadata.remove(descriptor) ?: emptyMap(),
            attachments.remove(descriptor)?.toList() ?: emptyList()
        )
        val outputText = output.remove(descriptor)?.toString() ?: ""

        when (val result = event.result) {
            is TestSuccessResult -> {
                if (descriptor.isAtomic) {
                    passed += testResult.copy(output = outputText.takeIf { captureAllTestOutput })
                    _passedCount.addAndFetch(1)
                }
            }

            is TestSkippedResult -> {
                if (descriptor.isAtomic) {
                    val r = testResult.copy(output = outputText.takeIf { captureAllTestOutput })
                    if (wasInProgress && (isCancelled || cancellationToken?.isCancellationRequested == true)) {
                        cancelled += r
                        _cancelledCount.addAndFetch(1)
                    } else {
                        skipped += r
                        _skippedCount.addAndFetch(1)
                    }
                }
            }

            is TestFailureResult -> {
                if (descriptor.isAtomic) {
                    failed += testResult.copy(
                        failures = result.failures.toList(),
                        output = outputText.takeIf { captureAllTestOutput || captureFailedTestOutput }
                    )
                    _failedCount.addAndFetch(1)
                }
            }
        }
    }

    private fun handleTestOutput(event: TestOutputEvent) {
        val descriptor = event.descriptor.parent as? TestOperationDescriptor ?: return
        val prefix = if (event.descriptor.destination == Destination.StdErr) "STDERR: " else ""
        output.computeIfAbsent(descriptor) { StringBuffer() }.append(prefix + event.descriptor.message)
    }

    private fun handleTestMetadata(event: TestKeyValueMetadataEvent) {
        val descriptor = event.descriptor.parent as? TestOperationDescriptor ?: return
        metadata.computeIfAbsent(descriptor) { ConcurrentHashMap<String, String>() }.putAll(event.values)
    }

    private fun handleTestFileAttachment(event: TestFileAttachmentMetadataEvent) {
        val descriptor = event.descriptor.parent as? TestOperationDescriptor ?: return
        attachments.computeIfAbsent(descriptor) { ConcurrentLinkedQueue<FileAttachment>() }
            .add(FileAttachment(event.file.toPath(), event.mediaType))
    }

    fun results(endTime: Long): Results {
        val inProgressResults = inProgress.filterKeys { it.isAtomic }.map { (descriptor, startTime) ->
            val (suiteName, testName) = getOrAssignTestName(descriptor)
            Result(
                testName,
                suiteName,
                findTaskPath(descriptor),
                output[descriptor]?.toString(),
                (endTime - startTime).milliseconds,
                null,
                metadata[descriptor] ?: emptyMap(),
                attachments[descriptor]?.toList() ?: emptyList()
            )
        }.toSet()

        return Results(
            passed = passed.toSet(),
            skipped = skipped.toSet(),
            failed = failed.toSet(),
            cancelled = cancelled.toSet(),
            inProgress = inProgressResults
        )
    }

    val operations = buildSet {
        add(OperationType.TEST)
        if (captureFailedTestOutput || captureAllTestOutput) {
            add(OperationType.TEST_OUTPUT)
            add(OperationType.TEST_METADATA)
        }
    }
}
