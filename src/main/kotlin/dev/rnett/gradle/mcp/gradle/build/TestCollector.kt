@file:Suppress("UnstableApiUsage")

package dev.rnett.gradle.mcp.gradle.build

import org.gradle.tooling.CancellationToken
import org.gradle.tooling.Failure
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.test.Destination
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal fun TestOperationDescriptor.testName(): String? {
    if (this is JvmTestOperationDescriptor) {
        val cls = this.className
        val method = this.methodName
        if (cls != null && method != null) {
            return "$cls.$method"
        }
    }
    return null
}

/**
 * Collects test results during a Gradle build execution.
 * 
 * Note: Test counts are incremental based on received events. Since Gradle discovers tests 
 * dynamically, the total count may increase during execution. This collector tracks 
 * "atomic" tests (actual test methods) and ignores suite-level events for progress reporting.
 */
class TestCollector(
    val captureFailedTestOutput: Boolean,
    val captureAllTestOutput: Boolean,
    private val cancellationToken: CancellationToken? = null
) : ProgressListener {
    @Volatile
    var isCancelled: Boolean = false
    private val output = ConcurrentHashMap<String, StringBuffer>()
    private val passed = mutableListOf<Result>()
    private val skipped = mutableListOf<Result>()
    private val failed = mutableListOf<Result>()
    private val cancelled = mutableListOf<Result>()
    private val inProgress = mutableMapOf<String, Long>() // Map of test name to start time
    private val metadata = mutableMapOf<String, MutableMap<String, String>>()
    private val attachments = mutableMapOf<String, MutableList<FileAttachment>>()

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
        val output: String?,
        val duration: Duration,
        val failures: List<Failure>?,
        val metadata: Map<String, String>,
        val attachments: List<FileAttachment>
    )

    val passedCount get() = synchronized(this) { passed.size }
    val failedCount get() = synchronized(this) { failed.size }
    val skippedCount get() = synchronized(this) { skipped.size }
    val totalCount get() = synchronized(this) { passed.size + failed.size + skipped.size + cancelled.size + inProgress.size }

    override fun statusChanged(event: ProgressEvent) {
        synchronized(this) {
            when (event) {
                is TestStartEvent -> {
                    val testName = event.descriptor.testName() ?: return
                    inProgress[testName] = event.eventTime
                }

                is TestFinishEvent -> {
                    val testName = event.descriptor.testName() ?: return
                    val wasInProgress = inProgress.remove(testName) != null
                    val testResult = Result(
                        testName,
                        null,
                        (event.result.endTime - event.result.startTime).milliseconds,
                        null,
                        metadata.remove(testName) ?: emptyMap(),
                        attachments.remove(testName) ?: emptyList()
                    )
                    val output = output.remove(testName)?.toString() ?: ""
                    when (val result = event.result) {
                        is TestSuccessResult -> {
                            passed += testResult.copy(output = output.takeIf { captureAllTestOutput })
                        }

                        is TestSkippedResult -> {
                            val r = testResult.copy(output = output.takeIf { captureAllTestOutput })
                            if (wasInProgress && (isCancelled || cancellationToken?.isCancellationRequested == true)) {
                                cancelled += r
                            } else {
                                skipped += r
                            }
                        }

                        is TestFailureResult -> {
                            failed += testResult.copy(
                                failures = result.failures.toList(),
                                output = output.takeIf { captureAllTestOutput || captureFailedTestOutput }
                            )
                        }
                    }
                }

                is TestOutputEvent -> {
                    val testName = (event.descriptor.parent as? TestOperationDescriptor)?.testName() ?: return
                    val prefix = if (event.descriptor.destination == Destination.StdErr) "STDERR: " else ""
                    output.computeIfAbsent(testName) { StringBuffer() }.append(prefix + event.descriptor.message)
                }

                is TestKeyValueMetadataEvent -> {
                    val testName = (event.descriptor.parent as? TestOperationDescriptor)?.testName() ?: return
                    metadata.getOrPut(testName) { mutableMapOf() }.putAll(event.values)
                }

                is TestFileAttachmentMetadataEvent -> {
                    val testName = (event.descriptor.parent as? TestOperationDescriptor)?.testName() ?: return
                    attachments.getOrPut(testName) { mutableListOf() }
                        .add(FileAttachment(event.file.toPath(), event.mediaType))
                }
            }
        }
    }

    fun results(endTime: Long): Results {
        return synchronized(this) {
            val inProgressResults = inProgress.map { (name, startTime) ->
                Result(
                    name,
                    output[name]?.toString(),
                    (endTime - startTime).milliseconds,
                    null,
                    metadata[name] ?: emptyMap(),
                    attachments[name] ?: emptyList()
                )
            }.toSet()

            Results(
                passed = passed.toList().toSet(),
                skipped = skipped.toList().toSet(),
                failed = failed.toList().toSet(),
                cancelled = cancelled.toList().toSet(),
                inProgress = inProgressResults
            )
        }
    }

    val operations = buildSet {
        add(OperationType.TEST)
        if (captureFailedTestOutput || captureAllTestOutput) {
            add(OperationType.TEST_OUTPUT)
            add(OperationType.TEST_METADATA)
        }
    }
}
