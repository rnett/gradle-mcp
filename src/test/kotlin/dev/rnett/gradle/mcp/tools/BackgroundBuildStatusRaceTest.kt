package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.BuildStatus
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.gradle.build.TestResults
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.gradle.tooling.CancellationTokenSource
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class BackgroundBuildStatusRaceTest : BaseMcpServerTest() {

    @Test
    fun `handles race condition when build completes`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = BuildId.newId()

        val runningBuild = mockk<RunningBuild>(relaxed = true) {
            every { id } returns buildId
            every { status } returns BuildStatus.Running
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { logBuffer } returns StringBuffer("Started\n")
            every { stop() } returns Unit
            every { cancellationTokenSource } returns mockk<CancellationTokenSource>()
            every { logLines } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTasks } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTaskPaths } returns emptySet()
            every { consoleOutput } returns "Started\n"
            coEvery { awaitFinished() } returns mockk<FinishedBuild>()
        }

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        // Simulate the transition: the real logic now handles this by storeResult replacing in manager
        launch {
            delay(100.milliseconds)
            val mockFinishedBuild = FinishedBuild(
                id = buildId,
                args = GradleInvocationArguments(additionalArguments = listOf("help")),
                consoleOutput = "BUILD SUCCESSFUL",
                publishedScans = emptyList(),
                testResults = TestResults(emptySet(), emptySet(), emptySet()),
                problemAggregations = emptyMap(),
                outcome = BuildOutcome.Success,
                finishTime = Clock.System.now()
            )

            // 2. Real logic: GradleProvider calls finish then storeResult
            every { runningBuild.finish(any(), any()) } returns mockFinishedBuild
            coEvery { runningBuild.awaitFinished() } returns mockFinishedBuild
            every { runningBuild.isRunning } returns false

            buildManager.storeResult(mockFinishedBuild)

            // Ensure the manager is updated
            println("[DEBUG_LOG] Result stored: ${buildManager.getBuild(buildId)}")
        }

        // This call should now pass and wait for completion
        val statusCall = server.client.callTool(
            ToolNames.BACKGROUND_BUILD_GET_STATUS, mapOf(
                "buildId" to JsonPrimitive(buildId.toString()),
                "wait" to JsonPrimitive(2.0)
            )
        )

        assert(statusCall != null)
        val statusText = statusCall!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assert(statusText.contains("BUILD COMPLETED"))
    }
}
