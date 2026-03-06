package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.DocsPageContent
import dev.rnett.gradle.mcp.DocsSearchResponse
import dev.rnett.gradle.mcp.DocsSearchResult
import dev.rnett.gradle.mcp.DocsSectionSummary
import dev.rnett.gradle.mcp.GradleDocsService
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import io.mockk.coEvery
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradleDocsToolsTest : BaseMcpServerTest() {

    val mockDocsService: GradleDocsService get() = server.koin.get()

    @Test
    fun `gradle_docs returns content for path`() = runTest {
        val content = DocsPageContent.Markdown("# Test Content")
        coEvery { mockDocsService.getDocsPageContent("test.md", any()) } returns content

        val response = server.client.callTool(
            ToolNames.GRADLE_DOCS, buildJsonObject {
                put("path", "test.md")
            }
        ) as CallToolResult

        val text = (response.content.first() as TextContent).text
        assertEquals("# Test Content", text)
    }

    @Test
    fun `gradle_docs supports pagination for search results`() = runTest {
        val results = (1..5).map { i ->
            DocsSearchResult("title$i", "path$i", "snippet$i", "tag$i")
        }

        coEvery { mockDocsService.searchDocs("test", any()) } returns DocsSearchResponse(results)

        val response = server.client.callTool(
            ToolNames.GRADLE_DOCS, buildJsonObject {
                put("query", "test")
                put("pagination", buildJsonObject {
                    put("offset", 1)
                    put("limit", 2)
                })
            }
        ) as CallToolResult

        val text = (response.content.first() as TextContent).text
        assertTrue(text!!.contains("### title2"))
        assertTrue(text.contains("### title3"))
        assertTrue(!text.contains("### title1"))
        assertTrue(text.contains("Showing matches 2 to 3 of 5"))
    }

    @Test
    fun `gradle_docs supports pagination for summaries`() = runTest {
        val summaries = (1..5).map { i ->
            DocsSectionSummary("tag$i", "Display $i", i)
        }

        coEvery { mockDocsService.summarizeSections(any()) } returns summaries

        val response = server.client.callTool(
            ToolNames.GRADLE_DOCS, buildJsonObject {
                put("pagination", buildJsonObject {
                    put("offset", 2)
                    put("limit", 2)
                })
            }
        ) as CallToolResult

        val text = (response.content.first() as TextContent).text
        assertTrue(text!!.contains("Display 3"))
        assertTrue(text.contains("Display 4"))
        assertTrue(!text.contains("Display 1"))
        assertTrue(text.contains("Showing sections 3 to 4 of 5"))
    }

    @Test
    fun `gradle_docs returns error and sets isError`() = runTest {
        coEvery { mockDocsService.searchDocs("invalid query", any()) } returns DocsSearchResponse(emptyList(), error = "Lucene Syntax Error")

        val response = server.client.callTool(
            ToolNames.GRADLE_DOCS, buildJsonObject {
                put("query", "invalid query")
            }
        ) as CallToolResult

        assertEquals(true, response.isError, "isError should be true for search results with errors")
        val text = (response.content.first() as TextContent).text
        assertTrue(text!!.contains("Lucene Syntax Error"))
    }
}
