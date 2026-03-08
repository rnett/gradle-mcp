package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DocsSectionSummary
import dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsService
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import io.mockk.coEvery
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains

class GradleDocsVersionDetectionTest : BaseMcpServerTest() {

    val mockDocsService: GradleDocsService get() = server.koin.get()
    val mockVersionService: GradleVersionService get() = server.koin.get()

    @BeforeTest
    override fun setup() = runTest {
        super.setup()
        coEvery { mockVersionService.resolveVersion(any()) } answers {
            val v = it.invocation.args[0] as? String
            if (v == null || v == "current") "resolved-current" else v
        }
    }

    @Test
    fun `test version detection from gradle-wrapper properties`() = runTest {
        val projectRoot = tempDir.resolve("test-project")
        projectRoot.createDirectories()
        val wrapperDir = projectRoot.resolve("gradle/wrapper")
        wrapperDir.createDirectories()
        wrapperDir.resolve("gradle-wrapper.properties").writeText(
            "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.5-bin.zip"
        )

        // Set as MCP root
        server.setServerRoots(Root(projectRoot.toUri().toString(), "test-project"))

        coEvery {
            with(any<dev.rnett.gradle.mcp.ProgressReporter>()) {
                mockDocsService.summarizeSections("8.5")
            }
        } returns listOf(
            DocsSectionSummary("userguide", "User Guide", 42)
        )

        // Call tool without version
        val args = emptyMap<String, kotlinx.serialization.json.JsonElement>()
        val call = server.client.callTool(ToolNames.GRADLE_DOCS, args)

        val text = (call!!.content[0] as TextContent).text ?: ""
        assertContains(text, "Documentation sections for Gradle 8.5:")
        assertContains(text, "- **User Guide** (`tag:userguide`): 42 pages")
    }

    @Test
    fun `test fallback to current when no project root or detection fails`() = runTest {
        coEvery {
            with(any<dev.rnett.gradle.mcp.ProgressReporter>()) {
                mockDocsService.summarizeSections("resolved-current")
            }
        } returns listOf(
            DocsSectionSummary("userguide", "User Guide", 10)
        )

        // No MCP roots set, no version provided
        val args = emptyMap<String, kotlinx.serialization.json.JsonElement>()
        val call = server.client.callTool(ToolNames.GRADLE_DOCS, args)

        val text = (call!!.content[0] as TextContent).text ?: ""
        assertContains(text, "Documentation sections for Gradle resolved-current:")
        assertContains(text, "- **User Guide** (`tag:userguide`): 10 pages")
    }

    @Test
    fun `test version detection from explicit project root`() = runTest {
        val projectRoot = tempDir.resolve("explicit-project")
        projectRoot.createDirectories()
        val wrapperDir = projectRoot.resolve("gradle/wrapper")
        wrapperDir.createDirectories()
        wrapperDir.resolve("gradle-wrapper.properties").writeText(
            "distributionUrl=https\\://services.gradle.org/distributions/gradle-7.6.3-bin.zip"
        )

        // Set as MCP root so it's valid
        server.setServerRoots(Root(projectRoot.toUri().toString(), "explicit-project"))

        coEvery {
            with(any<dev.rnett.gradle.mcp.ProgressReporter>()) {
                mockDocsService.summarizeSections("7.6.3")
            }
        } returns listOf(
            DocsSectionSummary("userguide", "User Guide", 5)
        )

        // Call tool with projectRoot
        val args = mapOf("projectRoot" to kotlinx.serialization.json.JsonPrimitive(projectRoot.toString()))
        val call = server.client.callTool(ToolNames.GRADLE_DOCS, args)

        val text = (call!!.content[0] as TextContent).text ?: ""
        assertContains(text, "Documentation sections for Gradle 7.6.3:")
        assertContains(text, "- **User Guide** (`tag:userguide`): 5 pages")
    }
}
