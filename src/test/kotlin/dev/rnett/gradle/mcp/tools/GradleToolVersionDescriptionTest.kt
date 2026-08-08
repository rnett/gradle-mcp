package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.LatestStableGradleVersion
import dev.rnett.gradle.mcp.LatestStableGradleVersion.Source
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GradleToolVersionDescriptionTest {

    private fun descriptionFor(server: Server, toolName: String): String =
        server.tools[toolName]?.tool?.description.orEmpty()

    @Test
    fun `gradle_docs tool description contains no version statement or hard-coded version`() {
        val docsTools = GradleDocsTools(mockk(relaxed = true), mockk(relaxed = true))
        val server = DI.createServer(DI.json, listOf(docsTools))

        val description = descriptionFor(server, ToolNames.GRADLE_DOCS)

        assertFalse(description.contains("latest Gradle version"), "GRADLE_DOCS description should not state the latest Gradle version")
        assertFalse(description.contains("latest stable Gradle version"), "GRADLE_DOCS description should not reference the latest stable version")
        assertFalse(description.contains("https://services.gradle.org/versions/current"), "GRADLE_DOCS description should not reference the version-check endpoint")
        assertFalse(description.contains(BuildConfig.GRADLE_VERSION), "GRADLE_DOCS description should not hard-code the bundled Gradle version")
    }

    @Test
    fun `gradle tool description does not include the latest stable version statement`() {
        val executionTools = GradleExecutionTools(mockk(relaxed = true))
        val server = DI.createServer(DI.json, listOf(executionTools))

        val description = descriptionFor(server, ToolNames.GRADLE)

        assertFalse(description.contains("latest stable Gradle version"), "GRADLE description should not include the latest stable version statement")
        assertFalse(description.contains(BuildConfig.GRADLE_VERSION), "GRADLE description should not hard-code the bundled Gradle version")
    }

    @Test
    fun `instructions line truthfully describes a live resolution`() {
        val line = latestStableGradleVersionInstructionsLine(
            LatestStableGradleVersion("9.99.99", Source.FETCHED_LIVE)
        )

        assertEquals("The latest Gradle version is **9.99.99**.", line)
    }

    @Test
    fun `instructions line identifies the bundled version on fallback`() {
        val line = latestStableGradleVersionInstructionsLine(
            LatestStableGradleVersion("8.88.88", Source.BUNDLED_FALLBACK)
        )

        assertEquals("The latest Gradle version is **8.88.88** (this server's bundled Gradle version).", line)
    }

    @Test
    fun `server instructions contain the injected latest version line`() = runTest {
        val sentinelLine = "<SENTINEL 9.99.99>"
        val server = DI.createServer(
            DI.json,
            listOf(GradleDocsTools(mockk(relaxed = true), mockk(relaxed = true))),
            latestGradleVersionInstructions = sentinelLine
        )

        val transports = ChannelTransport.createLinkedPair()
        val client = Client(Implementation("gradle-mcp-test-client", "test"), ClientOptions())
        try {
            withContext(Dispatchers.Default) {
                server.createSession(transports.serverTransport)
                client.connect(transports.clientTransport)
            }

            val instructions = client.serverInstructions.orEmpty()
            assertContains(instructions, sentinelLine)
            assertContains(instructions, "Use `gradle_docs` for official Gradle documentation")
        } finally {
            withContext(Dispatchers.Default) {
                runCatching { client.close() }
                runCatching { server.close() }
                runCatching { transports.clientTransport.close() }
                runCatching { transports.serverTransport.close() }
            }
        }
    }
}
