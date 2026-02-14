package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemId
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.BuildStatus
import dev.rnett.gradle.mcp.gradle.build.Failure
import dev.rnett.gradle.mcp.gradle.build.FailureId
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.GradleBuildScan
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.gradle.build.TestOutcome
import dev.rnett.gradle.mcp.gradle.build.TestResult
import dev.rnett.gradle.mcp.gradle.build.TestResults
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import dev.rnett.gradle.mcp.tools.ToolNames
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
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class McpToolWorkflowsTest : BaseMcpServerTest() {

    private fun syntheticBuildResult(): FinishedBuild {
        val args = GradleInvocationArguments.DEFAULT
        val id = BuildId.newId()
        val console = buildString {
            appendLine("Starting build for ${id}")
            appendLine("> Task :help")
            appendLine("BUILD SUCCESSFUL")
        }.trimEnd()
        val scans = emptyList<GradleBuildScan>()
        val testResults = TestResults(
            passed = setOf(
                TestResult("com.example.HelloTest.passes", null, 0.1.seconds, failures = null, status = TestOutcome.PASSED)
            ),
            skipped = emptySet(),
            failed = setOf(
                TestResult(
                    "com.example.HelloTest.fails",
                    "Assertion failed",
                    0.2.seconds,
                    failures = listOf(
                        Failure(
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
        return FinishedBuild(
            id = id,
            args = args,
            consoleOutput = console,
            publishedScans = scans,
            testResults = testResults,
            problemAggregations = problems,
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )
    }

    @Test
    fun `lookup tools can read stored build`() = runTest {
        val result = syntheticBuildResult()
        val buildResults = server.koin.get<BuildManager>()
        buildResults.storeResult(result)

        // lookup_latest_builds
        val latestResult = server.client.callTool(ToolNames.LOOKUP_LATEST_BUILDS, mapOf("maxBuilds" to 1))
        assert(latestResult != null)

        // lookup_build
        val summary = server.client.callTool(
            ToolNames.LOOKUP_BUILD,
            mapOf("buildId" to result.id.toString())
        )
        assert(summary != null)

        // lookup_build_console_output pagination from head
        val page1 = server.client.callTool(
            ToolNames.LOOKUP_BUILD_CONSOLE_OUTPUT,
            mapOf(
                "buildId" to result.id.toString(),
                "offsetLines" to 0,
                "limitLines" to 2,
                "tail" to false
            )
        )
        assert(page1 != null)

        // lookup_build_tests summary
        val testsSummary = server.client.callTool(
            ToolNames.LOOKUP_BUILD_TESTS,
            mapOf(
                "buildId" to result.id.toString(),
                "summary" to JsonObject(mapOf("limit" to JsonPrimitive(10)))
            )
        )
        assert(testsSummary != null)

        // lookup_build_problems summary
        val problemsSummary = server.client.callTool(
            ToolNames.LOOKUP_BUILD_PROBLEMS,
            mapOf(
                "buildId" to result.id.toString(),
                "summary" to JsonObject(emptyMap<String, JsonPrimitive>())
            )
        )
        assert(problemsSummary != null)
    }

    @Test
    fun `execution works with single MCP root and no projectRoot`() = runTest {
        val result = syntheticBuildResult()
        val runningBuild = mockk<RunningBuild>(relaxed = true) {
            coEvery { awaitFinished() } returns result
            every { id } returns result.id
        }
        val buildManager = server.koin.get<BuildManager>()
        buildManager.storeResult(result)
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
        val call = server.client.callTool(ToolNames.RUN_GRADLE_COMMAND, args)
        assert(call != null)

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
        val runningBuild = mockk<RunningBuild>(relaxed = true) {
            every { id } returns buildId
            every { status } returns BuildStatus.Running
            every { startTime } returns Clock.System.now()
            every { args } returns GradleInvocationArguments(additionalArguments = listOf("help"))
            every { logBuffer } returns StringBuffer("line1\nline2\n")
            every { stop() } returns Unit
            every { isRunning } returns true
            every { hasBuildFinished } returns false
            every { problemAggregations } returns emptyMap()
            every { testResults } returns TestResults(emptySet(), emptySet(), emptySet())
            every { publishedScans } returns emptyList()
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

        val buildManager = server.koin.get<BuildManager>()
        buildManager.registerBuild(runningBuild)
        server.setServerRoots(Root(name = null, uri = tempDir.toUri().toString()))

        // test background_run_gradle_command
        val runCall = server.client.callTool(ToolNames.BACKGROUND_RUN_GRADLE_COMMAND, mapOf("commandLine" to JsonArray(listOf(JsonPrimitive("help")))))
        assert(runCall != null)

        // test background_build_list
        val listCall = server.client.callTool(ToolNames.BACKGROUND_BUILD_LIST, emptyMap())
        assert(listCall != null)

        // test background_build_get_status
        val statusCall = server.client.callTool(ToolNames.BACKGROUND_BUILD_GET_STATUS, mapOf("buildId" to buildId.toString()))
        assert(statusCall != null)
        val statusText = statusCall!!.content.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.TextContent>().joinToString { it.text ?: "" }
        assert(statusText.contains("Status: Running"))
        assert(statusText.contains("Duration: "))

        // test background_build_stop
        val stopCall = server.client.callTool(ToolNames.BACKGROUND_BUILD_STOP, mapOf("buildId" to buildId.toString()))
        assert(stopCall != null)
        coVerify { runningBuild.stop() }

        // Step 3: Test that lookup_latest_builds shows background builds
        val latestCall = server.client.callTool(ToolNames.LOOKUP_LATEST_BUILDS, mapOf("maxBuilds" to JsonPrimitive(5)))
        assert(latestCall != null)
        val latestText = latestCall!!.content.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.TextContent>().joinToString { it.text ?: "" }
        assert(latestText.contains(buildId.toString()))
        assert(latestText.contains("Running"))

        // Step 3: Test that lookup_latest_builds with onlyCompleted doesn't show background builds
        val latestCompletedCall = server.client.callTool(ToolNames.LOOKUP_LATEST_BUILDS, mapOf("maxBuilds" to JsonPrimitive(5), "onlyCompleted" to JsonPrimitive(true)))
        assert(latestCompletedCall != null)
        val latestCompletedText = latestCompletedCall!!.content.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.TextContent>().joinToString { it.text ?: "" }
        assert(!latestCompletedText.contains(buildId.toString()))

        // Step 3: Test that lookup_build shows "still running" message
        val lookupCall = server.client.callTool(ToolNames.LOOKUP_BUILD, mapOf("buildId" to buildId.toString()))
        assert(lookupCall != null)
        val lookupText = lookupCall!!.content.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.TextContent>().joinToString { it.text ?: "" }
        assert(lookupText.contains("still running"))
    }

    @Test
    fun `run_single_task_and_get_output uses arguments`() = runTest {
        val result = syntheticBuildResult()
        val runningBuild = mockk<RunningBuild>(relaxed = true) {
            coEvery { awaitFinished() } returns result
            every { id } returns result.id
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
        server.client.callTool(ToolNames.RUN_SINGLE_TASK_AND_GET_OUTPUT, args)

        assert(capturedArgs.captured.additionalArguments.contains(":help"))
        assert(capturedArgs.captured.additionalArguments.contains("--info"))
        assert(capturedArgs.captured.additionalArguments.contains("--stacktrace"))
    }
}
