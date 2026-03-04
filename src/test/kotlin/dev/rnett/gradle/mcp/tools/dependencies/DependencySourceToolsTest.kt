package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.gradle.dependencies.GradleSourceService
import dev.rnett.gradle.mcp.gradle.dependencies.SourcesDir
import dev.rnett.gradle.mcp.gradle.dependencies.SourcesService
import dev.rnett.gradle.mcp.gradle.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.gradle.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.gradle.dependencies.search.SymbolSearch
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
    private lateinit var gradleSourceService: GradleSourceService

    @BeforeTest
    fun setupTest() {
        sourcesService = server.koin.get()
        gradleSourceService = server.koin.get()
        server.setServerRoots(io.modelcontextprotocol.kotlin.sdk.Root(tempDir.toUri().toString(), "root"))
    }

    @Test
    fun `search_dependency_sources search returns formatted results`() = runTest {
        val sourcesDir = tempDir.resolve("sources-root-search")
        val sources = sourcesDir.resolve("sources")
        sources.createDirectories()

        coEvery {
            sourcesService.downloadAllSources(any(), any(), any(), any())
        } returns SourcesDir(sourcesDir)

        coEvery {
            sourcesService.search(any(), SymbolSearch, "test-query")
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
    fun `search_dependency_sources full-text search returns formatted results`() = runTest {
        val sourcesDir = tempDir.resolve("sources-root-full-text")
        val sources = sourcesDir.resolve("sources")
        sources.createDirectories()

        coEvery {
            sourcesService.downloadAllSources(any(), any(), any(), any())
        } returns SourcesDir(sourcesDir)

        coEvery {
            sourcesService.search(any(), FullTextSearch, "test-query")
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
                put("searchType", "FULL_TEXT")
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text
        assertTrue(result!!.contains("Found 1 result(s) for 'test-query':"))
        assertTrue(result.contains("File: com/example/Test.kt:10"))
        assertTrue(result.contains("class Test { }"))
    }

    @Test
    fun `read_dependency_sources read returns file content`() = runTest {
        val sourcesDir = tempDir.resolve("sources-root")
        val sources = sourcesDir.resolve("sources")
        sources.createDirectories()
        val testFile = sources.resolve("com/example/Test.kt")
        testFile.parent.createDirectories()
        testFile.writeText("package com.example\n\nclass Test")

        coEvery {
            sourcesService.downloadAllSources(any(), any(), any(), any())
        } returns SourcesDir(sourcesDir)

        val response = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("path", "com/example/Test.kt")
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text
        assertTrue(result!!.contains("package com.example"))
        assertTrue(result.contains("class Test"))
        assertTrue(result.contains("File: com/example/Test.kt"))
    }

    @Test
    fun `read_dependency_sources read returns directory tree`() = runTest {
        val sourcesDir = tempDir.resolve("sources-root-dir")
        val sources = sourcesDir.resolve("sources")
        sources.createDirectories()

        sources.resolve("com/example/a_dir").createDirectories()
        sources.resolve("com/example/a_dir/File1.kt").writeText("")
        sources.resolve("com/example/z_file.kt").writeText("")

        coEvery {
            sourcesService.downloadAllSources(any(), any(), any(), any())
        } returns SourcesDir(sourcesDir)

        val response = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
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
    fun `read_dependency_sources prevents path traversal`() = runTest {
        val sourcesDir = tempDir.resolve("sources-root-traversal")
        val sources = sourcesDir.resolve("sources")
        sources.createDirectories()

        coEvery {
            sourcesService.downloadAllSources(any(), any(), any(), any())
        } returns SourcesDir(sourcesDir)

        val response = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("path", "../outside.txt")
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text
        assertEquals("Invalid path: ../outside.txt", result)
    }

    @Test
    fun `read_dependency_sources returns error for non-existent path`() = runTest {
        val sourcesDir = tempDir.resolve("sources-root-missing")
        val sources = sourcesDir.resolve("sources")
        sources.createDirectories()

        coEvery {
            sourcesService.downloadAllSources(any(), any(), any(), any())
        } returns SourcesDir(sourcesDir)

        val response = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("path", "missing.kt")
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text
        assertEquals("Path not found: missing.kt", result)
    }

    @Test
    fun `read_dependency_sources with project path calls correct service method`() = runTest {
        val sourcesDir = tempDir.resolve("sources-project")
        sourcesDir.createDirectories()

        coEvery {
            sourcesService.downloadProjectSources(any(), ":app", any(), any(), any())
        } returns SourcesDir(sourcesDir)

        server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("projectPath", ":app")
            }
        ) as CallToolResult
    }

    @Test
    fun `read_dependency_sources with gradleSource calls gradleSourceService`() = runTest {
        val sourcesDir = tempDir.resolve("sources-gradle")
        sourcesDir.createDirectories()

        coEvery {
            gradleSourceService.getGradleSources(any(), any())
        } returns SourcesDir(sourcesDir)

        server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("gradleSource", true)
            }
        ) as CallToolResult

        coEvery {
            gradleSourceService.getGradleSources(any(), any())
        }
    }

    @Test
    fun `search_dependency_sources with gradleSource calls gradleSourceService`() = runTest {
        val sourcesDir = tempDir.resolve("sources-gradle-search")
        sourcesDir.createDirectories()

        coEvery {
            gradleSourceService.getGradleSources(any(), any())
        } returns SourcesDir(sourcesDir)

        coEvery {
            sourcesService.search(any(), any(), any())
        } returns emptyList()

        server.client.callTool(
            ToolNames.SEARCH_DEPENDENCY_SOURCES, buildJsonObject {
                put("gradleSource", true)
                put("query", "test")
            }
        ) as CallToolResult

        coEvery {
            gradleSourceService.getGradleSources(any(), any())
        }
    }

    @Test
    fun `read_dependency_sources returns last refresh message`() = runTest {
        val sourcesDir = tempDir.resolve("sources-refresh")
        val sources = sourcesDir.resolve("sources")
        sources.createDirectories()
        val metadata = sourcesDir.resolve("metadata")
        metadata.createDirectories()
        val lastRefresh = java.time.Instant.now().minusSeconds(3600) // 1 hour ago
        metadata.resolve(".last_refresh").writeText(lastRefresh.toString())

        coEvery {
            sourcesService.downloadAllSources(any(), any(), any(), any())
        } returns SourcesDir(sourcesDir)

        val response = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject { }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text
        assertTrue(result!!.contains("Sources last refreshed at"))
        assertTrue(result.contains("(1 hour(s) ago)"))
    }

    @Test
    fun `read_dependency_sources passes fresh parameter`() = runTest {
        val sourcesDir = tempDir.resolve("sources-fresh")
        sourcesDir.createDirectories()

        coEvery {
            sourcesService.downloadAllSources(any(), any(), any(), fresh = true)
        } returns SourcesDir(sourcesDir)

        server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("fresh", true)
            }
        ) as CallToolResult
    }
}
