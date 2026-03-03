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
import kotlin.test.assertContains
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
                TestResult("com.example.HelloTest.passes", null, 0.1.seconds, failures = null, status = TestOutcome.PASSED, metadata = emptyMap(), attachments = emptyList())
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
                    status = TestOutcome.FAILED,
                    metadata = emptyMap(),
                    attachments = emptyList()
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
    fun `inspect tools can read stored build`() = runTest {
        val result = syntheticBuildResult()
        val buildResults = server.koin.get<BuildManager>()
        buildResults.storeResult(result)

        // inspect_gradle_build (dashboard)
        val latestResult = server.client.callTool(ToolNames.INSPECT_BUILD, emptyMap())
        assert(latestResult != null)

        // inspect_gradle_build (summary)
        val summary = server.client.callTool(
            ToolNames.INSPECT_BUILD,
            mapOf("buildId" to result.id.toString(), "include" to JsonArray(listOf(JsonPrimitive("summary"))))
        )
        assert(summary != null)

        // inspect_gradle_build (console)
        val console = server.client.callTool(
            ToolNames.INSPECT_BUILD,
            mapOf(
                "buildId" to result.id.toString(),
                "include" to JsonArray(listOf(JsonPrimitive("console"))),
                "consoleOptions" to JsonObject(
                    mapOf(
                        "offsetLines" to JsonPrimitive(0),
                        "limitLines" to JsonPrimitive(2),
                        "tail" to JsonPrimitive(false)
                    )
                )
            )
        )
        assert(console != null)

        // inspect_gradle_build (tests)
        val tests = server.client.callTool(
            ToolNames.INSPECT_BUILD,
            mapOf(
                "buildId" to result.id.toString(),
                "include" to JsonArray(listOf(JsonPrimitive("tests"))),
                "testsOptions" to JsonObject(mapOf("summary" to JsonObject(mapOf("limit" to JsonPrimitive(10)))))
            )
        )
        assert(tests != null)

        // inspect_gradle_build (problems)
        val problems = server.client.callTool(
            ToolNames.INSPECT_BUILD,
            mapOf(
                "buildId" to result.id.toString(),
                "include" to JsonArray(listOf(JsonPrimitive("problems")))
            )
        )
        assert(problems != null)
    }

    @Test
    fun `gradle_execute works with single MCP root and no projectRoot`() = runTest {
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
        val call = server.client.callTool(ToolNames.GRADLEW, args)
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
    fun `consolidated build workflows`() = runTest {
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
            every { consoleOutputLines } returns listOf("line1", "line2")
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

        // test gradle_execute background
        val runCall = server.client.callTool(
            ToolNames.GRADLEW, mapOf(
                "commandLine" to JsonArray(listOf(JsonPrimitive("help"))),
                "background" to JsonPrimitive(true)
            )
        )
        assert(runCall != null)

        // test inspect_gradle_build (dashboard)
        val listCall = server.client.callTool(ToolNames.INSPECT_BUILD, emptyMap())
        assert(listCall != null)
        val listText = listCall!!.content.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.TextContent>().joinToString { it.text ?: "" }
        assertContains(listText, buildId.toString())
        assertContains(listText, "Running")

        // test inspect_gradle_build with buildId
        val statusCall = server.client.callTool(ToolNames.INSPECT_BUILD, mapOf("buildId" to buildId.toString()))
        assert(statusCall != null)
        val statusText = statusCall!!.content.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.TextContent>().joinToString { it.text ?: "" }
        assertContains(statusText, "BUILD IN PROGRESS")

        // test gradle_execute stop
        val stopCall = server.client.callTool(ToolNames.GRADLEW, mapOf("stopBuildId" to buildId.toString()))
        assert(stopCall != null)
        coVerify { runningBuild.stop() }
    }

    @Test
    fun `gradle_execute uses arguments`() = runTest {
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
            "commandLine" to JsonArray(listOf(JsonPrimitive(":help"), JsonPrimitive("--info"), JsonPrimitive("--stacktrace")))
        )
        server.client.callTool(ToolNames.GRADLEW, args)

        assert(capturedArgs.captured.additionalArguments.contains(":help"))
        assert(capturedArgs.captured.additionalArguments.contains("--info"))
        assert(capturedArgs.captured.additionalArguments.contains("--stacktrace"))
    }
}
