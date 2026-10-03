package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.fixtures.gradle.GradleProjectFixture
import dev.rnett.gradle.mcp.fixtures.gradle.testKotlinProject
import dev.rnett.gradle.mcp.fixtures.gradle.withTestGradleDefaults
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.gradle.build.TaskResult
import dev.rnett.gradle.mcp.gradle.build.TestResults
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.koin.core.scope.Scope
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class GradleExecutionToolTest : BaseMcpServerTest() {

    private fun runningBuildReturning(finished: FinishedBuild): RunningBuild {
        val running = mockk<RunningBuild>(relaxed = true)
        coEvery { running.awaitFinished() } returns finished
        return running
    }

    private fun syntheticFinished(
        id: BuildId,
        consoleOutput: String = "Synthetic build console",
        taskResults: Map<String, TaskResult> = emptyMap(),
        taskOutputs: Map<String, String> = emptyMap(),
        outcome: BuildOutcome = BuildOutcome.Success
    ): FinishedBuild = FinishedBuild(
        id = id,
        args = GradleInvocationArguments.DEFAULT,
        startTime = Clock.System.now(),
        consoleOutput = consoleOutput,
        publishedScans = emptyList(),
        testResults = TestResults(emptySet(), emptySet(), emptySet()),
        problemAggregations = emptyMap(),
        taskResults = taskResults,
        taskOutputs = taskOutputs,
        outcome = outcome,
        finishTime = Clock.System.now()
    )


    @ParameterizedTest(name = "gradle {0} is passed through without hidden defaults")
    @CsvSource(
        "--version, Gradle",
        "-v, Gradle",
        "--help, USAGE: gradle",
        "-h, USAGE: gradle"
    )
    fun `gradle alias passthrough uses raw provider args`(flag: String, expectedSnippet: String) = runTest {
        val finishedBuild = mockk<FinishedBuild>(relaxed = true)
        every { finishedBuild.outcome } returns BuildOutcome.Success
        every { finishedBuild.id } returns buildManager.newId()
        every { finishedBuild.args } returns GradleInvocationArguments(additionalArguments = listOf(flag))
        every { finishedBuild.consoleOutput } returns expectedSnippet
        coEvery { finishedBuild.awaitFinished() } returns finishedBuild

        val runningBuild = mockk<RunningBuild>(relaxed = true)
        coEvery { runningBuild.awaitFinished() } returns finishedBuild

        val capturedArgs = mutableListOf<GradleInvocationArguments>()
        every { provider.runBuild(any(), any(), any(), any(), any(), any()) } answers {
            capturedArgs += secondArg<GradleInvocationArguments>()
            runningBuild
        }

        val response = server.client.callTool(
            ToolNames.GRADLE,
            mapOf(
                "projectRoot" to tempDir.toString(),
                "commandLine" to JsonArray(listOf(JsonPrimitive(flag)))
            )
        )

        val text = response!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertContains(text, expectedSnippet)
        assertTrue(response.isError != true, "Call should not be an error, but was: $text")
        assertEquals(1, capturedArgs.size)
        assertEquals(listOf(flag), capturedArgs.single().additionalArguments)
        assertTrue(capturedArgs.single().additionalSystemProps.isEmpty(), "Expected no hidden Gradle defaults for CLI alias passthrough")
    }

    @Test
    fun `captureTaskOutput success leads with the build id header`() = runTest {
        val id = BuildId(Uuid.random().toString())
        val finished = syntheticFinished(id, taskOutputs = mapOf(":app:run" to "hello\nworld"))
        every { provider.runBuild(any(), any(), any(), any(), any(), any()) } returns runningBuildReturning(finished)

        val response = server.client.callTool(
            ToolNames.GRADLE,
            mapOf(
                "projectRoot" to tempDir.toString(),
                "commandLine" to JsonArray(listOf(JsonPrimitive(":app:run"))),
                "captureTaskOutput" to ":app:run"
            )
        )

        val text = response!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(text.startsWith("Gradle MCP Build ID: ${id.id}\n\n"), "Response must lead with the build identity header. Output:\n$text")
        assertContains(text, "hello\nworld")
    }

    @Test
    fun `captureTaskOutput truncation still leads with the build id header`() = runTest {
        val id = BuildId(Uuid.random().toString())
        val longOutput = (1..150).joinToString("\n") { "line $it" }
        val finished = syntheticFinished(id, taskOutputs = mapOf(":app:run" to longOutput))
        every { provider.runBuild(any(), any(), any(), any(), any(), any()) } returns runningBuildReturning(finished)

        val response = server.client.callTool(
            ToolNames.GRADLE,
            mapOf(
                "projectRoot" to tempDir.toString(),
                "commandLine" to JsonArray(listOf(JsonPrimitive(":app:run"))),
                "captureTaskOutput" to ":app:run"
            )
        )

        val text = response!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(text.startsWith("Gradle MCP Build ID: ${id.id}\n\n"), "Truncated response must lead with the build identity header. Output:\n$text")
        assertContains(text, "Task output truncated to last 100 lines")
        assertContains(text, "buildId=\"${id.id}\"")
        assertContains(text, "line 150")
    }

    @Test
    fun `captureTaskOutput not-found fallback is unchanged`() = runTest {
        val id = BuildId(Uuid.random().toString())
        val finished = syntheticFinished(id)
        every { provider.runBuild(any(), any(), any(), any(), any(), any()) } returns runningBuildReturning(finished)

        val response = server.client.callTool(
            ToolNames.GRADLE,
            mapOf(
                "projectRoot" to tempDir.toString(),
                "commandLine" to JsonArray(listOf(JsonPrimitive(":app:missing"))),
                "captureTaskOutput" to ":app:missing"
            )
        )

        val text = response!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        // The fallback path is unchanged: it must not be prefixed with an extra header, and it already
        // surfaces the build id through the appended toOutputString() summary.
        assertTrue(text.startsWith("Task output for :app:missing not found in executed tasks."), "Unexpected leading content: $text")
        assertContains(text, "Build result summary:")
        assertContains(text, "Gradle MCP Build ID: ${id.id}")
    }
}

