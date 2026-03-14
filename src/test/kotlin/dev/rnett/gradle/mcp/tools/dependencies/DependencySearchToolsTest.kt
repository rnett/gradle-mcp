package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.maven.MAVEN_CENTRAL_URL
import dev.rnett.gradle.mcp.maven.MavenCentralSearchResponse
import dev.rnett.gradle.mcp.maven.MavenCentralService
import dev.rnett.gradle.mcp.maven.MavenRepoService
import dev.rnett.gradle.mcp.tools.ToolNames
import io.mockk.coEvery
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class DependencySearchToolsTest : BaseMcpServerTest() {

    private lateinit var mavenRepoService: MavenRepoService
    private lateinit var mavenCentralService: MavenCentralService

    @BeforeEach
    fun setupTest() {
        mavenRepoService = server.koin.get()
        mavenCentralService = server.koin.get()
    }

    @Test
    fun `search_maven_central returns formatted artifacts`() = runTest {
        val searchResponse = MavenCentralSearchResponse.Response(
            numFound = 1,
            start = 0,
            docs = listOf(
                MavenCentralSearchResponse.ArtifactResult(
                    groupId = "org.jetbrains.kotlin",
                    artifactId = "kotlin-stdlib",
                    latestVersion = "1.9.0", // Wrong version from search
                    classifier = "jar"
                )
            )
        )

        coEvery {
            mavenCentralService.searchCentral("kotlin-stdlib", 0, 10)
        } returns searchResponse

        coEvery {
            mavenRepoService.getVersions(MAVEN_CENTRAL_URL, "org.jetbrains.kotlin", "kotlin-stdlib")
        } returns listOf("1.9.0", "2.0.0") // Correct latest version

        val response = server.client.callTool(
            ToolNames.SEARCH_MAVEN_CENTRAL, buildJsonObject {
                put("query", "kotlin-stdlib")
            }
        ) as CallToolResult

        val resultText = (response.content.first() as io.modelcontextprotocol.kotlin.sdk.TextContent).text!!

        assertContains(resultText, "Found 1 artifacts for 'kotlin-stdlib':")
        assertContains(resultText, "- org.jetbrains.kotlin:kotlin-stdlib")
        assertContains(resultText, "Latest Version: 2.0.0") // Should use version from getVersions
    }

    @Test
    fun `search_maven_central with versions=true returns versions latest first`() = runTest {
        coEvery {
            mavenRepoService.getVersions(MAVEN_CENTRAL_URL, "org.jetbrains.kotlin", "kotlin-stdlib")
        } returns listOf("1.9.0", "1.9.20", "2.0.0")

        val response = server.client.callTool(
            ToolNames.SEARCH_MAVEN_CENTRAL, buildJsonObject {
                put("query", "org.jetbrains.kotlin:kotlin-stdlib")
                put("versions", true)
                put("limit", 2)
                put("offset", 0)
            }
        ) as CallToolResult

        val resultText = (response.content.first() as io.modelcontextprotocol.kotlin.sdk.TextContent).text!!

        assertContains(resultText, "Versions for org.jetbrains.kotlin:kotlin-stdlib (Total: 3):")
        assertContains(resultText, "- 2.0.0")
        assertContains(resultText, "- 1.9.20")
        assert(!resultText.contains("- 1.9.0"))
    }
}
