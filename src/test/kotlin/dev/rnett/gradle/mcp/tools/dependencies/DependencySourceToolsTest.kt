package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.SourceIndexService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.NestedPackageContents
import dev.rnett.gradle.mcp.dependencies.search.PackageContents
import dev.rnett.gradle.mcp.dependencies.search.SearchResponse
import dev.rnett.gradle.mcp.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.dependencies.search.SubPackageContents
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.tools.ToolNames
import io.mockk.coEvery
import io.mockk.coVerify
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
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
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

        coEvery { with(any<ProgressReporter>()) { sourcesService.resolveAndProcessProjectSources(any(), any(), any(), any(), any(), any()) } } returns mockSources

        server.setServerRoots(Root(tempDir.toUri().toString(), "root"))
    }

    private fun resultText(result: CallToolResult): String =
        (result.content.first() as TextContent).text!!

    // ─── read_dependency_sources ───────────────────────────────────────────────

    @Test
    fun `read_dependency_sources success path includes Sources root header`() = runTest {
        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("projectPath", ":")
            }
        ) as CallToolResult

        assertContains(resultText(result), "Sources root:")
        assertContains(resultText(result), "Sources have not been refreshed yet.")
    }

    @Test
    fun `read_dependency_sources invalid path error includes Sources root header`() = runTest {
        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("path", "../../etc/passwd")
                put("projectPath", ":")
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
                put("projectPath", ":")
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
                put("projectPath", ":")
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
                put("projectPath", ":")
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
                put("projectPath", ":")
            }
        ) as CallToolResult

        // Should not expose internal error branch (dead code removed)
        assertFalse(result.isError == true)
    }

    // ─── walkDirectory: depth-limit annotations ────────────────────────────────

    @Test
    fun `walkDirectory annotates directories at the depth limit with item count`() = runTest {
        // Create: tempDir/level1/level2/ with 3 children
        val level1 = tempDir.resolve("level1").createDirectories()
        val level2 = level1.resolve("level2").createDirectories()
        level2.resolve("a.kt").createFile()
        level2.resolve("b.kt").createFile()
        level2.resolve("c.kt").createFile()

        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("projectPath", ":")
            }
        ) as CallToolResult

        val text = resultText(result)
        // level2 is at depth 2 (the limit for maxDepth=2), so it should get "(3 items)"
        assertContains(text, "(3 items)")
    }

    @Test
    fun `walkDirectory does NOT annotate directories within the depth limit`() = runTest {
        // Create: tempDir/level1/ with one child file
        val level1 = tempDir.resolve("level1").createDirectories()
        level1.resolve("file.kt").createFile()

        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("projectPath", ":")
            }
        ) as CallToolResult

        val text = resultText(result)
        // level1 is at depth 1 (within limit), its children will be walked — no count suffix
        assertFalse(text.contains("level1/  ("))
    }

    @Test
    fun `walkDirectory annotates empty directory at depth limit with 0 items`() = runTest {
        val level1 = tempDir.resolve("level1").createDirectories()
        level1.resolve("emptyDir").createDirectories()

        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("projectPath", ":")
            }
        ) as CallToolResult

        assertContains(resultText(result), "(0 items)")
    }

    // ─── nested package listing ────────────────────────────────────────────────

    @Test
    fun `nested package listing shows sub-packages with their symbol count`() = runTest {
        coEvery { indexService.listPackageContents(any(), any()) } returns PackageContents(
            symbols = emptyList(),
            subPackages = listOf("collections", "coroutines")
        )
        coEvery { indexService.listNestedPackageContents(any(), any()) } returns NestedPackageContents(
            symbols = emptyList(),
            subPackages = listOf(
                SubPackageContents(name = "collections", symbols = listOf("List", "Map", "Set"), subPackages = emptyList()),
                SubPackageContents(name = "coroutines", symbols = listOf("launch", "async"), subPackages = emptyList())
            )
        )

        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("path", "kotlin.stdlib")
                put("projectPath", ":")
            }
        ) as CallToolResult

        val text = resultText(result)
        assertContains(text, "collections/  (3 symbols)")
        assertContains(text, "coroutines/  (2 symbols)")
    }

    @Test
    fun `nested package listing shows sub-package count for entries with no direct symbols`() = runTest {
        coEvery { indexService.listPackageContents(any(), any()) } returns PackageContents(
            symbols = emptyList(),
            subPackages = listOf("io")
        )
        coEvery { indexService.listNestedPackageContents(any(), any()) } returns NestedPackageContents(
            symbols = emptyList(),
            subPackages = listOf(
                SubPackageContents(name = "io", symbols = emptyList(), subPackages = listOf("ktor", "okhttp"))
            )
        )

        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("path", "kotlin.stdlib")
                put("projectPath", ":")
            }
        ) as CallToolResult

        assertContains(resultText(result), "io/  (2 sub-packages)")
    }

    @Test
    fun `nested package listing with too many sub-packages shows flat list with note`() = runTest {
        val manySubPackages = (1..31).map { "pkg$it" }
        coEvery { indexService.listPackageContents(any(), any()) } returns PackageContents(
            symbols = emptyList(),
            subPackages = manySubPackages
        )
        coEvery { indexService.listNestedPackageContents(any(), any()) } returns NestedPackageContents(
            symbols = emptyList(),
            subPackages = manySubPackages.map { SubPackageContents(name = it, symbols = emptyList(), subPackages = emptyList()) },
            tooManySubPackages = true
        )

        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("path", "kotlin.stdlib")
                put("projectPath", ":")
            }
        ) as CallToolResult

        assertContains(resultText(result), "too many sub-packages to expand")
    }

    // ─── JDK source auto-inclusion ────────────────────────────────────────────

    @Test
    fun `read_dependency_sources can read JDK sources from reserved session-view path`() = runTest {
        val jdkFile = tempDir.resolve("jdk/sources/java.base/java/lang/String.java")
        jdkFile.parent.createDirectories()
        jdkFile.writeText("package java.lang;\npublic class String {}")

        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("path", "jdk/sources/java.base/java/lang/String.java")
                put("projectPath", ":")
            }
        ) as CallToolResult

        val text = resultText(result)
        assertContains(text, "package java.lang;")
    }

    @Test
    fun `search includes JDK sources in unified index`() = runTest {
        coEvery { indexService.search(any(), any(), any(), any()) } returns SearchResponse<SearchResult>(
            results = listOf(
                SearchResult(
                    relativePath = "com/example/DepClass.kt",
                    file = tempDir.resolve("com/example/DepClass.kt"),
                    line = 1,
                    snippet = "class DepClass",
                    score = 1.0f,
                    matchLines = listOf(1)
                ),
                SearchResult(
                    relativePath = "jdk/sources/java.base/java/util/List.java",
                    file = tempDir.resolve("jdk/sources/java.base/java/util/List.java"),
                    line = 1,
                    snippet = "public interface List",
                    score = 1.0f,
                    matchLines = listOf(1)
                )
            )
        )

        val result = server.client.callTool(
            ToolNames.SEARCH_DEPENDENCY_SOURCES, buildJsonObject {
                put("query", "List")
                put("projectPath", ":")
            }
        ) as CallToolResult

        val text = resultText(result)
        assertContains(text, "DepClass")
        assertContains(text, "List")
    }

    @Test
    fun `gradle own source skips dependency source resolution`() = runTest {
        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("gradleOwnSource", true)
            }
        ) as CallToolResult

        val text = resultText(result)
        assertContains(text, "Sources root:")
        coVerify(exactly = 0) {
            with(any<ProgressReporter>()) { sourcesService.resolveAndProcessProjectSources(any(), any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `JDK sources missing gracefully skips`() = runTest {
        val result = server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES, buildJsonObject {
                put("projectPath", ":")
            }
        ) as CallToolResult

        val text = resultText(result)
        assertContains(text, "Sources root:")
    }
}
