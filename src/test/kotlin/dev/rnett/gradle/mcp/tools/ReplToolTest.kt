package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.GradleResult
import dev.rnett.gradle.mcp.gradle.RunningBuild
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplToolTest : BaseMcpServerTest() {

    @BeforeEach
    fun setupTest() = runTest {
        server.setServerRoots(Root(tempDir.toUri().toString(), "root"))
    }

    @Test
    fun `start command with projectRoot works`() = runTest {
        val projectPath = ":app"
        val sourceSet = "main"
        val javaExec = "java"
        val customRoot = tempDir.resolve("custom")
        java.io.File(customRoot.toUri()).mkdirs()
        java.io.File(customRoot.resolve("gradlew").toUri()).createNewFile()

        server.setServerRoots(Root(tempDir.toUri().toString(), "root"))

        val consoleOutput = """
            [gradle-mcp-repl-env] classpath=cp1;cp2
            [gradle-mcp-repl-env] javaExecutable=$javaExec
        """.trimIndent()

        val buildResult = mockk<BuildResult>(relaxed = true)
        every { buildResult.isSuccessful } returns true
        every { buildResult.id } returns BuildId.newId()
        every { buildResult.consoleOutput } returns consoleOutput

        val runningBuild = mockk<RunningBuild<Unit>>(relaxed = true)
        coEvery { runningBuild.awaitFinished() } returns GradleResult(buildResult, Result.success(Unit))

        every { provider.runBuild(any(), any(), any()) } returns runningBuild

        val response = server.client.callTool(
            "repl", mapOf(
                "command" to "start",
                "projectRoot" to customRoot.toString(),
                "projectPath" to projectPath,
                "sourceSet" to sourceSet
            )
        ) as CallToolResult

        val text = (response.content.first() as TextContent).text
        assertTrue(text!!.startsWith("REPL session started with ID: "), "Expected start message, got: $text")

        // Verify provider was called with the custom root
        io.mockk.verify { provider.runBuild(GradleProjectRoot(customRoot.toString()), any(), any()) }
    }

    @Test
    fun `start command runs build and starts session`() = runTest {
        val projectPath = ":app"
        val sourceSet = "main"
        val javaExec = "java"

        val consoleOutput = """
            [gradle-mcp-repl-env] classpath=cp1;cp2
            [gradle-mcp-repl-env] javaExecutable=$javaExec
            [gradle-mcp-repl-env] compilerPlugins=p1;p2
            [gradle-mcp-repl-env] compilerArgs=a1;a2
        """.trimIndent()

        val buildResult = mockk<BuildResult>(relaxed = true)
        every { buildResult.isSuccessful } returns true
        every { buildResult.id } returns BuildId.newId()
        every { buildResult.consoleOutput } returns consoleOutput

        val runningBuild = mockk<RunningBuild<Unit>>(relaxed = true)
        coEvery { runningBuild.awaitFinished() } returns GradleResult(buildResult, Result.success(Unit))

        every { provider.runBuild(any(), any(), any()) } returns runningBuild

        val response = server.client.callTool(
            "repl", mapOf(
                "command" to "start",
                "projectPath" to projectPath,
                "sourceSet" to sourceSet
            )
        ) as CallToolResult

        val text = (response.content.first() as TextContent).text
        assertTrue(text!!.startsWith("REPL session started with ID: "), "Expected start message, got: $text")
    }

    @Test
    fun `stop command works`() = runTest {
        // Start a session first (we need to mock the build again)
        val javaExec = "java"
        val consoleOutput = "[gradle-mcp-repl-env] javaExecutable=$javaExec"
        val buildResult = mockk<BuildResult>(relaxed = true)
        every { buildResult.isSuccessful } returns true
        every { buildResult.id } returns BuildId.newId()
        every { buildResult.consoleOutput } returns consoleOutput

        val runningBuild = mockk<RunningBuild<Unit>>(relaxed = true)
        coEvery { runningBuild.awaitFinished() } returns GradleResult(buildResult, Result.success(Unit))

        every { provider.runBuild(any(), any(), any()) } returns runningBuild

        server.client.callTool(
            "repl", mapOf(
                "command" to "start",
                "projectPath" to ":app",
                "sourceSet" to "main"
            )
        )

        val response = server.client.callTool("repl", mapOf("command" to "stop")) as CallToolResult
        assertEquals("REPL session stopped.", (response.content.first() as TextContent).text)
    }

    @Test
    fun `snippet without session returns error`() = runTest {
        val response = server.client.callTool(
            "repl", mapOf(
                "command" to "run",
                "code" to "val x = 1"
            )
        ) as CallToolResult
        assertTrue(response.isError == true)
        val text = (response.content.first() as TextContent).text
        assertEquals("No active REPL session. Start one with command 'start'.", text)
    }
}
