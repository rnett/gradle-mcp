package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.DI
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.shared.RequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class McpContextTest {
    @Test
    fun `captured request extra delivers notifications from the async progress scope`() = runTest(timeout = 10.seconds) {
        val delivered = CompletableDeferred<Unit>()
        val extra = mockk<RequestHandlerExtra>()
        val connection = mockk<ClientConnection>(relaxed = true)
        coEvery { extra.sendNotification(any()) } coAnswers {
            delivered.complete(Unit)
        }

        McpContext(DI.json, null, connection, RequestId("progress"), extra).use { context ->
            emitUntilDelivered(context, delivered)
        }

        coVerify(atLeast = 1) { extra.sendNotification(any()) }
        coVerify(exactly = 0) { connection.notification(any(), any()) }
    }

    @Test
    fun `null request extra falls back to the client connection`() = runTest(timeout = 10.seconds) {
        val delivered = CompletableDeferred<Unit>()
        val connection = mockk<ClientConnection>()
        coEvery { connection.notification(any(), any()) } coAnswers {
            delivered.complete(Unit)
        }

        McpContext(DI.json, null, connection, RequestId("progress"), null).use { context ->
            emitUntilDelivered(context, delivered)
        }

        coVerify(atLeast = 1) { connection.notification(any(), any()) }
    }

    private suspend fun emitUntilDelivered(context: McpContext, delivered: CompletableDeferred<Unit>) {
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                while (!delivered.isCompleted) {
                    context.emitProgressNotification(0.5, 1.0, "testing")
                    yield()
                }
                delivered.await()
            }
        }
    }
}
