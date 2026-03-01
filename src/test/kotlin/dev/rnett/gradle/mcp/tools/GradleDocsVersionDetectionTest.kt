package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.DocsPage
import dev.rnett.gradle.mcp.GradleDocsService
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

    @BeforeTest
    override fun setup() = runTest {
        super.setup()
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

        coEvery { mockDocsService.getAllDocsPages("8.5") } returns listOf(
            DocsPage("Test Page", "test.html")
        )

        // Call tool without version
        val args = emptyMap<String, kotlinx.serialization.json.JsonElement>()
        val call = server.client.callTool(ToolNames.GET_ALL_GRADLE_DOCS_PAGES, args)

        val text = (call!!.content[0] as TextContent).text ?: ""
        assertContains(text, "Available documentation pages for Gradle 8.5:")
        assertContains(text, "- Test Page: `test.html`")
    }

    @Test
    fun `test fallback to current when no project root or detection fails`() = runTest {
        coEvery { mockDocsService.getAllDocsPages(null) } returns listOf(
            DocsPage("Current Page", "current.html")
        )

        // No MCP roots set, no version provided
        val args = emptyMap<String, kotlinx.serialization.json.JsonElement>()
        val call = server.client.callTool(ToolNames.GET_ALL_GRADLE_DOCS_PAGES, args)

        val text = (call!!.content[0] as TextContent).text ?: ""
        assertContains(text, "Available documentation pages for Gradle current:")
        assertContains(text, "- Current Page: `current.html`")
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

        coEvery { mockDocsService.getAllDocsPages("7.6.3") } returns listOf(
            DocsPage("Explicit Page", "explicit.html")
        )

        // Call tool with projectRoot
        val args = mapOf("projectRoot" to kotlinx.serialization.json.JsonPrimitive(projectRoot.toString()))
        val call = server.client.callTool(ToolNames.GET_ALL_GRADLE_DOCS_PAGES, args)

        val text = (call!!.content[0] as TextContent).text ?: ""
        assertContains(text, "Available documentation pages for Gradle 7.6.3:")
        assertContains(text, "- Explicit Page: `explicit.html`")
    }
}