class GradleExecutionToolRealBuildTest : BaseMcpServerTest() {

    private lateinit var project: GradleProjectFixture

    override fun Scope.createProvider(): GradleProvider {
        return DefaultGradleProvider(
            buildManager = get()
        ).withTestGradleDefaults()
    }

    @BeforeEach
    override fun setup() = runTest {
        project = testKotlinProject()
        super.setup()
    }

    @AfterEach
    override fun cleanup() = runTest {
        project.close()
        super.cleanup()
    }

    @Test
    fun `query_build shows provenance for binary plugin task from real build`() = runTest(timeout = 5.minutes) {
        server.client.callTool(
            ToolNames.GRADLE,
            mapOf(
                "projectRoot" to project.path().toString(),
                "commandLine" to JsonArray(listOf(JsonPrimitive("compileKotlin"), JsonPrimitive("--rerun")))
            )
        )

        val buildId = server.koin.get<BuildManager>().latestFinished(1).single().id.id
        val call = server.client.callTool(
            ToolNames.QUERY_BUILD,
            buildJsonObject {
                put("buildId", buildId)
                put("kind", "TASKS")
                put("query", ":compileKotlin")
            }
        )

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertContains(text, "Task: :compileKotlin")
        assertContains(text, "Provenance:")
    }

    @Test
    fun `query_build shows provenance for script task from real build`() = runTest(timeout = 5.minutes) {
        project.close()
        project = testKotlinProject {
            buildScript(
                """
                plugins {
                    kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}"
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation("org.junit.jupiter:junit-jupiter")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
                }

                tasks.test {
                    useJUnitPlatform()
                }

                tasks.register("scriptProvenance") {
                    doLast {
                        println("script provenance")
                    }
                }
                """.trimIndent()
            )
        }

        server.client.callTool(
            ToolNames.GRADLE,
            mapOf(
                "projectRoot" to project.path().toString(),
                "commandLine" to JsonArray(listOf(JsonPrimitive("scriptProvenance"), JsonPrimitive("--rerun")))
            )
        )

        val buildId = server.koin.get<BuildManager>().latestFinished(1).single().id.id
        val call = server.client.callTool(
            ToolNames.QUERY_BUILD,
            buildJsonObject {
                put("buildId", buildId)
                put("kind", "TASKS")
                put("query", ":scriptProvenance")
            }
        )

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertContains(text, "Task: :scriptProvenance")
        assertContains(text, "Provenance:")
        assertContains(text, "build.gradle.kts")
    }
}
