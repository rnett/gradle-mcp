package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.tools.PaginationUnit
import dev.rnett.gradle.mcp.tools.paginate
import dev.rnett.gradle.mcp.tools.paginateText
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue


class PaginationIntegrationTest : BaseMcpServerTest() {

    @Serializable
    data class ListToolArgs(val pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS)

    @Serializable
    data class TextToolArgs(
        val text: String,
        val pagination: PaginationInput = PaginationInput.DEFAULT_LINES,
        val unit: PaginationUnit = PaginationUnit.LINES
    )

    private fun createTestComponent() = object : McpServerComponent("test", "test") {
        val listTool by tool<ListToolArgs, String>("list_tool", "A tool that returns a paginated list") { args ->
            val items = (1..100).map { "item $it" }
            paginate(items, args.pagination, "items")
        }

        val textTool by tool<TextToolArgs, String>("text_tool", "A tool that returns paginated text") { args ->
            paginateText(args.text, args.pagination, args.unit)
        }
    }

    private suspend fun setupTools() {
        server.server.add(createTestComponent())
    }

    @Test
    fun `integration test collection pagination`() = runTest {
        setupTools()
        val response = server.client.callTool("list_tool", buildJsonObject {
            put("pagination", buildJsonObject {
                put("offset", 10)
                put("limit", 5)
            })
        }) as CallToolResult

        val text = (response.content.first() as TextContent).text
        requireNotNull(text)
        assertTrue(text.contains("item 11"))
        assertTrue(text.contains("item 15"))
        assertTrue(!text.contains("item 10"))
        assertTrue(!text.contains("item 16"))
        assertTrue(text.contains("Showing items 11 to 15 of 100"))
        assertTrue(text.contains("offset=15"))
    }

    @Test
    fun `integration test text pagination lines`() = runTest {
        setupTools()
        val fullText = (1..150).joinToString("\n") { "line $it" }
        val response = server.client.callTool("text_tool", buildJsonObject {
            put("text", fullText)
            put("pagination", buildJsonObject {
                put("offset", 5)
                put("limit", 5)
            })
            put("unit", "LINES")
        }) as CallToolResult

        val text = (response.content.first() as TextContent).text
        requireNotNull(text)
        assertTrue(text.contains("line 6"))
        assertTrue(text.contains("line 10"))
        assertTrue(!text.contains("line 5"))
        assertTrue(text.contains("Showing lines 6 to 10 of 150"))
    }

    @Test
    fun `integration test text pagination characters`() = runTest {
        setupTools()
        val fullText = "abcdefghijklmnopqrstuvwxyz"
        val response = server.client.callTool("text_tool", buildJsonObject {
            put("text", fullText)
            put("pagination", buildJsonObject {
                put("offset", 0)
                put("limit", 10)
            })
            put("unit", "CHARACTERS")
        }) as CallToolResult

        val text = (response.content.first() as TextContent).text
        requireNotNull(text)
        assertTrue(text.startsWith("abcdefghij"))
        assertTrue(text.contains("Showing characters 1 to 10 of 26"))
    }

    @Test
    fun `integration test default pagination items`() = runTest {
        setupTools()
        val response = server.client.callTool("list_tool", buildJsonObject { }) as CallToolResult

        val text = (response.content.first() as TextContent).text
        requireNotNull(text)
        assertTrue(text.contains("item 1"))
        assertTrue(text.contains("item 20"))
        assertTrue(text.contains("Showing items 1 to 20 of 100"))
    }

    @Test
    fun `integration test default pagination lines`() = runTest {
        setupTools()
        val fullText = (1..150).joinToString("\n") { "line $it" }
        val response = server.client.callTool("text_tool", buildJsonObject {
            put("text", fullText)
        }) as CallToolResult

        val text = (response.content.first() as TextContent).text
        requireNotNull(text)
        assertTrue(text.contains("line 1"))
        assertTrue(text.contains("line 100"))
        assertTrue(text.contains("Showing lines 1 to 100 of 150"))
    }
}
