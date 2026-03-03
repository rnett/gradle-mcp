package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.gradle.dependencies.SourcesDir
import dev.rnett.gradle.mcp.gradle.dependencies.SourcesService
import dev.rnett.gradle.mcp.gradle.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import dev.rnett.gradle.mcp.tools.ToolNames
import io.mockk.coEvery
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencySourceToolsTest : BaseMcpServerTest() {

    private lateinit var sourcesService: SourcesService

    @BeforeTest
    fun setupTest() {
        sourcesService = server.koin.get()
        server.setServerRoots(io.modelcontextprotocol.kotlin.sdk.Root(tempDir.toUri().toString(), "root"))
    }

    @Test
    fun `search_dependency_sources returns formatted results`() = runTest {
        val sourcesDir = tempDir.resolve("sources-root-search")
        val sources = sourcesDir.resolve("sources")
        sources.createDirectories()

        coEvery {
            sourcesService.downloadAllSources(any(), any(), any())
        } returns SourcesDir(sourcesDir)

        coEvery {
            sourcesService.search(any(), any(), "test-query")
        } returns listOf(
            SearchResult(
                relativePath = "com/example/Test.kt",
                file = sources.resolve("com/example/Test.kt"),
                line = 10,
                snippet = "class Test { }",
                score = 1.0f
            )
        )

        val response = server.client.callTool(
            ToolNames.SEARCH_DEPENDENCY_SOURCES, buildJsonObject {
                put("query", "test-query")
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text
        assertTrue(result!!.contains("Found 1 result(s) for 'test-query':"))
        assertTrue(result.contains("File: com/example/Test.kt:10"))
        assertTrue(result.contains("class Test { }"))
    }

    @Test
    fun `read_dependency_source_path returns file content`() = runTest {
        val sourcesDir = tempDir.resolve("sources-root")
        val sources = sourcesDir.resolve("sources")
        sources.createDirectories()
        val testFile = sources.resolve("com/example/Test.kt")
        testFile.parent.createDirectories()
        testFile.writeText("package com.example\n\nclass Test")

        coEvery {
            sourcesService.downloadAllSources(any(), any(), any())
        } returns SourcesDir(sourcesDir)

        val response = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCE_PATH, buildJsonObject {
                put("path", "com/example/Test.kt")
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text
        assertTrue(result!!.contains("package com.example"))
        assertTrue(result.contains("class Test"))
        assertTrue(result.contains("File: com/example/Test.kt"))
    }

    @Test
    fun `read_dependency_source_path returns directory tree`() = runTest {
        val sourcesDir = tempDir.resolve("sources-root-dir")
        val sources = sourcesDir.resolve("sources")
        sources.createDirectories()

        sources.resolve("com/example/a_dir").createDirectories()
        sources.resolve("com/example/a_dir/File1.kt").writeText("")
        sources.resolve("com/example/z_file.kt").writeText("")

        coEvery {
            sourcesService.downloadAllSources(any(), any(), any())
        } returns SourcesDir(sourcesDir)

        val response = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCE_PATH, buildJsonObject {
                put("path", "com/example")
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text
        assertTrue(result!!.contains("example/"))
        assertTrue(result.contains("├── a_dir/"))
        assertTrue(result.contains("│   └── File1.kt"))
        assertTrue(result.contains("└── z_file.kt"))
    }

    @Test
    fun `read_dependency_source_path prevents path traversal`() = runTest {
        val sourcesDir = tempDir.resolve("sources-root-traversal")
        val sources = sourcesDir.resolve("sources")
        sources.createDirectories()

        coEvery {
            sourcesService.downloadAllSources(any(), any(), any())
        } returns SourcesDir(sourcesDir)

        val response = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCE_PATH, buildJsonObject {
                put("path", "../outside.txt")
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text
        assertEquals("Invalid path: ../outside.txt", result)
    }

    @Test
    fun `read_dependency_source_path returns error for non-existent path`() = runTest {
        val sourcesDir = tempDir.resolve("sources-root-missing")
        val sources = sourcesDir.resolve("sources")
        sources.createDirectories()

        coEvery {
            sourcesService.downloadAllSources(any(), any(), any())
        } returns SourcesDir(sourcesDir)

        val response = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCE_PATH, buildJsonObject {
                put("path", "missing.kt")
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text
        assertEquals("Path not found: missing.kt", result)
    }
}
