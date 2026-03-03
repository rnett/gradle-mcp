package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.maven.MAVEN_CENTRAL_URL
import dev.rnett.gradle.mcp.maven.MavenCentralSearchResponse
import dev.rnett.gradle.mcp.maven.MavenCentralService
import dev.rnett.gradle.mcp.maven.MavenRepoService
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import io.mockk.coEvery
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.BeforeTest
import kotlin.test.Test

class DependencySearchToolsTest : BaseMcpServerTest() {

    private lateinit var mavenRepoService: MavenRepoService
    private lateinit var mavenCentralService: MavenCentralService

    @BeforeTest
    fun setupTest() {
        mavenRepoService = server.koin.get()
        mavenCentralService = server.koin.get()
    }

    @Test
    fun `search_maven_central returns artifacts with all versions`() = runTest {
        val searchResponse = MavenCentralSearchResponse.Response(
            numFound = 1,
            start = 0,
            docs = listOf(
                MavenCentralSearchResponse.ArtifactResult(
                    groupId = "org.jetbrains.kotlin",
                    artifactId = "kotlin-stdlib",
                    latestVersion = "2.0.0",
                    classifier = "jar"
                )
            )
        )

        coEvery {
            mavenCentralService.searchCentral("kotlin-stdlib", 0, 20)
        } returns searchResponse

        coEvery {
            mavenRepoService.getVersions(MAVEN_CENTRAL_URL, "org.jetbrains.kotlin", "kotlin-stdlib")
        } returns listOf("1.9.0", "1.9.20", "2.0.0")

        val response = server.client.callTool(
            "search_maven_central", buildJsonObject {
                put("query", "kotlin-stdlib")
            }
        ) as CallToolResult

        // The tool returns JSON string for SearchMavenCentralOutput because it's a data class
        val resultText = (response.content.first() as io.modelcontextprotocol.kotlin.sdk.TextContent).text!!

        // We expect the versions to be reversed in the output
        val expectedPart = "\"versions\":[\"2.0.0\",\"1.9.20\",\"1.9.0\"]"
        assert(resultText.contains(expectedPart))
        assert(resultText.contains("\"groupId\":\"org.jetbrains.kotlin\""))
        assert(resultText.contains("\"artifactId\":\"kotlin-stdlib\""))
    }

    @Test
    fun `search_maven_versions returns versions latest first and supports pagination`() = runTest {
        coEvery {
            mavenRepoService.getVersions(MAVEN_CENTRAL_URL, "org.jetbrains.kotlin", "kotlin-stdlib")
        } returns listOf("1.9.0", "1.9.20", "2.0.0")

        val response = server.client.callTool(
            "search_maven_versions", buildJsonObject {
                put("group", "org.jetbrains.kotlin")
                put("artifact", "kotlin-stdlib")
                put("limit", 2)
                put("offset", 0)
            }
        ) as CallToolResult

        val resultText = (response.content.first() as io.modelcontextprotocol.kotlin.sdk.TextContent).text!!

        // Latest first: 2.0.0, 1.9.20, 1.9.0. Limit 2, Offset 0 -> 2.0.0, 1.9.20
        val expectedVersions = "\"versions\":[\"2.0.0\",\"1.9.20\"]"
        assert(resultText.contains(expectedVersions))
        assert(resultText.contains("\"totalVersions\":3"))
    }

    @Test
    fun `search_maven_versions combines versions from multiple repositories`() = runTest {
        val repo1 = "https://repo1.maven.org/maven2/"
        val repo2 = "https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven"

        coEvery {
            mavenRepoService.getVersions(repo1, "test", "artifact")
        } returns listOf("1.0.0")

        coEvery {
            mavenRepoService.getVersions(repo2, "test", "artifact")
        } returns listOf("1.1.0")

        val response = server.client.callTool(
            "search_maven_versions", buildJsonObject {
                put("group", "test")
                put("artifact", "artifact")
                putJsonArray("repositories") {
                    add(repo1)
                    add(repo2)
                }
            }
        ) as CallToolResult

        val resultText = (response.content.first() as io.modelcontextprotocol.kotlin.sdk.TextContent).text!!

        // Combined and reversed: 1.1.0, 1.0.0
        assert(resultText.contains("\"versions\":[\"1.1.0\",\"1.0.0\"]"))
    }
}
