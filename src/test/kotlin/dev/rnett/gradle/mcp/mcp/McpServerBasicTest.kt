package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Integration tests for the MCP server using a real Kotlin MCP client connected over in-memory STDIO streams.
 */
class McpServerBasicTest : BaseMcpServerTest() {

    @Test
    fun `client can initialize and list tools`() = runTest {
        val tools = server.client.listTools()
        assertTrue(tools.tools.isNotEmpty())
        assertContains(tools.tools.map { it.name }, "get_gradle_docs_link")
    }

}