package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.fixtures.gradle.GradleProjectFixture
import dev.rnett.gradle.mcp.fixtures.gradle.testKotlinProject
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.GradleProvider
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.scope.Scope
import kotlin.test.assertContains
import kotlin.test.assertTrue

class GradleExecutionToolTest : BaseMcpServerTest() {

    private lateinit var _project: GradleProjectFixture

    override fun Scope.createProvider(): GradleProvider {
        return DefaultGradleProvider(
            config = get(),
            buildManager = get()
        )
    }

    @BeforeEach
    override fun setup() = runTest {
        _project = testKotlinProject()
        super.setup()
        server.setServerRoots(Root(_project.path().toUri().toString(), "root"))
    }

    @AfterEach
    override fun cleanup() = runTest {
        _project.close()
        super.cleanup()
    }

    @Test
    fun `gradle --version works with real project`() = runTest {
        val args = mapOf("commandLine" to JsonArray(listOf(JsonPrimitive("--version"))))
        val call = server.client.callTool(ToolNames.GRADLE, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        // Should contain Gradle version info
        assertContains(text, "Gradle")
        assertTrue(call.isError != true, "Call should not be an error, but was: $text")
    }

    @Test
    fun `gradle --help works with real project`() = runTest {
        val args = mapOf("commandLine" to JsonArray(listOf(JsonPrimitive("--help"))))
        val call = server.client.callTool(ToolNames.GRADLE, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        // Should contain Gradle help text
        assertContains(text, "USAGE: gradle")
        assertTrue(call.isError != true, "Call should not be an error, but was: $text")
    }

    @Test
    fun `gradle -v works with real project`() = runTest {
        val args = mapOf("commandLine" to JsonArray(listOf(JsonPrimitive("-v"))))
        val call = server.client.callTool(ToolNames.GRADLE, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertContains(text, "Gradle")
        assertTrue(call.isError != true, "Call should not be an error, but was: $text")
    }

    @Test
    fun `gradle -h works with real project`() = runTest {
        val args = mapOf("commandLine" to JsonArray(listOf(JsonPrimitive("-h"))))
        val call = server.client.callTool(ToolNames.GRADLE, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertContains(text, "USAGE: gradle")
        assertTrue(call.isError != true, "Call should not be an error, but was: $text")
    }
}
