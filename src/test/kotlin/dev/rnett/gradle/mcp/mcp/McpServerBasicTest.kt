package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import dev.rnett.gradle.mcp.tools.ToolNames
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Integration tests for the MCP server using a real Kotlin MCP client connected over in-memory STDIO streams.
 */
class McpServerBasicTest : BaseMcpServerTest() {

    @Test
    fun `client can initialize and list tools`() = runTest {
        val tools = server.client.listTools()
        assert(tools.tools.isNotEmpty())
        assert(tools.tools.any { it.name == ToolNames.RUN_TESTS_WITH_GRADLE })
    }

}