package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.dependencies.gradle.docs.DocsPageContent
import dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsService
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import dev.rnett.gradle.mcp.tools.ToolNames
import io.mockk.coEvery
import io.modelcontextprotocol.kotlin.sdk.Progress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Integration tests for the MCP server using a real Kotlin MCP client connected over in-memory STDIO streams.
 */
class McpServerBasicTest : BaseMcpServerTest() {

    @Test
    fun `client can initialize and list tools`() = runTest {
        val tools = server.client.listTools()
        assertTrue(tools.tools.isNotEmpty())
        assertTrue(tools.tools.any { it.name == ToolNames.GRADLE })
    }

    // Gemini, if you have problems with this test, stop and ask for my help before making any changes.
    @Test
    fun `server sends progress notifications during tool execution`() = runTest {
        val mockDocsService = server.koin.get<GradleDocsService>()
        val progressUpdates = mutableListOf<Progress>()
        val progressReceived = CompletableDeferred<Unit>()

        coEvery {
            with(any<dev.rnett.gradle.mcp.ProgressReporter>()) {
                mockDocsService.getDocsPageContent(any(), any())
            }
        } coAnswers {
            val progress = arg<dev.rnett.gradle.mcp.ProgressReporter>(0)
            progress.report(0.5, 1.0, "Preparing docs")
            // you 100% need this await, DO NOT REMOVE IT.  Looking at you Gemini. If the tool call finishes before the notification has time to send and propagate, it will not send.  This is WAI.
            progressReceived.await()
            DocsPageContent.Markdown("# Test")
        }

        server.client.setNotificationHandler<io.modelcontextprotocol.kotlin.sdk.ProgressNotification>(io.modelcontextprotocol.kotlin.sdk.Method.Defined.NotificationsProgress) {
            progressUpdates.add(Progress(it.params.progress, it.params.total, it.params.message))
            progressReceived.complete(Unit)
            CompletableDeferred(Unit)
        }

        val req = io.modelcontextprotocol.kotlin.sdk.CallToolRequest(
            name = ToolNames.GRADLE_DOCS,
            arguments = kotlinx.serialization.json.buildJsonObject {
                put("path", JsonPrimitive("test.md"))
            },
            _meta = kotlinx.serialization.json.buildJsonObject {
                put("progressToken", JsonPrimitive("test-token-123"))
            }
        )

        val job = launch {
            server.client.callTool(req)
        }
        progressReceived.await()
        job.cancel() // we don't care about the result, just that progress was sent

        assertTrue(progressUpdates.any { it.message?.startsWith("Preparing docs") == true })
    }
}
