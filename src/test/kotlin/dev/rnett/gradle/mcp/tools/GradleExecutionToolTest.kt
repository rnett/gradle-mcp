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
    fun `run_single_task_and_get_output --version works with real project`() = runTest {
        val args = mapOf("taskPath" to JsonPrimitive("--version"))
        val call = server.client.callTool(ToolNames.RUN_SINGLE_TASK_AND_GET_OUTPUT, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        // Should contain Gradle version info
        assertContains(text, "Gradle")
        assertContains(text, "Kotlin:")
        assertContains(text, "Launcher JVM:")
        assertTrue(call.isError != true, "Call should not be an error, but was: $text")
    }

    @Test
    fun `run_single_task_and_get_output --help works with real project`() = runTest {
        val args = mapOf("taskPath" to JsonPrimitive("--help"))
        val call = server.client.callTool(ToolNames.RUN_SINGLE_TASK_AND_GET_OUTPUT, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        // Should contain Gradle help text
        assertContains(text, "USAGE: gradle [option...] [task...]")
        assertTrue(call.isError != true, "Call should not be an error, but was: $text")
    }
}
