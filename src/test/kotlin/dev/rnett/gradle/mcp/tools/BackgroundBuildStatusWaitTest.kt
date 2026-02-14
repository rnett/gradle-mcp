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

class BackgroundBuildStatusWaitTest : BaseMcpServerTest() {

    @Test
    fun `background_build_get_status waits for completion`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = BuildId.newId()

        val runningBuild = mockk<RunningBuild>(relaxed = true) {
            every { id } returns buildId
            every { isRunning } returns true
            every { hasBuildFinished } returns false
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
            coEvery { awaitFinished() } returns FinishedBuild(
                id = buildId,
                args = GradleInvocationArguments(additionalArguments = listOf("help")),
                consoleOutput = "SUCCESS",
                publishedScans = emptyList(),
                testResults = TestResults(emptySet(), emptySet(), emptySet()),
                problemAggregations = emptyMap(),
                outcome = BuildOutcome.Success,
                finishTime = Clock.System.now()
            )
        }

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        val mockFinishedBuild = FinishedBuild(
            id = buildId,
            args = GradleInvocationArguments(additionalArguments = listOf("help")),
            consoleOutput = "SUCCESS",
            publishedScans = emptyList(),
            testResults = TestResults(emptySet(), emptySet(), emptySet()),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )

        every { runningBuild.finish(any()) } returns mockFinishedBuild
        coEvery { runningBuild.awaitFinished() } returns mockFinishedBuild
        every { runningBuild.isRunning } returns true
        every { runningBuild.hasBuildFinished } returns false
        every { runningBuild.status } returns BuildOutcome.Success

        every { runningBuild.isRunning } returns false
        every { runningBuild.hasBuildFinished } returns true
        buildManager.storeResult(mockFinishedBuild)

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

    @Test
    fun `background_build_get_status waits for waitFor`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = BuildId.newId()
        val logBuffer = StringBuffer("Started\n")
        val logLinesFlow = MutableSharedFlow<String>(replay = 1)

