package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.gradle.BackgroundBuildManager
import dev.rnett.gradle.mcp.gradle.Build
import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.BuildResults
import dev.rnett.gradle.mcp.gradle.BuildStatus
import dev.rnett.gradle.mcp.gradle.FailureId
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.GradleResult
import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemId
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import dev.rnett.gradle.mcp.gradle.RunningBuild
import dev.rnett.gradle.mcp.gradle.TestOutcome
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.modelcontextprotocol.kotlin.sdk.Root
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class McpToolWorkflowsTest : BaseMcpServerTest() {

    private fun syntheticBuildResult(): BuildResult {
        val args = GradleInvocationArguments.DEFAULT
        val id = BuildId.newId()
        val console = buildString {
            appendLine("Starting build for ${id}")
            appendLine("> Task :help")
            appendLine("BUILD SUCCESSFUL")
        }.trimEnd()
        val scans = emptyList<dev.rnett.gradle.mcp.gradle.GradleBuildScan>()
        val testResults = Build.TestResults(
            passed = setOf(
                Build.TestResult("com.example.HelloTest.passes", null, 0.1.seconds, failures = null, status = TestOutcome.PASSED)
            ),
            skipped = emptySet(),
            failed = setOf(
                Build.TestResult(
                    "com.example.HelloTest.fails",
                    "Assertion failed",
                    0.2.seconds,
                    failures = listOf(
                        Build.Failure(
                            FailureId("f1"),
                            message = "expected true to be false",
                            description = null,
                            causes = emptyList(),
                            problemAggregations = emptyMap()
                        )
                    ),
                    status = TestOutcome.FAILED
                )
            )
        )
        val problems = mapOf(
            ProblemSeverity.ERROR to listOf(
                ProblemAggregation(
                    ProblemAggregation.ProblemDefinition(
                        id = ProblemId("test.group", "E001"),
                        displayName = "An example error",
                        severity = ProblemSeverity.ERROR,
                        documentationLink = null
                    ),
                    occurences = listOf(
                        ProblemAggregation.ProblemOccurence(
                            details = "Bad thing happened",
                            originLocations = emptyList(),
                            contextualLocations = emptyList(),
                            potentialSolutions = listOf("Do better")
                        )
                    )
                )
            )
        )
        return BuildResult(id, args, console, scans, testResults, buildFailures = null, problemAggregations = problems)
    }

    @Test
    fun `lookup tools can read stored build`() = runTest {
        val result = syntheticBuildResult()
        val buildResults = server.koin.get<BuildResults>()
        buildResults.storeResult(result)

        // lookup_latest_builds
        val latestResult = server.client.callTool("lookup_latest_builds", mapOf("maxBuilds" to 1))
        assertTrue(latestResult != null)

        // lookup_build
        val summary = server.client.callTool(
            "lookup_build",
            mapOf("buildId" to result.id.toString())
        )
        assertTrue(summary != null)

        // lookup_build_console_output pagination from head
        val page1 = server.client.callTool(
            "lookup_build_console_output",
            mapOf(
                "buildId" to result.id.toString(),
                "offsetLines" to 0,
                "limitLines" to 2,
                "tail" to false
            )
        )
        assertTrue(page1 != null)

        // lookup_build_tests summary
        val testsSummary = server.client.callTool(
            "lookup_build_tests",
            mapOf(
                "buildId" to result.id.toString(),
                "summary" to JsonObject(mapOf("limit" to JsonPrimitive(10)))
            )
        )
        assertTrue(testsSummary != null)

        // lookup_build_problems summary
        val problemsSummary = server.client.callTool(
            "lookup_build_problems",
            mapOf(
                "buildId" to result.id.toString(),
                "summary" to JsonObject(emptyMap<String, JsonPrimitive>())
            )
        )
        assertTrue(problemsSummary != null)
    }

    @Test
    fun `execution works with single MCP root and no projectRoot`() = runTest {
        val result = syntheticBuildResult()
        val runningBuild = mockk<RunningBuild<Unit>>(relaxed = true) {
            coEvery { awaitFinished() } returns GradleResult(result, Result.success(Unit))
            coEvery { id } returns result.id
        }
        coEvery {
            provider.runBuild(
                GradleProjectRoot(tempDir.absolutePathString()),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns runningBuild

        // set single root with no name required
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        val args = mapOf(
            "commandLine" to JsonArray(listOf(JsonPrimitive("help"))),
            // projectRoot omitted
        )
        val call = server.client.callTool("run_gradle_command", args)
        assertTrue(call != null)

        coVerify {
            provider.runBuild(
                GradleProjectRoot(tempDir.absolutePathString()),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `background build workflows`() = runTest {
        val buildId = BuildId.newId()
        val runningBuild = mockk<RunningBuild<Unit>>(relaxed = true) {
            every { id } returns buildId
            every { status } returns BuildStatus.RUNNING
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { logBuffer } returns StringBuffer("line1\nline2\n")
            every { stop() } returns Unit
            every { isRunning } returns true
            every { isSuccessful } returns null
            every { problemAggregations } returns emptyMap()
            every { testResults } returns Build.TestResults(emptySet(), emptySet(), emptySet())
            every { publishedScans } returns ConcurrentLinkedQueue<dev.rnett.gradle.mcp.gradle.GradleBuildScan>()
            every { consoleOutput } returns "line1\nline2\n"
            every { problems } returns emptyList()
        }

        coEvery {
            provider.runBuild(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns runningBuild

        every { provider.backgroundBuildManager } returns mockk {
            every { registerBuild(any()) } returns Unit
            every { listBuilds() } returns listOf(runningBuild)
            every { getBuild(buildId) } returns runningBuild
        }
        every { provider.buildResults } returns mockk {
            every { getResult(buildId) } returns null
        }

        val backgroundBuildManager = server.koin.get<BackgroundBuildManager>()
        backgroundBuildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        // test background_run_gradle_command
        val runCall = server.client.callTool("background_run_gradle_command", mapOf("commandLine" to JsonArray(listOf(JsonPrimitive("help")))))
        assertTrue(runCall != null)

        // test background_build_list
        val listCall = server.client.callTool("background_build_list", emptyMap())
        assertTrue(listCall != null)

        // test background_build_get_status
        val statusCall = server.client.callTool("background_build_get_status", mapOf("buildId" to buildId.toString()))
        assertTrue(statusCall != null)
        val statusText = statusCall.content.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.TextContent>().joinToString { it.text ?: "" }
        assertTrue(statusText.contains("Status: RUNNING"))
        assertTrue(statusText.contains("Duration: "))

        // test background_build_stop
        val stopCall = server.client.callTool("background_build_stop", mapOf("buildId" to buildId.toString()))
        assertTrue(stopCall != null)
        coVerify { runningBuild.stop() }

        // Step 3: Test that lookup_latest_builds shows background builds
        val latestCall = server.client.callTool("lookup_latest_builds", mapOf("maxBuilds" to JsonPrimitive(5)))
        assertTrue(latestCall != null)
        val latestText = latestCall.content.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.TextContent>().joinToString { it.text ?: "" }
        assertTrue(latestText.contains(buildId.toString()))
        assertTrue(latestText.contains("RUNNING"))

        // Step 3: Test that lookup_latest_builds with onlyCompleted doesn't show background builds
        val latestCompletedCall = server.client.callTool("lookup_latest_builds", mapOf("maxBuilds" to JsonPrimitive(5), "onlyCompleted" to JsonPrimitive(true)))
        assertTrue(latestCompletedCall != null)
        val latestCompletedText = latestCompletedCall.content.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.TextContent>().joinToString { it.text ?: "" }
        assertTrue(!latestCompletedText.contains(buildId.toString()))

        // Step 3: Test that lookup_build shows "still running" message
        val lookupCall = server.client.callTool("lookup_build", mapOf("buildId" to buildId.toString()))
        assertTrue(lookupCall != null)
        val lookupText = lookupCall.content.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.TextContent>().joinToString { it.text ?: "" }
        assertTrue(lookupText.contains("still running"))
    }

    @Test
    fun `run_single_task_and_get_output uses arguments`() = runTest {
        val result = syntheticBuildResult()
        val runningBuild = mockk<RunningBuild<Unit>>(relaxed = true) {
            coEvery { awaitFinished() } returns GradleResult(result, Result.success(Unit))
            coEvery { id } returns result.id
        }

        val capturedArgs = slot<GradleInvocationArguments>()
        coEvery {
            provider.runBuild(
                any(),
                capture(capturedArgs),
                any(),
                any(),
                any(),
                any()
            )
        } returns runningBuild

        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        val args = mapOf(
            "taskPath" to JsonPrimitive(":help"),
            "arguments" to JsonArray(listOf(JsonPrimitive("--info"), JsonPrimitive("--stacktrace")))
        )
        server.client.callTool("run_single_task_and_get_output", args)

        assertTrue(capturedArgs.captured.additionalArguments.contains(":help"))
        assertTrue(capturedArgs.captured.additionalArguments.contains("--info"))
        assertTrue(capturedArgs.captured.additionalArguments.contains("--stacktrace"))
    }
}
