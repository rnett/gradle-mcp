package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.fixtures.GradleProjectFixture
import dev.rnett.gradle.mcp.gradle.fixtures.testKotlinProject
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.koin.core.scope.Scope
import kotlin.io.path.createDirectories
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class GradleExecutionToolTest : BaseMcpServerTest() {

    private lateinit var _project: GradleProjectFixture

    override fun Scope.createProvider(): GradleProvider {
        return DefaultGradleProvider(
            config = get(),
            initScriptProvider = DefaultInitScriptProvider(tempDir.resolve("init-scripts").createDirectories()),
            buildManager = get()
        )
    }

    @BeforeTest
    override fun setup() = runTest {
        _project = testKotlinProject()
        super.setup()
        server.setServerRoots(Root(_project.path().toUri().toString(), "root"))
    }

    @AfterTest
    override fun cleanup() = runTest {
        _project.close()
        super.cleanup()
    }

    @Test
    fun `gradlew --version works with real project`() = runTest {
        val args = mapOf("commandLine" to JsonArray(listOf(JsonPrimitive("--version"))))
        val call = server.client.callTool(ToolNames.GRADLEW, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        // Should contain Gradle version info
        assertContains(text, "Gradle")
        assertTrue(call.isError != true, "Call should not be an error, but was: $text")
    }

    @Test
    fun `gradlew --help works with real project`() = runTest {
        val args = mapOf("commandLine" to JsonArray(listOf(JsonPrimitive("--help"))))
        val call = server.client.callTool(ToolNames.GRADLEW, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        // Should contain Gradle help text
        assertContains(text, "USAGE: gradle")
        assertTrue(call.isError != true, "Call should not be an error, but was: $text")
    }

    @Test
    fun `gradlew -v works with real project`() = runTest {
        val args = mapOf("commandLine" to JsonArray(listOf(JsonPrimitive("-v"))))
        val call = server.client.callTool(ToolNames.GRADLEW, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertContains(text, "Gradle")
        assertTrue(call.isError != true, "Call should not be an error, but was: $text")
    }

    @Test
    fun `gradlew -h works with real project`() = runTest {
        val args = mapOf("commandLine" to JsonArray(listOf(JsonPrimitive("-h"))))
        val call = server.client.callTool(ToolNames.GRADLEW, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertContains(text, "USAGE: gradle")
        assertTrue(call.isError != true, "Call should not be an error, but was: $text")
    }
}
