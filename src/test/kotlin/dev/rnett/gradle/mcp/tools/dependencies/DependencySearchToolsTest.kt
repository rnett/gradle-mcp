package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.maven.DepsDevService
import dev.rnett.gradle.mcp.maven.DepsDevVersion
import dev.rnett.gradle.mcp.tools.ToolNames
import io.mockk.coEvery
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertFalse

class DependencySearchToolsTest : BaseMcpServerTest() {

    private lateinit var depsDevService: DepsDevService

    @BeforeEach
    fun setupTest() {
        depsDevService = server.koin.get()
    }

    private suspend fun callTool(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String {
        val response = server.client.callTool(
            ToolNames.LOOKUP_MAVEN_VERSIONS, buildJsonObject(block)
        ) as CallToolResult
        return (response.content.first() as TextContent).text!!
    }

    @Test
    fun `returns versions sorted most-recent first with publish dates`() = runTest {
        coEvery { depsDevService.getMavenVersions("org.jetbrains.kotlin", "kotlin-stdlib") } returns listOf(
            DepsDevVersion("2.0.0", LocalDate.of(2024, 5, 1)),
            DepsDevVersion("1.9.20", LocalDate.of(2023, 11, 1)),
            DepsDevVersion("1.9.0", LocalDate.of(2023, 6, 1))
        )

        val result = callTool { put("coordinates", "org.jetbrains.kotlin:kotlin-stdlib") }

        assertContains(result, "Versions for org.jetbrains.kotlin:kotlin-stdlib:")
        assertContains(result, "- 2.0.0 (2024-05-01)")
        assertContains(result, "- 1.9.20 (2023-11-01)")
        assertContains(result, "- 1.9.0 (2023-06-01)")
        // No pagination footer when all items fit within the limit
        assertFalse(result.contains("offset="), "No pagination footer expected for small result set")
    }

    @Test
    fun `default limit of 5 is applied`() = runTest {
        val versions = (1..10).map { i ->
            DepsDevVersion("1.$i.0", LocalDate.of(2024, 1, i))
        }.sortedByDescending { it.publishedAt }
        coEvery { depsDevService.getMavenVersions("com.example", "library") } returns versions

        val result = callTool { put("coordinates", "com.example:library") }

        assertContains(result, "Pagination: Showing versions 1 to 5 of 10")
        assertContains(result, "offset=5")
    }

    @Test
    fun `explicit limit overrides default`() = runTest {
        val versions = (1..10).map { i ->
            DepsDevVersion("1.$i.0", LocalDate.of(2024, 1, i))
        }.sortedByDescending { it.publishedAt }
        coEvery { depsDevService.getMavenVersions("com.example", "library") } returns versions

        val result = callTool {
            put("coordinates", "com.example:library")
            put("pagination", buildJsonObject { put("limit", 3) })
        }

        assertContains(result, "Pagination: Showing versions 1 to 3 of 10")
    }

    @Test
    fun `pagination with offset returns correct slice`() = runTest {
        val versions = (1..20).map { i ->
            DepsDevVersion("1.$i.0", LocalDate.of(2024, 1, i))
        }.sortedByDescending { it.publishedAt }
        coEvery { depsDevService.getMavenVersions("com.example", "library") } returns versions

        val result = callTool {
            put("coordinates", "com.example:library")
            put("pagination", buildJsonObject {
                put("offset", 10)
                put("limit", 10)
            })
        }

        assertContains(result, "Pagination: Showing versions 11 to 20 of 20")
    }

    @Test
    fun `returns error message for missing artifact`() = runTest {
        coEvery { depsDevService.getMavenVersions("ai.koog", "koog-agents") } returns emptyList()

        val result = callTool { put("coordinates", "ai.koog:koog-agents") }

        assertContains(result, "No versions found for ai.koog:koog-agents")
    }

    @Test
    fun `new Central Portal packages are found via deps-dev`() = runTest {
        coEvery { depsDevService.getMavenVersions("ai.koog", "koog-agents") } returns listOf(
            DepsDevVersion("0.2.0", LocalDate.of(2025, 3, 1)),
            DepsDevVersion("0.1.0", LocalDate.of(2025, 1, 1))
        )

        val result = callTool { put("coordinates", "ai.koog:koog-agents") }

        assertContains(result, "Versions for ai.koog:koog-agents:")
        assertContains(result, "- 0.2.0 (2025-03-01)")
    }

    @Test
    fun `returns format error for invalid coordinates`() = runTest {
        val result = callTool { put("coordinates", "not-a-valid-coordinate") }

        assertContains(result, "group:artifact")
    }
}
