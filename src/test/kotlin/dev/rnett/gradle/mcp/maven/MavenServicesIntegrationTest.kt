package dev.rnett.gradle.mcp.maven

import dev.rnett.gradle.mcp.DI
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue


class MavenServicesIntegrationTest {

    private val client = DI.createHttpClient()

    private val mavenService = DefaultMavenRepoService(client)

    @AfterEach
    fun cleanup() {
        client.close()
    }

    @Test
    fun `DefaultMavenCentralService searchCentral builds request and parses response`() = runTest {
        val mockClient = HttpClient(MockEngine { request ->
            assertEquals("search.maven.org", request.url.host)
            assertEquals("/solrsearch/select", request.url.encodedPath)
            assertEquals("kotlin-stdlib", request.url.parameters["q"])
            assertEquals("10", request.url.parameters["rows"])
            assertEquals("0", request.url.parameters["start"])
            assertEquals("json", request.url.parameters["wt"])

            respond(
                content = """
                    {
                      "response": {
                        "numFound": 1,
                        "start": 0,
                        "docs": [
                          {
                            "g": "org.jetbrains.kotlin",
                            "a": "kotlin-stdlib",
                            "latestVersion": "2.2.20",
                            "p": "jar"
                          }
                        ]
                      }
                    }
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) {
                json(DI.json)
            }
        }

        val response = try {
            DefaultMavenCentralService(mockClient).searchCentral("kotlin-stdlib", 0, 10)
        } finally {
            mockClient.close()
        }

        assertTrue(response.numFound > 0, "Expected at least one result for 'kotlin-stdlib'")
        val artifactIds = response.docs.map { it.artifactId }
        assertContains(artifactIds, "kotlin-stdlib")

        val stdlib = response.docs.find { it.artifactId == "kotlin-stdlib" }!!
        assertEquals("org.jetbrains.kotlin", stdlib.groupId)
    }

    @Test
    fun `DefaultMavenService getVersions returns real versions`() = runTest {
        val versions = mavenService.getVersions(MAVEN_CENTRAL_URL, "org.jetbrains.kotlin", "kotlin-stdlib")

        assertTrue(versions.isNotEmpty(), "Expected versions for 'kotlin-stdlib'")
        assertContains(versions, "2.0.0")
        assertContains(versions, "1.9.0")
    }
}
