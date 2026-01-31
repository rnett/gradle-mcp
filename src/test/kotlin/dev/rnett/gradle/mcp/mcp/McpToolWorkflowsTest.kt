package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.BuildResults
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
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.Root
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.gradle.tooling.model.build.BuildEnvironment
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertTrue
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
        val testResults = BuildResult.TestResults(
            passed = setOf(
                BuildResult.TestResult("com.example.HelloTest.passes", null, 0.1.seconds, failures = null, status = TestOutcome.PASSED)
            ),
            skipped = emptySet(),
            failed = setOf(
                BuildResult.TestResult(
                    "com.example.HelloTest.fails",
                    "Assertion failed",
                    0.2.seconds,
                    failures = listOf(
                        BuildResult.Failure(
                            FailureId("f1"),
                            message = "expected true to be false",
                            description = null,
                            causes = emptyList(),
                            problems = emptyList()
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
        return BuildResult(id, args, console, scans, testResults, buildFailures = null, problems = problems)
    }

    @Test
    fun `lookup tools can read stored build`() = runTest {
        val result = syntheticBuildResult()
        BuildResults.storeResult(result)

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
    }

    @Test
    fun `introspection works with MCP root name`() = runTest {
        val result = syntheticBuildResult()
        val runningBuild = mockk<RunningBuild<BuildEnvironment>> {
            coEvery { awaitFinished() } returns GradleResult(result, Result.success(mockk()))
            coEvery { id } returns result.id
        }
        coEvery {
            provider.getBuildModel<BuildEnvironment>(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns runningBuild

        server.setServerRoots(Root(name = "proj", uri = tempDir.toUri().toString()))

        val call = server.client.callTool("get_environment", mapOf("projectRoot" to "proj"))
        assertTrue(call != null)

        coVerify {
            provider.getBuildModel<BuildEnvironment>(
                GradleProjectRoot(tempDir.pathString),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `execution works with single MCP root and no projectRoot`() = runTest {
        val result = syntheticBuildResult()
        val runningBuild = mockk<RunningBuild<Unit>> {
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
}
