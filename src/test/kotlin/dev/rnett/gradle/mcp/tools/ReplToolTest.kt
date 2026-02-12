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
            ToolNames.REPL, mapOf(
                "command" to "start",
                "projectRoot" to customRoot.toString(),
                "projectPath" to projectPath,
                "sourceSet" to sourceSet
            )
        ) as CallToolResult

        val text = (response.content.first() as TextContent).text
        assert(text!!.startsWith("REPL session started with ID: "))

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
            [gradle-mcp-repl-env] compilerClasspath=p1;p2
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
            ToolNames.REPL, mapOf(
                "command" to "start",
                "projectPath" to projectPath,
                "sourceSet" to sourceSet
            )
        ) as CallToolResult

        val text = (response.content.first() as TextContent).text
        assert(text!!.startsWith("REPL session started with ID: "))
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
            ToolNames.REPL, mapOf(
                "command" to "start",
                "projectPath" to ":app",
                "sourceSet" to "main"
            )
        )

        val response = server.client.callTool(ToolNames.REPL, mapOf("command" to "stop")) as CallToolResult
        assert((response.content.first() as TextContent).text == "REPL session stopped.")
    }

    @Test
    fun `snippet without session returns error`() = runTest {
        val response = server.client.callTool(
            ToolNames.REPL, mapOf(
                "command" to "run",
                "code" to "val x = 1"
            )
        ) as CallToolResult
        assert(response.isError == true)
        val text = (response.content.first() as TextContent).text
        assert(text == "No active REPL session. Start one with command 'start'.")
    }

    @Test
    fun `start command with missing javaExecutable returns graceful error`() = runTest {
        val projectPath = ":app"
        val sourceSet = "main"

        val consoleOutput = """
            [gradle-mcp-repl-env] projectRoot=C:\path\to\project
            [gradle-mcp-repl-env] classpath=cp1;cp2
        """.trimIndent()

        val buildResult = mockk<BuildResult>(relaxed = true)
        every { buildResult.isSuccessful } returns true
        every { buildResult.id } returns BuildId.newId()
        every { buildResult.consoleOutput } returns consoleOutput

        val runningBuild = mockk<RunningBuild<Unit>>(relaxed = true)
        coEvery { runningBuild.awaitFinished() } returns GradleResult(buildResult, Result.success(Unit))

        every { provider.runBuild(any(), any(), any()) } returns runningBuild

        val response = server.client.callTool(
            ToolNames.REPL, mapOf(
                "command" to "start",
                "projectPath" to projectPath,
                "sourceSet" to sourceSet
            )
        ) as CallToolResult

        assert(response.isError == true)
        val text = (response.content.first() as TextContent).text
        assert(text!!.contains("No JVM target available"))
    }

    @Test
    fun `start command with non-jvm source set returns graceful error`() = runTest {
        val projectPath = ":app"
        val sourceSet = "commonMain"

        val buildResult = mockk<BuildResult>(relaxed = true)
        every { buildResult.isSuccessful } returns false
        every { buildResult.id } returns BuildId.newId()
        // Simulate the error message from repl-env.init.gradle.kts
        every { buildResult.toOutputString() } returns "FAILURE: Build failed with an exception.\n\n* What went wrong:\nExecution failed for task ':app:resolveReplEnvironment'.\n> SourceSet 'commonMain' found in project ':app', but it does not appear to be a JVM source set. REPL is only supported for JVM source sets."

        val runningBuild = mockk<RunningBuild<Unit>>(relaxed = true)
        coEvery { runningBuild.awaitFinished() } returns GradleResult(buildResult, Result.success(Unit))

        every { provider.runBuild(any(), any(), any()) } returns runningBuild

        val response = server.client.callTool(
            ToolNames.REPL, mapOf(
                "command" to "start",
                "projectPath" to projectPath,
                "sourceSet" to sourceSet
            )
        ) as CallToolResult

        assert(response.isError == true)
        val text = (response.content.first() as TextContent).text
        assert(text!!.contains("Failed to resolve REPL environment because Gradle task failed"))
        assert(text!!.contains("does not appear to be a JVM source set"))
    }
}
