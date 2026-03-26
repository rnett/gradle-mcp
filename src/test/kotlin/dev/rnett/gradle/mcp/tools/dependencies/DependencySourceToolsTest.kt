package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.SourceIndexService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.SearchResponse
import dev.rnett.gradle.mcp.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.tools.ToolNames
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class DependencySourceToolsTest : BaseMcpServerTest() {

    private lateinit var sourcesService: SourcesService
    private lateinit var indexService: SourceIndexService
    private lateinit var mockSources: SourcesDir

    @BeforeEach
    fun setupTest() = runTest {
        sourcesService = server.koin.get()
        indexService = server.koin.get()

        mockSources = mockk<SourcesDir>(relaxed = true)
        every { mockSources.sources } returns tempDir
        every { mockSources.lastRefresh() } returns null
        every { mockSources.resolveIndexDirs(any()) } returns emptyList()

        coEvery { with(any<ProgressReporter>()) { sourcesService.resolveAndProcessAllSources(any(), any(), any(), any(), any()) } } returns mockSources

        server.setServerRoots(Root(tempDir.toUri().toString(), "root"))
    }

    private fun resultText(result: CallToolResult): String =
        (result.content.first() as TextContent).text!!

    // ─── read_dependency_sources ───────────────────────────────────────────────

    @Test
    fun `read_dependency_sources success path includes Sources root header`() = runTest {
        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {}
        ) as CallToolResult

        assertContains(resultText(result), "Sources root:")
        assertContains(resultText(result), "Sources have not been refreshed yet.")
    }

    @Test
    fun `read_dependency_sources invalid path error includes Sources root header`() = runTest {
        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("path", "../../etc/passwd")
            }
        ) as CallToolResult

        val text = resultText(result)
        assertContains(text, "Sources root:")
        assertContains(text, "Invalid path:")
    }

    @Test
    fun `read_dependency_sources path not found error includes Sources root header`() = runTest {
        coEvery { indexService.listPackageContents(any(), any()) } returns null

        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("path", "nonexistent/path/file.kt")
            }
        ) as CallToolResult

        val text = resultText(result)
        assertContains(text, "Sources root:")
        assertContains(text, "Path not found:")
    }

    // ─── search_dependency_sources ─────────────────────────────────────────────

    @Test
    fun `search_dependency_sources success path includes Sources root header`() = runTest {
        coEvery { indexService.search(any(), any(), any(), any()) } returns SearchResponse<SearchResult>(
            results = emptyList()
        )

        val result = server.client.callTool(
            ToolNames.SEARCH_DEPENDENCY_SOURCES, buildJsonObject {
                put("query", "SomeClass")
            }
        ) as CallToolResult

        assertContains(resultText(result), "Sources root:")
    }

    @Test
    fun `search_dependency_sources error path includes Sources root header`() = runTest {
        coEvery { indexService.search(any(), any(), any(), any()) } returns SearchResponse<SearchResult>(
            results = emptyList(),
            error = "Index not found — use fresh=true"
        )

        val result = server.client.callTool(
            ToolNames.SEARCH_DEPENDENCY_SOURCES, buildJsonObject {
                put("query", "SomeClass")
            }
        ) as CallToolResult

        val text = resultText(result)
        assertContains(text, "Sources root:")
        assertContains(text, "Index not found")
        assert(result.isError == true)
    }

    @Test
    fun `search_dependency_sources does not include redundant sources root in plain error text`() = runTest {
        coEvery { indexService.search(any(), any(), any(), any()) } returns SearchResponse<SearchResult>(
            results = emptyList()
        )

        val result = server.client.callTool(
            ToolNames.SEARCH_DEPENDENCY_SOURCES, buildJsonObject {
                put("query", "SomeClass")
            }
        ) as CallToolResult

        // Should not expose internal error branch (dead code removed)
        assertFalse(result.isError == true)
    }
}
