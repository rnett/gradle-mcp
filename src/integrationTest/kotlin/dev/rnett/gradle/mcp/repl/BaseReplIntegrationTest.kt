package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.fixtures.gradle.GradleProjectFixture
import dev.rnett.gradle.mcp.fixtures.gradle.withTestGradleDefaults
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.GradleConnectionService
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.build.BuildExecutionService
import dev.rnett.gradle.mcp.tools.ToolNames
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.koin.core.scope.Scope
import java.nio.file.Files
import kotlin.test.assertTrue
import kotlin.test.fail

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseReplIntegrationTest : BaseMcpServerTest() {

    protected lateinit var project: GradleProjectFixture

    @BeforeAll
    fun setupAll() {
        tempDir = Files.createTempDirectory("gradle-mcp-test")
        super.setup()
    }

    @AfterAll
    fun cleanupAll() {
        runBlocking {
            try {
                server.client.callTool(ToolNames.REPL, mapOf("command" to "stop"))
            } catch (e: Exception) {
                // Ignore
            }
            if (::project.isInitialized) {
                project.close()
            }
            super.cleanup()
        }
        try {
            tempDir.toFile().deleteRecursively()
        } catch (e: Exception) {
            // Ignore
        }
    }

    @BeforeEach
    override fun setup() {
        // Do nothing here, we call it in @BeforeAll
    }

    @AfterEach
    override fun cleanup() {
        // Do nothing here, we call it in @AfterAll
    }

    protected fun initProject(fixture: GradleProjectFixture) {
        project = fixture
    }

    protected suspend fun startRepl(projectPath: String = ":", sourceSet: String = "main") {
        val startResponse = server.client.callTool(
            ToolNames.REPL, mapOf(
                "command" to "start",
                "projectRoot" to project.path().toString(),
                "projectPath" to projectPath,
                "sourceSet" to sourceSet
            )
        ) as CallToolResult
        assertTrue(
            (startResponse.content.first() as TextContent).text!!.startsWith("REPL session started"),
            "Expected REPL to start, but got: ${(startResponse.content.first() as TextContent).text}"
        )
    }

    override fun Scope.createProvider(): GradleProvider {
        return DefaultGradleProvider(
            connectionService = get<GradleConnectionService>(),
            executionService = get<BuildExecutionService>(),
            buildManager = get<BuildManager>()
        ).withTestGradleDefaults()
    }


    protected suspend fun runSnippetAndAssertImage(code: String, resourceName: String) {
        val response = server.client.callTool(
            ToolNames.REPL, mapOf(
                "command" to "run",
                "code" to code
            )
        ) as CallToolResult
        assert(!response.isError!!) { "Snippet failed: ${response.content.joinToString { if (it is TextContent) it.text!! else "Image(${it})" }}" }

        val imageContent = response.content.filterIsInstance<ImageContent>().firstOrNull()
            ?: fail("Expected image content in response, but got: ${response.content}")

        ImageAssert.assertImage(imageContent.data, resourceName)
    }

    protected suspend fun runSnippet(code: String): String {
        val response = server.client.callTool(
            ToolNames.REPL, mapOf(
                "command" to "run",
                "code" to code
            )
        ) as CallToolResult
        assert(!response.isError!!) { "Snippet failed: ${response.content.joinToString { if (it is TextContent) it.text!! else "Image(${it})" }}" }

        return response.content.joinToString("\n") {
            when (it) {
                is TextContent -> it.text!!
                is ImageContent -> "Image(${it.mimeType})"
                else -> it.toString()
            }
        }
    }
}
