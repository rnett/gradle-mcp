package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.ProgressReporter
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.shared.RequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.ServerNotification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class ProgressNotificationPipeline private constructor(
    private val clientConnection: ClientConnection,
    private val progressToken: RequestId?,
    private val extra: RequestHandlerExtra?,
    private val disableSampling: Boolean,
    dispatcher: CoroutineDispatcher
) : AutoCloseable {
    constructor(
        clientConnection: ClientConnection,
        progressToken: RequestId?,
        extra: RequestHandlerExtra?,
        disableSampling: Boolean = System.getProperty("gradle.mcp.test.disableSampling") == "true"
    ) : this(clientConnection, progressToken, extra, disableSampling, Dispatchers.Default)

    private val notificationQueue = Channel<ServerNotification>(
        capacity = NOTIFICATION_REPLAY + NOTIFICATION_EXTRA_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val progressQueue = Channel<Triple<Double, Double?, String?>>(
        capacity = PROGRESS_REPLAY + PROGRESS_EXTRA_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val progressReporter: ProgressReporter by lazy {
        if (progressToken != null) {
            scope.launch {
                val progressEvents = progressQueue.receiveAsFlow()
                val animated = progressEvents.transformLatest { (progress, total, message) ->
                    repeat(ANIMATION_STEPS) { step ->
                        val suffix = ".".repeat(step)
                        emit(Triple(progress, total, message?.plus(suffix)))
                        delay(ANIMATION_DELAY_MILLIS)
                    }
                }
                val sampled = if (disableSampling) progressEvents else animated.sample(SAMPLE_PERIOD_MILLIS)
                sampled.collect { (progress, total, message) ->
                    emitProgressNotification(progress, total, message)
                }
            }
        }

        ProgressReporter { progress, total, message ->
            if (progressToken != null) {
                progressQueue.trySend(Triple(progress, total, message))
            }
        }
    }

    internal fun emitProgressNotification(progress: Double, total: Double? = null, message: String? = null) {
        val token = progressToken ?: return
        emitNotification(
            ProgressNotification(
                ProgressNotificationParams(token, progress, total, message)
            )
        )
    }

    internal fun emitNotification(notification: ServerNotification) {
        notificationQueue.trySend(notification)
    }

    init {
        scope.launch {
            notificationQueue.receiveAsFlow().collect { notification ->
                try {
                    extra?.sendNotification(notification) ?: clientConnection.notification(notification)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LOGGER.error("Failed to emit MCP notification", e)
                }
            }
        }
    }

    override fun close() {
        progressQueue.cancel()
        notificationQueue.cancel()
        scope.cancel()
    }

    internal companion object {
        fun createForTest(
            clientConnection: ClientConnection,
            progressToken: RequestId?,
            extra: RequestHandlerExtra?,
            disableSampling: Boolean,
            dispatcher: CoroutineDispatcher
        ) = ProgressNotificationPipeline(clientConnection, progressToken, extra, disableSampling, dispatcher)

        const val NOTIFICATION_REPLAY = 0
        const val NOTIFICATION_EXTRA_CAPACITY = 500
        const val PROGRESS_REPLAY = 10
        const val PROGRESS_EXTRA_CAPACITY = 50
        const val ANIMATION_STEPS = 4
        const val ANIMATION_DELAY_MILLIS = 500L
        const val SAMPLE_PERIOD_MILLIS = 100L

        private val LOGGER = LoggerFactory.getLogger(ProgressNotificationPipeline::class.java)
    }
}
