package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.TaskOutcome
import dev.rnett.gradle.mcp.gradle.build.TaskResult
import dev.rnett.gradle.mcp.gradle.build.TestOutcome
import dev.rnett.gradle.mcp.gradle.build.TestResult
import dev.rnett.gradle.mcp.gradle.build.TestResults
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class GradleBuildLookupOutputFileTest : BaseMcpServerTest() {

    private fun createSyntheticBuild(): FinishedBuild {
        val id = BuildId(Uuid.random().toString())
        val testResults = TestResults(
            passed = setOf(
                TestResult("testOne", "com.example.TestA", "Output A1", 0.1.seconds, null, TestOutcome.PASSED, emptyMap(), emptyList()),
                TestResult("testTwo", "com.example.TestA", "Output A2", 0.2.seconds, null, TestOutcome.PASSED, emptyMap(), emptyList())
            ),
            failed = emptySet(),
            skipped = emptySet()
        )
        val taskResults = mapOf(
            ":app:compileJava" to TaskResult(":app:compileJava", TaskOutcome.SUCCESS, 1.0.seconds, "Compile output"),
            ":app:processResources" to TaskResult(":app:processResources", TaskOutcome.SUCCESS, 0.5.seconds, "Resources output")
        )
        return FinishedBuild(
            id = id,
            startTime = Clock.System.now(),
            args = GradleInvocationArguments.DEFAULT,
            consoleOutput = "Synthetic build console",
            publishedScans = emptyList(),
            testResults = testResults,
            problemAggregations = emptyMap(),
            taskResults = taskResults,
            outcome = BuildOutcome.Success,
            finishTime = Clock.System.now()
        )
    }

    @Test
    fun `test outputFile writes to file and returns correct message`() = runTest {
        val build = createSyntheticBuild()
        buildManager.storeResult(build)

        val tempFile = Path(tempDir.toString(), "test-output.txt")
        tempFile.deleteIfExists()

        val response = server.client.callTool("inspect_build", buildJsonObject {
            put("buildId", build.id.id)
            put("mode", "summary")
            put("outputFile", tempFile.toString())
        }) as CallToolResult

        val text = (response.content.first() as TextContent).text
        requireNotNull(text)
        assertTrue(text.startsWith("Output written to"))
        assertTrue(text.contains(tempFile.toString()))
        assertTrue(text.contains("characters"))
        assertTrue(text.contains("lines"))

        // Verify file was written
        val fileContent = tempFile.readText()
        assertTrue(fileContent.contains("--- BUILD FINISHED ---"))
        assertTrue(fileContent.contains("--- Summary ---"))
        assertTrue(fileContent.contains("Tests:"))
    }

    @Test
    fun `test outputFile with details mode writes correct content`() = runTest {
        val build = createSyntheticBuild()
        buildManager.storeResult(build)

        val tempFile = Path(tempDir.toString(), "test-details.txt")
        tempFile.deleteIfExists()

        val response = server.client.callTool("inspect_build", buildJsonObject {
            put("buildId", build.id.id)
            put("mode", "details")
            put("testName", "com.example.TestA.testOne")
            put("outputFile", tempFile.toString())
        }) as CallToolResult

        val text = (response.content.first() as TextContent).text
        requireNotNull(text)
        assertTrue(text.startsWith("Output written to"))
        assertTrue(text.contains(tempFile.toString()))

        // Verify file was written with test details
        val fileContent = tempFile.readText()
        assertTrue(fileContent.contains("--- BUILD FINISHED ---"))
        assertTrue(fileContent.contains("--- Tests ---"))
        assertTrue(fileContent.contains("com.example.TestA.testOne"))
        assertTrue(fileContent.contains("PASSED"))
    }

    @Test
    fun `test outputFile with task path writes correct content`() = runTest {
        val build = createSyntheticBuild()
        buildManager.storeResult(build)

        val tempFile = Path(tempDir.toString(), "test-task.txt")
        tempFile.deleteIfExists()

        val response = server.client.callTool("inspect_build", buildJsonObject {
            put("buildId", build.id.id)
            put("mode", "details")
            put("taskPath", ":app:compileJava")
            put("outputFile", tempFile.toString())
        }) as CallToolResult

        val text = (response.content.first() as TextContent).text
        requireNotNull(text)
        assertTrue(text.startsWith("Output written to"))
        assertTrue(text.contains(tempFile.toString()))

        // Verify file was written with task details
        val fileContent = tempFile.readText()
        assertTrue(fileContent.contains("--- BUILD FINISHED ---"))
        assertTrue(fileContent.contains("--- Tasks ---"))
        assertTrue(fileContent.contains(":app:compileJava"))
        assertTrue(fileContent.contains("SUCCESS"))
    }

    @Test
    fun `test outputFile returns error message on failure`() = runTest {
        val build = createSyntheticBuild()
        buildManager.storeResult(build)

        // Use an invalid path that should fail
        val invalidPath = "/invalid/path/that/should/fail/test-output.txt"

        val response = server.client.callTool("inspect_build", buildJsonObject {
            put("buildId", build.id.id)
            put("mode", "summary")
            put("outputFile", invalidPath)
        }) as CallToolResult

        val text = (response.content.first() as TextContent).text
        requireNotNull(text)
        assertTrue(text.startsWith("Error writing to file"))
        assertTrue(text.contains(invalidPath))
    }

    @Test
    fun `test without outputFile returns content directly`() = runTest {
        val build = createSyntheticBuild()
        buildManager.storeResult(build)

        val response = server.client.callTool("inspect_build", buildJsonObject {
            put("buildId", build.id.id)
            put("mode", "summary")
        }) as CallToolResult

        val text = (response.content.first() as TextContent).text
        requireNotNull(text)
        // Should NOT contain "Output written to"
        assertTrue(!text.startsWith("Output written to"))
        // Should contain the actual build output
        assertTrue(text.contains("--- BUILD FINISHED ---"))
        assertTrue(text.contains("--- Summary ---"))
    }

    @Test
    fun `test outputFile skips pagination limits`() = runTest {
        // Create 30 synthetic builds (default limit is 20)
        repeat(30) {
            val build = createSyntheticBuild()
            buildManager.storeResult(build)
        }

        val tempFile = Path(tempDir.toString(), "test-pagination.txt")
        tempFile.deleteIfExists()

        val response = server.client.callTool("inspect_build", buildJsonObject {
            put("outputFile", tempFile.toString())
        }) as CallToolResult

        val text = (response.content.first() as TextContent).text
        requireNotNull(text)
        assertTrue(text.startsWith("Output written to"))

        val fileContent = tempFile.readText()
        // Count lines that look like build entries (not header)
        val buildCount = fileContent.lines().count { it.contains(" | ") && !it.contains("BuildId |") }
        assertTrue(buildCount >= 30, "Should have at least 30 builds in output, but got $buildCount")
        // Verify no pagination metadata
        assertTrue(!fileContent.contains("Pagination: Showing"), "Should not contain pagination metadata")
    }

    @Test
    fun `test outputFile ignores offset and tail parameters`() = runTest {
        val original = createSyntheticBuild()
        val build = FinishedBuild(
            id = original.id,
            args = original.args,
            startTime = original.startTime,
            consoleOutput = (1..150).joinToString("\n") { "Line $it" },
            publishedScans = original.publishedScans,
            testResults = original.testResults,
            problemAggregations = original.problemAggregations,
            taskResults = original.taskResults,
            taskOutputs = original.taskOutputs,
            taskOutputCapturingFailed = original.taskOutputCapturingFailed,
            outcome = original.outcome,
            finishTime = original.finishTime
        )
        buildManager.storeResult(build)

        val tempFile = Path(tempDir.toString(), "test-params-ignored.txt")
        tempFile.deleteIfExists()

        // Request tail + offset 20
        val response = server.client.callTool("inspect_build", buildJsonObject {
            put("buildId", build.id.id)
            put("consoleTail", true)
            put("pagination", buildJsonObject {
                put("offset", 20)
                put("limit", 10)
            })
            put("outputFile", tempFile.toString())
        }) as CallToolResult

        val text = (response.content.first() as TextContent).text
        requireNotNull(text)

        val fileContent = tempFile.readText()
        // Should contain Line 1 (proving it's head mode)
        assertTrue(fileContent.contains("Line 1"), "Should contain Line 1 (forced head mode)")
        // Should contain more than 10 lines (proving limit was ignored)
        val lineCount = fileContent.lines().count { it.startsWith("Line ") }
        assertTrue(lineCount >= 150, "Should contain all 150 lines, but got $lineCount")
        // Verify no pagination metadata
        assertTrue(!fileContent.contains("Pagination: Showing"), "Should not contain pagination metadata")
    }
}