        val runningBuild = mockk<RunningBuild>(relaxed = true) {
            every { id } returns buildId
            every { status } returns BuildStatus.Running
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { this@mockk.logBuffer } returns logBuffer
            every { stop() } returns Unit
            every { cancellationTokenSource } returns mockk<CancellationTokenSource>()
            every { logLines } returns logLinesFlow.asSharedFlow()
            every { completedTasks } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTaskPaths } returns emptySet()
            every { consoleOutput } returns logBuffer.toString()
        }

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        launch {
            delay(500.milliseconds)
            val line = "Ready to go"
            logBuffer.append("$line\n")
            logLinesFlow.emit(line)
        }

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            ToolNames.BACKGROUND_BUILD_GET_STATUS, mapOf(
                "buildId" to JsonPrimitive(buildId.toString()),
                "wait" to JsonPrimitive(2.0),
                "waitFor" to JsonPrimitive("Ready")
            )
        )
        val duration = testScheduler.currentTime - startTime

        assert(statusCall != null)
        assert(duration >= 500)
        assert(duration < 2000)

        val statusText = statusCall!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assert(statusText.contains("BUILD IN PROGRESS"))
        assert(statusText.contains("Matching lines for 'Ready':"))
        assert(statusText.contains("Ready to go"))
    }

    @Test
    fun `background_build_get_status returns immediately if waitFor already matches`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = BuildId.newId()
        val logBuffer = StringBuffer("Started\nReady to go\n")
        val logLinesFlow = MutableSharedFlow<String>(replay = 1)
        logLinesFlow.emit("Ready to go")

        val runningBuild = mockk<RunningBuild>(relaxed = true) {
            every { id } returns buildId
            every { status } returns BuildStatus.Running
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { this@mockk.logBuffer } returns logBuffer
            every { stop() } returns Unit
            every { cancellationTokenSource } returns mockk<CancellationTokenSource>()
            every { logLines } returns logLinesFlow.asSharedFlow()
            every { completedTasks } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTaskPaths } returns emptySet()
            every { consoleOutput } returns logBuffer.toString()
        }

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            ToolNames.BACKGROUND_BUILD_GET_STATUS, mapOf(
                "buildId" to JsonPrimitive(buildId.toString()),
                "wait" to JsonPrimitive(2.0),
                "waitFor" to JsonPrimitive("Ready")
            )
        )
        val duration = testScheduler.currentTime - startTime

        assert(statusCall != null)
        assert(duration < 100)

        val statusText = statusCall!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assert(statusText.contains("BUILD IN PROGRESS"))
        assert(statusText.contains("Matching lines for 'Ready':"))
        assert(statusText.contains("Ready to go"))
    }

    @Test
    fun `background_build_get_status waits for waitForTask`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = BuildId.newId()
        val completedTasksFlow = MutableSharedFlow<String>(replay = 1)

        val runningBuild = mockk<RunningBuild>(relaxed = true) {
            every { id } returns buildId
            every { status } returns BuildStatus.Running
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { logBuffer } returns StringBuffer("Started\n")
            every { stop() } returns Unit
            every { cancellationTokenSource } returns mockk<CancellationTokenSource>()
            every { logLines } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTasks } returns completedTasksFlow.asSharedFlow()
            every { completedTaskPaths } returns emptySet()
            every { consoleOutput } returns "Started\n"
        }

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        launch {
            delay(500.milliseconds)
            every { runningBuild.completedTaskPaths } returns setOf(":help")
            completedTasksFlow.emit(":help")
        }

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            ToolNames.BACKGROUND_BUILD_GET_STATUS,
            mapOf(
                "buildId" to JsonPrimitive(buildId.toString()),
                "wait" to JsonPrimitive(2.0),
                "waitForTask" to JsonPrimitive(":help")
            )
        )
        val duration = testScheduler.currentTime - startTime

        assert(statusCall != null)
        assert(duration >= 500)
        val statusText = statusCall!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assert(statusText.contains("BUILD IN PROGRESS"))
    }

    @Test
    fun `background_build_get_status returns immediately if waitForTask already matches`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = BuildId.newId()
        val completedTasksFlow = MutableSharedFlow<String>(replay = 1)
        completedTasksFlow.emit(":help")

        val runningBuild = mockk<RunningBuild>(relaxed = true) {
            every { id } returns buildId
            every { status } returns BuildStatus.Running
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { logBuffer } returns StringBuffer("Started\n")
            every { stop() } returns Unit
            every { cancellationTokenSource } returns mockk<CancellationTokenSource>()
            every { logLines } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTasks } returns completedTasksFlow.asSharedFlow()
            every { completedTaskPaths } returns setOf(":help")
            every { consoleOutput } returns "Started\n"
        }

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            ToolNames.BACKGROUND_BUILD_GET_STATUS,
            mapOf(
                "buildId" to JsonPrimitive(buildId.toString()),
                "wait" to JsonPrimitive(2.0),
                "waitForTask" to JsonPrimitive(":help")
            )
        )
        val duration = testScheduler.currentTime - startTime

        assert(statusCall != null)
        assert(duration < 100)
        val statusText = statusCall!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assert(statusText.contains("BUILD IN PROGRESS"))
    }

    @Test
    fun `background_build_get_status respects afterCall for waitForTask`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = BuildId.newId()
        val completedTasksFlow = MutableSharedFlow<String>(replay = 1)
        completedTasksFlow.emit(":preExisting")

        val runningBuild = mockk<RunningBuild>(relaxed = true) {
            every { id } returns buildId
            every { status } returns BuildStatus.Running
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { logBuffer } returns StringBuffer("Started\n")
            every { stop() } returns Unit
            every { cancellationTokenSource } returns mockk<CancellationTokenSource>()
            every { logLines } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTasks } returns completedTasksFlow.asSharedFlow()
            every { completedTaskPaths } returns setOf(":preExisting")
            every { consoleOutput } returns "Started\n"
        }

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        launch {
            delay(500.milliseconds)
            every { runningBuild.completedTaskPaths } returns setOf(":preExisting", ":help")
            completedTasksFlow.emit(":help")
        }

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            ToolNames.BACKGROUND_BUILD_GET_STATUS,
            mapOf(
                "buildId" to JsonPrimitive(buildId.toString()),
                "wait" to JsonPrimitive(2.0),
                "waitForTask" to JsonPrimitive(":help"),
                "afterCall" to JsonPrimitive(true)
            )
        )
        val duration = testScheduler.currentTime - startTime

        assert(statusCall != null)
        assert(duration >= 500)
        val statusText = statusCall!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assert(statusText.contains("BUILD IN PROGRESS"))
    }
}
