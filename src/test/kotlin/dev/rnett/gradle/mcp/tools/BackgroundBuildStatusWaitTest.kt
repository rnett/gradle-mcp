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
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gradle.tooling.CancellationTokenSource
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.absolutePathString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundBuildStatusWaitTest : BaseMcpServerTest() {

    @BeforeTest
    override fun setup() = runTest {
        System.setProperty("gradle.mcp.test.disableSampling", "true")
        super.setup()
    }

    @AfterTest
    override fun cleanup() = runTest {
        System.clearProperty("gradle.mcp.test.disableSampling")
        super.cleanup()
    }

    private fun createMockRunningBuild(
        buildId: BuildId,
        logBuffer: StringBuffer = StringBuffer("Started\n"),
        logLinesFlow: MutableSharedFlow<String> = MutableSharedFlow(),
        completingTasksFlow: MutableSharedFlow<String> = MutableSharedFlow(),
        progressFlow: MutableSharedFlow<dev.rnett.gradle.mcp.gradle.build.BuildProgress> = MutableSharedFlow(replay = 1)
    ): RunningBuild {
        return mockk<RunningBuild>(relaxed = true) {
            every { id } returns buildId
            every { isRunning } returns true
            every { hasBuildFinished } returns false
            every { status } returns BuildStatus.Running
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { this@mockk.logBuffer } returns logBuffer
            every { stop() } returns Unit
            every { cancellationTokenSource } returns mockk<CancellationTokenSource>()
            every { logLines } returns logLinesFlow.asSharedFlow()
            every { completingTasks } returns completingTasksFlow.asSharedFlow()
            every { completedTaskPaths } answers { emptySet() }
            every { consoleOutput } answers { logBuffer.toString() }
            every { consoleOutputLines } answers { logBuffer.toString().lines() }
            every { progressTracker } returns mockk {
                every { progress } returns progressFlow.asSharedFlow()
            }
        }
    }

    @Test
    fun `inspect_build waits for completion`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = buildManager.newId()

        val runningBuild = createMockRunningBuild(buildId)
        coEvery { runningBuild.awaitFinished() } returns FinishedBuild(
            id = buildId,
            startTime = Clock.System.now(),
            args = GradleInvocationArguments(additionalArguments = listOf("help")),
            consoleOutput = "SUCCESS",
            publishedScans = emptyList(),
            testResults = TestResults(emptySet(), emptySet(), emptySet()),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        val mockFinishedBuild = FinishedBuild(
            id = buildId,
            startTime = Clock.System.now(),
            args = GradleInvocationArguments(additionalArguments = listOf("help")),
            consoleOutput = "SUCCESS",
            publishedScans = emptyList(),
            testResults = TestResults(emptySet(), emptySet(), emptySet()),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )

        every { runningBuild.finish(any(), any()) } returns mockFinishedBuild
        coEvery { runningBuild.awaitFinished() } returns mockFinishedBuild
        every { runningBuild.isRunning } returns true
        every { runningBuild.hasBuildFinished } returns false
        every { runningBuild.status } returns BuildOutcome.Success

        every { runningBuild.isRunning } returns false
        every { runningBuild.hasBuildFinished } returns true
        buildManager.storeResult(mockFinishedBuild)

        val statusCall = server.client.callTool(
            ToolNames.INSPECT_BUILD, buildJsonObject {
                put("buildId", buildId.toString())
                put("timeout", 2.0)
                put("projectRoot", tempDir.absolutePathString())
            }
        ) as CallToolResult

        val statusText = statusCall.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assert(statusText.contains("BUILD FINISHED"))
    }

    @Test
    fun `inspect_build waits for waitFor`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = buildManager.newId()
        val logBuffer = StringBuffer("Started\n")
        val logLinesFlow = MutableSharedFlow<String>(replay = 1)

        val runningBuild = createMockRunningBuild(buildId, logBuffer, logLinesFlow)
        coEvery { runningBuild.awaitFinished() } coAnswers { suspendCancellableCoroutine { } }

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
            ToolNames.INSPECT_BUILD, buildJsonObject {
                put("buildId", buildId.toString())
                put("timeout", 2.0)
                put("waitFor", "Ready")
                put("projectRoot", tempDir.absolutePathString())
            }
        ) as CallToolResult
        val duration = testScheduler.currentTime - startTime

        assert(duration >= 500)
        assert(duration < 2000)

        val statusText = statusCall.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assert(statusText.contains("BUILD IN PROGRESS"))
        assert(statusText.contains("Matching lines for 'Ready':"))
        assert(statusText.contains("Ready to go"))
    }

    @Test
    fun `inspect_build returns immediately if waitFor already matches`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = buildManager.newId()
        val logBuffer = StringBuffer("Started\nReady to go\n")
        val logLinesFlow = MutableSharedFlow<String>(replay = 1)
        logLinesFlow.emit("Ready to go")

        val runningBuild = createMockRunningBuild(buildId, logBuffer, logLinesFlow)
        coEvery { runningBuild.awaitFinished() } coAnswers { suspendCancellableCoroutine { } }

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            ToolNames.INSPECT_BUILD, buildJsonObject {
                put("buildId", buildId.toString())
                put("timeout", 2.0)
                put("waitFor", "Ready")
                put("projectRoot", tempDir.absolutePathString())
            }
        ) as CallToolResult
        val duration = testScheduler.currentTime - startTime

        assert(duration < 100)

        val statusText = statusCall.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assert(statusText.contains("BUILD IN PROGRESS"))
        assert(statusText.contains("Matching lines for 'Ready':"))
        assert(statusText.contains("Ready to go"))
    }

    @Test
    fun `inspect_build waits for waitForTask`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = buildManager.newId()
        val completedTasksFlow = MutableSharedFlow<String>(replay = 1)

        val runningBuild = createMockRunningBuild(buildId, completingTasksFlow = completedTasksFlow)
        coEvery { runningBuild.awaitFinished() } coAnswers { suspendCancellableCoroutine { } }

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        launch {
            delay(500.milliseconds)
            every { runningBuild.completedTaskPaths } returns setOf(":help")
            completedTasksFlow.emit(":help")
        }

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            ToolNames.INSPECT_BUILD, buildJsonObject {
                put("buildId", buildId.toString())
                put("timeout", 2.0)
                put("waitForTask", ":help")
                put("projectRoot", tempDir.absolutePathString())
            }
        ) as CallToolResult
        val duration = testScheduler.currentTime - startTime

        assert(duration >= 500)
        val statusText = statusCall.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assert(statusText.contains("BUILD IN PROGRESS"))
    }

    @Test
    fun `inspect_build returns immediately if waitForTask already matches`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = buildManager.newId()
        val completedTasksFlow = MutableSharedFlow<String>(replay = 1)
        completedTasksFlow.emit(":help")

        val runningBuild = createMockRunningBuild(buildId, completingTasksFlow = completedTasksFlow)
        every { runningBuild.completedTaskPaths } returns setOf(":help")
        coEvery { runningBuild.awaitFinished() } coAnswers { suspendCancellableCoroutine { } }

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            ToolNames.INSPECT_BUILD, buildJsonObject {
                put("buildId", buildId.toString())
                put("timeout", 2.0)
                put("waitForTask", ":help")
                put("projectRoot", tempDir.absolutePathString())
            }
        ) as CallToolResult
        val duration = testScheduler.currentTime - startTime

        assert(duration < 100)
        val statusText = statusCall.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assert(statusText.contains("BUILD IN PROGRESS"))
    }

    @Test
    fun `inspect_build respects afterCall for waitForTask`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = buildManager.newId()
        val completedTasksFlow = MutableSharedFlow<String>(replay = 1)
        completedTasksFlow.emit(":preExisting")

        val runningBuild = createMockRunningBuild(buildId, completingTasksFlow = completedTasksFlow)
        every { runningBuild.completedTaskPaths } returns setOf(":preExisting")
        coEvery { runningBuild.awaitFinished() } coAnswers { suspendCancellableCoroutine { } }

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        launch {
            delay(500.milliseconds)
            every { runningBuild.completedTaskPaths } returns setOf(":preExisting", ":help")
            completedTasksFlow.emit(":help")
        }

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            ToolNames.INSPECT_BUILD, buildJsonObject {
                put("buildId", buildId.toString())
                put("timeout", 2.0)
                put("waitForTask", ":help")
                put("afterCall", true)
                put("projectRoot", tempDir.absolutePathString())
            }
        ) as CallToolResult
        val duration = testScheduler.currentTime - startTime

        assert(duration >= 500) { "Expected duration >= 500ms, but was $duration" }
        val statusText = statusCall.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assert(statusText.contains("BUILD IN PROGRESS"))
    }

    @Test
    fun `inspect_build errors if build finishes without waitFor match`() = runTest {
        val buildManager = server.koin.get<BuildManager>()
        val buildId = buildManager.newId()
        val logBuffer = StringBuffer("Started\n")

        val runningBuild = createMockRunningBuild(buildId, logBuffer)
        every { runningBuild.status } returns BuildStatus.Running
        every { runningBuild.isRunning } returns true
        every { runningBuild.hasBuildFinished } returns false

        val mockFinishedBuild = FinishedBuild(
            id = buildId,
            startTime = Clock.System.now(),
            args = GradleInvocationArguments(additionalArguments = listOf("help")),
            consoleOutput = "Finished",
            publishedScans = emptyList(),
            testResults = TestResults(emptySet(), emptySet(), emptySet()),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )

        val finishDeferred = CompletableDeferred<FinishedBuild>()
        coEvery { runningBuild.awaitFinished() } coAnswers {
            finishDeferred.await()
        }

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        server.scope.launch {
            delay(1000) // Real time 1s
            buildManager.storeResult(mockFinishedBuild)
            finishDeferred.complete(mockFinishedBuild)
        }

        val statusCall = server.client.callTool(
            ToolNames.INSPECT_BUILD, buildJsonObject {
                put("buildId", buildId.toString())
                put("timeout", 5.0)
                put("waitFor", "Ready")
                put("projectRoot", tempDir.absolutePathString())
            }
        ) as CallToolResult

        assertTrue(statusCall.isError == true, "Expected an error result")
        val statusText = statusCall.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(statusText.contains("without matching regex: Ready"), "Error message mismatch: $statusText")
    }

    @Test
    fun `gradle with background=true returns immediately without waiting for build to finish`() = runTest {
        val buildId = buildManager.newId()
        val runningBuild = createMockRunningBuild(buildId)
        // awaitFinished() suspends forever — the tool must NOT call it in the background path
        coEvery { runningBuild.awaitFinished() } coAnswers { suspendCancellableCoroutine { } }

        every { provider.runBuild(any(), any(), any(), any(), any(), any()) } returns runningBuild
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        val result = server.client.callTool(
            ToolNames.GRADLE, buildJsonObject {
                put("commandLine", buildJsonArray { add("build") })
                put("background", true)
                put("projectRoot", tempDir.absolutePathString())
            }
        ) as CallToolResult

        // If the tool waited for the build to finish, this test would hang/timeout.
        // The fact that it completes proves the background path returns immediately.
        val text = result.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertEquals(buildId.toString(), text)
    }

    @Test
    fun `inspect_build reports progress while waiting`() = runTest {
        val buildId = buildManager.newId()
        val progressFlow = MutableSharedFlow<dev.rnett.gradle.mcp.gradle.build.BuildProgress>(replay = 10)
        val completingTasksFlow = MutableSharedFlow<String>(replay = 1)

        val runningBuild = createMockRunningBuild(buildId, progressFlow = progressFlow, completingTasksFlow = completingTasksFlow)
        coEvery { runningBuild.awaitFinished() } coAnswers { suspendCancellableCoroutine { } }

        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        val progressNotifications = ConcurrentLinkedQueue<ProgressNotification.Params>()
        val firstProgressReceived = CompletableDeferred<Unit>()
        val secondProgressReceived = CompletableDeferred<Unit>()

        server.client.setNotificationHandler(Method.Defined.NotificationsProgress) { notification: ProgressNotification ->
            val params = notification.params
            progressNotifications.add(params)
            if (params.message?.contains("Doing something") == true) {
                firstProgressReceived.complete(Unit)
            } else if (params.message?.contains("Almost done") == true) {
                secondProgressReceived.complete(Unit)
            }
            CompletableDeferred(Unit)
        }

        val toolCall = async {
            server.client.request<CallToolResult>(
                CallToolRequest(
                    name = ToolNames.INSPECT_BUILD,
                    arguments = buildJsonObject {
                        put("buildId", buildId.toString())
                        put("timeout", 5.0)
                        put("waitForTask", ":targetTask")
                        put("projectRoot", tempDir.absolutePathString())
                    },
                    _meta = buildJsonObject {
                        put("progressToken", "test-token")
                    }
                )
            )
        }

        launch {
            progressFlow.emit(dev.rnett.gradle.mcp.gradle.build.BuildProgress(0.5, "Doing something"))
            firstProgressReceived.await()

            progressFlow.emit(dev.rnett.gradle.mcp.gradle.build.BuildProgress(0.8, "Almost done"))
            secondProgressReceived.await()

            every { runningBuild.completedTaskPaths } returns setOf(":targetTask")
            completingTasksFlow.emit(":targetTask")
        }

        toolCall.await()

        val notifications = progressNotifications.toList()
        assertTrue(notifications.isNotEmpty(), "Should have received progress notifications")
        assertTrue(notifications.any { it.message?.contains("Doing something") == true }, "Should have received 'Doing something' progress")
        assertTrue(notifications.any { it.message?.contains("Almost done") == true }, "Should have received 'Almost done' progress")
    }
}
