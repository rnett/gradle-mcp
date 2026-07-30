package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.ProgressReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.shared.RequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.ServerNotification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ProgressNotificationPipelineTest {
    @Test
    fun `pipeline coroutine paths do not use Java monitor locking`() {
        val source = Path.of("src/main/kotlin/dev/rnett/gradle/mcp/mcp/ProgressNotificationPipeline.kt").readText()
        val pipelineSource = source.substringAfter("class ProgressNotificationPipeline").substringBefore("/**")

        assertFalse(pipelineSource.contains("synchronized("), "ProgressNotificationPipeline must not block coroutine threads on Java monitors")
    }

    @Test
    fun `pipeline collectors use normally dispatched startup`() {
        val source = Path.of("src/main/kotlin/dev/rnett/gradle/mcp/mcp/ProgressNotificationPipeline.kt").readText()
        val pipelineSource = source.substringAfter("class ProgressNotificationPipeline").substringBefore("/**")

        assertFalse(pipelineSource.contains("CoroutineStart.UNDISPATCHED"), "ProgressNotificationPipeline collectors must honor their dispatcher from startup")
    }

    @Test
    fun `normally dispatched collectors retain first emissions and serialize reentrant callbacks`() {
        val dispatcher = QueuedDispatcher()
        val messages = mutableListOf<String>()
        val extra = mockk<RequestHandlerExtra>()
        lateinit var pipeline: ProgressNotificationPipeline
        coEvery { extra.sendNotification(any()) } coAnswers {
            assertTrue(dispatcher.isRunning, "notification callback escaped the pipeline dispatcher")
            val message = (firstArg<ServerNotification>() as ProgressNotification).params.message.orEmpty()
            messages += message
            if (message == "first") pipeline.emitNotification(notification("reentrant"))
        }
        pipeline = ProgressNotificationPipeline.createForTest(
            mockk(relaxed = true),
            RequestId("progress"),
            extra,
            disableSampling = true,
            dispatcher = dispatcher
        )

        val reporter = pipeline.progressReporter
        assertEquals(2, dispatcher.pendingCount, "both collectors must be dispatched rather than started inline")
        pipeline.emitNotification(notification("first"))
        reporter.report(0.5, 1.0, "progress")
        assertTrue(messages.isEmpty(), "notifications must not execute on the constructing thread")

        dispatcher.runCurrent()

        assertEquals(listOf("first", "reentrant", "progress"), messages)
        pipeline.close()
        dispatcher.runCurrent()
    }

    @Test
    fun `pipeline constants retain bounded flow and timing contract`() {
        assertEquals(0, ProgressNotificationPipeline.NOTIFICATION_REPLAY)
        assertEquals(500, ProgressNotificationPipeline.NOTIFICATION_EXTRA_CAPACITY)
        assertEquals(10, ProgressNotificationPipeline.PROGRESS_REPLAY)
        assertEquals(50, ProgressNotificationPipeline.PROGRESS_EXTRA_CAPACITY)
        assertEquals(4, ProgressNotificationPipeline.ANIMATION_STEPS)
        assertEquals(500L, ProgressNotificationPipeline.ANIMATION_DELAY_MILLIS)
        assertEquals(100L, ProgressNotificationPipeline.SAMPLE_PERIOD_MILLIS)
    }

    @Test
    fun `captured request extra serializes notifications in enqueue order with bounded backpressure`() = runTest(timeout = 10.seconds) {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val allDelivered = CompletableDeferred<Unit>()
        val messages = mutableListOf<String>()
        val extra = mockk<RequestHandlerExtra>()
        val connection = mockk<ClientConnection>(relaxed = true)
        coEvery { extra.sendNotification(any()) } coAnswers {
            val message = (firstArg<ServerNotification>() as ProgressNotification).params.message.orEmpty()
            messages += message
            if (messages.size == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
            if (messages.size == 501) allDelivered.complete(Unit)
        }

        ProgressNotificationPipeline(connection, null, extra, disableSampling = true).use { pipeline ->
            emitAndAwait(pipeline, notification("first"), firstStarted)
            repeat(600) { pipeline.emitNotification(notification(it.toString())) }
            releaseFirst.complete(Unit)
            withContext(Dispatchers.Default) { withTimeout(5.seconds) { allDelivered.await() } }
        }

        assertEquals(listOf("first") + (100 until 600).map(Int::toString), messages)
        coVerify(exactly = 0) { connection.notification(any(), any()) }
    }

    @Test
    fun `null request extra falls back only to the invoking connection`() = runTest(timeout = 10.seconds) {
        val delivered = CompletableDeferred<Unit>()
        val connection = mockk<ClientConnection>()
        coEvery { connection.notification(any(), any()) } coAnswers { delivered.complete(Unit) }

        ProgressNotificationPipeline(connection, null, null, disableSampling = true).use { pipeline ->
            emitAndAwait(pipeline, notification("fallback"), delivered)
        }

        coVerify(exactly = 1) { connection.notification(any(), any()) }
    }

    @Test
    fun `progress token gates progress but not generic notifications`() = runTest(timeout = 10.seconds) {
        val delivered = CompletableDeferred<Unit>()
        val extra = mockk<RequestHandlerExtra>()
        val connection = mockk<ClientConnection>(relaxed = true)
        coEvery { extra.sendNotification(any()) } coAnswers { delivered.complete(Unit) }

        ProgressNotificationPipeline(connection, null, extra, disableSampling = true).use { pipeline ->
            pipeline.progressReporter.report(0.1, 1.0, "reporter")
            pipeline.emitProgressNotification(0.2, 1.0, "direct")
            emitAndAwait(pipeline, notification("generic"), delivered)
        }

        coVerify(exactly = 1) { extra.sendNotification(any()) }
    }

    @Test
    fun `sampling disablement forwards nested service reporter values directly`() = runTest(timeout = 10.seconds) {
        val delivered = CompletableDeferred<Unit>()
        val messages = mutableListOf<String>()
        val extra = mockk<RequestHandlerExtra>()
        coEvery { extra.sendNotification(any()) } coAnswers {
            messages += (firstArg<ServerNotification>() as ProgressNotification).params.message.orEmpty()
            if (messages.size == 3) delivered.complete(Unit)
        }
        val pipeline = ProgressNotificationPipeline(mockk(relaxed = true), RequestId("progress"), extra, disableSampling = true)

        withContext(Dispatchers.Default) {
            val service = NestedService()
            service.run(pipeline.progressReporter)
            withTimeout(5.seconds) { delivered.await() }
        }
        pipeline.close()

        assertEquals(listOf("nested-1", "nested-2", "nested-3"), messages)
    }

    @Test
    fun `sampling and animation emit four progress messages five hundred milliseconds apart`() = runTest(timeout = 10.seconds) {
        val delivered = CompletableDeferred<Unit>()
        val messages = mutableListOf<String>()
        val extra = mockk<RequestHandlerExtra>()
        coEvery { extra.sendNotification(any()) } coAnswers {
            messages += (firstArg<ServerNotification>() as ProgressNotification).params.message.orEmpty()
            if (messages.size == 4) delivered.complete(Unit)
        }
        val pipeline = ProgressNotificationPipeline(mockk(relaxed = true), RequestId("progress"), extra, disableSampling = false)

        pipeline.progressReporter.report(0.5, 1.0, "work")
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { delivered.await() } }
        pipeline.close()

        assertEquals(listOf("work", "work.", "work..", "work..."), messages)
    }

    @Test
    fun `notification failure is isolated from later queued delivery`() = runTest(timeout = 10.seconds) {
        val delivered = CompletableDeferred<Unit>()
        var attempts = 0
        val extra = mockk<RequestHandlerExtra>()
        coEvery { extra.sendNotification(any()) } coAnswers {
            attempts++
            if (attempts == 1) error("expected send failure")
            delivered.complete(Unit)
        }

        ProgressNotificationPipeline(mockk(relaxed = true), null, extra, disableSampling = true).use { pipeline ->
            pipeline.emitNotification(notification("fails"))
            emitAndAwait(pipeline, notification("succeeds"), delivered)
        }

        assertEquals(2, attempts)
    }

    @Test
    fun `close before dispatched startup cancels buffered and post close work`() {
        val dispatcher = QueuedDispatcher()
        val extra = mockk<RequestHandlerExtra>(relaxed = true)
        val pipeline = ProgressNotificationPipeline.createForTest(
            mockk(relaxed = true),
            RequestId("progress"),
            extra,
            disableSampling = true,
            dispatcher = dispatcher
        )
        val reporter = pipeline.progressReporter
        pipeline.emitNotification(notification("buffered"))
        reporter.report(0.5, 1.0, "buffered-progress")

        pipeline.close()
        pipeline.close()
        pipeline.emitNotification(notification("closed"))
        reporter.report(1.0, 1.0, "closed-progress")
        dispatcher.runCurrent()

        coVerify(exactly = 0) { extra.sendNotification(any()) }
    }

    @Test
    fun `close is idempotent non suspending drops queued work and rejects every send surface`() = runTest(timeout = 10.seconds) {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val extra = mockk<RequestHandlerExtra>()
        coEvery { extra.sendNotification(any()) } coAnswers {
            firstStarted.complete(Unit)
            releaseFirst.await()
        }
        val pipeline = ProgressNotificationPipeline(mockk(relaxed = true), RequestId("progress"), extra, disableSampling = true)

        emitAndAwait(pipeline, notification("in-flight"), firstStarted)
        repeat(10) { pipeline.emitNotification(notification("queued-$it")) }
        pipeline.close()
        pipeline.close()
        pipeline.progressReporter.report(1.0, 1.0, "closed-reporter")
        pipeline.emitProgressNotification(1.0, 1.0, "closed-direct")
        pipeline.emitNotification(notification("closed-generic"))
        releaseFirst.complete(Unit)
        repeat(10) { yield() }

        coVerify(atMost = 1) { extra.sendNotification(any()) }
    }

    private suspend fun emitAndAwait(
        pipeline: ProgressNotificationPipeline,
        notification: ServerNotification,
        delivered: CompletableDeferred<Unit>
    ) {
        withContext(Dispatchers.Default) {
            pipeline.emitNotification(notification)
            withTimeout(5.seconds) { delivered.await() }
        }
    }

    private fun notification(message: String): ProgressNotification = ProgressNotification(
        ProgressNotificationParams(RequestId("generic"), 0.5, 1.0, message)
    )

    private class NestedService {
        suspend fun run(progressReporter: ProgressReporter) {
            progressReporter.report(0.1, 1.0, "nested-1")
            progressReporter.report(0.2, 1.0, "nested-2")
            progressReporter.report(0.3, 1.0, "nested-3")
        }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        var isRunning = false
            private set

        val pendingCount: Int
            get() = tasks.size

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runCurrent() {
            while (tasks.isNotEmpty()) {
                isRunning = true
                try {
                    tasks.removeFirst().run()
                } finally {
                    isRunning = false
                }
            }
        }
    }
}
