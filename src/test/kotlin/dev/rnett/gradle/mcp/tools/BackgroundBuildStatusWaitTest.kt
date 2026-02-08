package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BackgroundBuildManager
import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.BuildStatus
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.RunningBuild
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.gradle.tooling.CancellationTokenSource
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class BackgroundBuildStatusWaitTest : BaseMcpServerTest() {

    @Test
    fun `background_build_get_status waits for completion`() = runTest {
        val buildId = BuildId.newId()
        val resultDeferred = CompletableDeferred<dev.rnett.gradle.mcp.gradle.GradleResult<Unit>>()

        val runningBuild = mockk<RunningBuild<Unit>> {
            every { id } returns buildId
            every { status } returns BuildStatus.RUNNING
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { logBuffer } returns StringBuffer("Started\n")
            every { result } returns resultDeferred
            every { stop() } returns Unit
            every { cancellationTokenSource } returns mockk<CancellationTokenSource>()
            every { logLines } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTasks } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTaskPaths } returns emptySet()
            every { consoleOutput } returns "Started\n"
        }

        BackgroundBuildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        launch {
            delay(500.milliseconds)
            every { runningBuild.status } returns BuildStatus.SUCCESSFUL
            val mockBuildResult = mockk<BuildResult> {
                every { id } returns buildId
                every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
                every { isSuccessful } returns true
                every { consoleOutput } returns "SUCCESS"
                every { consoleOutputLines } returns listOf("SUCCESS")
                every { buildFailures } returns null
                every { testResults } returns mockk {
                    every { isEmpty } returns true
                    every { totalCount } returns 0
                    every { passed } returns emptySet()
                    every { skipped } returns emptySet()
                    every { failed } returns emptySet()
                }
                every { problems } returns emptyMap()
                every { publishedScans } returns emptyList()
            }
            dev.rnett.gradle.mcp.gradle.BuildResults.storeResult(mockBuildResult)
            resultDeferred.complete(mockk {
                every { buildResult } returns mockBuildResult
                every { value } returns Result.success(Unit)
            })
            // In real app, updateStatus would remove it from BackgroundBuildManager, but here we'll just let it be or remove it manually
            BackgroundBuildManager.removeBuild(buildId)
        }

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            "background_build_get_status", mapOf(
                "buildId" to JsonPrimitive(buildId.toString()),
                "wait" to JsonPrimitive(2.0)
            )
        )
        val duration = testScheduler.currentTime - startTime

        assertNotNull(statusCall)
        assertTrue(duration >= 500, "Should have waited at least 500ms, took $duration ms")
        val statusText = statusCall.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(statusText.contains("BUILD COMPLETED"), "Status should be COMPLETED, was: $statusText")
    }

    @Test
    fun `background_build_get_status waits for waitFor`() = runTest {
        val buildId = BuildId.newId()
        val resultDeferred = CompletableDeferred<dev.rnett.gradle.mcp.gradle.GradleResult<Unit>>()
        val logBuffer = StringBuffer("Started\n")
        val logLinesFlow = MutableSharedFlow<String>(replay = 1)

        val runningBuild = mockk<RunningBuild<Unit>> {
            every { id } returns buildId
            every { status } returns BuildStatus.RUNNING
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { this@mockk.logBuffer } returns logBuffer
            every { result } returns resultDeferred
            every { stop() } returns Unit
            every { cancellationTokenSource } returns mockk<CancellationTokenSource>()
            every { logLines } returns logLinesFlow.asSharedFlow()
            every { completedTasks } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTaskPaths } returns emptySet()
            every { consoleOutput } returns "Started\n"
        }

        BackgroundBuildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        launch {
            delay(500.milliseconds)
            val line = "Ready to go"
            logBuffer.append("$line\n")
            logLinesFlow.emit(line)
        }

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            "background_build_get_status", mapOf(
                "buildId" to JsonPrimitive(buildId.toString()),
                "wait" to JsonPrimitive(2.0),
                "waitFor" to JsonPrimitive("Ready")
            )
        )
        val duration = testScheduler.currentTime - startTime

        assertNotNull(statusCall)
        assertTrue(duration >= 500, "Should have waited at least 500ms, took $duration ms")
        assertTrue(duration < 2000, "Should have short-circuited wait, took $duration ms")

        val statusText = statusCall.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(statusText.contains("BUILD IN PROGRESS"), "Status should still be IN PROGRESS")
        assertTrue(statusText.contains("Matching lines for 'Ready':"), "Should contain matching lines header")
        assertTrue(statusText.contains("Ready to go"), "Should contain filter match")
    }

    @Test
    fun `background_build_get_status returns immediately if waitFor already matches`() = runTest {
        val buildId = BuildId.newId()
        val resultDeferred = CompletableDeferred<dev.rnett.gradle.mcp.gradle.GradleResult<Unit>>()
        val logBuffer = StringBuffer("Started\nReady to go\n")
        val logLinesFlow = MutableSharedFlow<String>(replay = 1)
        logLinesFlow.emit("Ready to go")

        val runningBuild = mockk<RunningBuild<Unit>> {
            every { id } returns buildId
            every { status } returns BuildStatus.RUNNING
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { this@mockk.logBuffer } returns logBuffer
            every { result } returns resultDeferred
            every { stop() } returns Unit
            every { cancellationTokenSource } returns mockk<CancellationTokenSource>()
            every { logLines } returns logLinesFlow.asSharedFlow()
            every { completedTasks } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTaskPaths } returns emptySet()
            every { consoleOutput } returns "Started\nReady to go\n"
        }

        BackgroundBuildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            "background_build_get_status", mapOf(
                "buildId" to JsonPrimitive(buildId.toString()),
                "wait" to JsonPrimitive(2.0),
                "waitFor" to JsonPrimitive("Ready")
            )
        )
        val duration = testScheduler.currentTime - startTime

        assertNotNull(statusCall)
        assertTrue(duration < 100, "Should have returned immediately, took $duration ms")

        val statusText = statusCall.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(statusText.contains("BUILD IN PROGRESS"), "Status should still be IN PROGRESS")
        assertTrue(statusText.contains("Matching lines for 'Ready':"), "Should contain matching lines header")
        assertTrue(statusText.contains("Ready to go"), "Should contain filter match")
    }

    @Test
    fun `background_build_get_status waits for waitForTask`() = runTest {
        val buildId = BuildId.newId()
        val resultDeferred = CompletableDeferred<dev.rnett.gradle.mcp.gradle.GradleResult<Unit>>()
        val completedTasksFlow = MutableSharedFlow<String>(replay = 1)

        val runningBuild = mockk<RunningBuild<Unit>> {
            every { id } returns buildId
            every { status } returns BuildStatus.RUNNING
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { logBuffer } returns StringBuffer("Started\n")
            every { result } returns resultDeferred
            every { stop() } returns Unit
            every { cancellationTokenSource } returns mockk<CancellationTokenSource>()
            every { logLines } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTasks } returns completedTasksFlow.asSharedFlow()
            every { completedTaskPaths } returns emptySet()
            every { consoleOutput } returns "Started\n"
        }

        BackgroundBuildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        launch {
            delay(500.milliseconds)
            every { runningBuild.completedTaskPaths } returns setOf(":help")
            completedTasksFlow.emit(":help")
        }

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            "background_build_get_status",
            mapOf(
                "buildId" to JsonPrimitive(buildId.toString()),
                "wait" to JsonPrimitive(2.0),
                "waitForTask" to JsonPrimitive(":help")
            )
        )
        val duration = testScheduler.currentTime - startTime

        assertNotNull(statusCall)
        assertTrue(duration >= 500, "Should have waited at least 500ms, took $duration ms")
        val statusText = statusCall.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(statusText.contains("BUILD IN PROGRESS"), "Status should be IN PROGRESS")
    }

    @Test
    fun `background_build_get_status returns immediately if waitForTask already matches`() = runTest {
        val buildId = BuildId.newId()
        val resultDeferred = CompletableDeferred<dev.rnett.gradle.mcp.gradle.GradleResult<Unit>>()
        val completedTasksFlow = MutableSharedFlow<String>(replay = 1)
        completedTasksFlow.emit(":help")

        val runningBuild = mockk<RunningBuild<Unit>> {
            every { id } returns buildId
            every { status } returns BuildStatus.RUNNING
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { logBuffer } returns StringBuffer("Started\n")
            every { result } returns resultDeferred
            every { stop() } returns Unit
            every { cancellationTokenSource } returns mockk<CancellationTokenSource>()
            every { logLines } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTasks } returns completedTasksFlow.asSharedFlow()
            every { completedTaskPaths } returns setOf(":help")
            every { consoleOutput } returns "Started\n"
        }

        BackgroundBuildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            "background_build_get_status",
            mapOf(
                "buildId" to JsonPrimitive(buildId.toString()),
                "wait" to JsonPrimitive(2.0),
                "waitForTask" to JsonPrimitive(":help")
            )
        )
        val duration = testScheduler.currentTime - startTime

        assertNotNull(statusCall)
        assertTrue(duration < 100, "Should have returned immediately, took $duration ms")
        val statusText = statusCall.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(statusText.contains("BUILD IN PROGRESS"), "Status should be IN PROGRESS")
    }

    @Test
    fun `background_build_get_status respects afterCall for waitForTask`() = runTest {
        val buildId = BuildId.newId()
        val resultDeferred = CompletableDeferred<dev.rnett.gradle.mcp.gradle.GradleResult<Unit>>()
        val completedTasksFlow = MutableSharedFlow<String>(replay = 1)
        completedTasksFlow.emit(":preExisting")

        val runningBuild = mockk<RunningBuild<Unit>> {
            every { id } returns buildId
            every { status } returns BuildStatus.RUNNING
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { logBuffer } returns StringBuffer("Started\n")
            every { result } returns resultDeferred
            every { stop() } returns Unit
            every { cancellationTokenSource } returns mockk<CancellationTokenSource>()
            every { logLines } returns MutableSharedFlow<String>().asSharedFlow()
            every { completedTasks } returns completedTasksFlow.asSharedFlow()
            every { completedTaskPaths } returns setOf(":preExisting")
            every { consoleOutput } returns "Started\n"
        }

        BackgroundBuildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        launch {
            delay(500.milliseconds)
            every { runningBuild.completedTaskPaths } returns setOf(":preExisting", ":help")
            completedTasksFlow.emit(":help")
        }

        val startTime = testScheduler.currentTime
        val statusCall = server.client.callTool(
            "background_build_get_status",
            mapOf(
                "buildId" to JsonPrimitive(buildId.toString()),
                "wait" to JsonPrimitive(2.0),
                "waitForTask" to JsonPrimitive(":help"),
                "afterCall" to JsonPrimitive(true)
            )
        )
        val duration = testScheduler.currentTime - startTime

        assertNotNull(statusCall)
        assertTrue(duration >= 500, "Should have waited for new task, took $duration ms")
        val statusText = statusCall.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(statusText.contains("BUILD IN PROGRESS"), "Status should be IN PROGRESS")
    }
}
