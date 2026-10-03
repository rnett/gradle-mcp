package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.gradle.build.TestResults
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class GradleDependencyServiceCancellationTest {

    private val gradleProvider: GradleProvider = mockk(relaxed = true)
    private val service = DefaultGradleDependencyService(gradleProvider)

    private fun stubCanceledBuild(consoleOutput: String) {
        val running = mockk<RunningBuild>(relaxed = true)
        every { running.consoleOutput } returns consoleOutput
        val finished = FinishedBuild(
            id = BuildId(Uuid.random().toString()),
            args = GradleInvocationArguments.DEFAULT,
            startTime = Clock.System.now(),
            consoleOutput = consoleOutput,
            publishedScans = emptyList(),
            testResults = TestResults(emptySet(), emptySet(), emptySet()),
            problemAggregations = emptyMap(),
            outcome = BuildOutcome.Canceled,
            finishTime = Clock.System.now()
        )
        coEvery { running.awaitFinished() } returns finished
        every { gradleProvider.runBuild(any(), any(), any(), any(), any(), any()) } returns running
    }

    @Test
    fun `getDependencies throws when a canceled build emitted no structured data`() = runTest {
        stubCanceledBuild("")

        val error = assertFailsWith<IllegalStateException> {
            with(ProgressReporter.NONE) {
                service.getDependencies(GradleProjectRoot("."), null, DependencyRequestOptions())
            }
        }

        assertContains(error.message!!, "cancel")
    }

    @Test
    fun `getDependencies throws when a canceled build emitted partial structured data`() = runTest {
        stubCanceledBuild(
            """
            [gradle-mcp] [DEPENDENCIES] PROJECT | : | root
            [gradle-mcp] [DEPENDENCIES] DEP | : | implementation | org.slf4j:slf4j-api:1.7.30
            """.trimIndent()
        )

        val error = assertFailsWith<IllegalStateException> {
            with(ProgressReporter.NONE) {
                service.getDependencies(GradleProjectRoot("."), null, DependencyRequestOptions())
            }
        }

        assertContains(error.message!!, "cancel")
    }

    @Test
    fun `downloadAllSources inherits the cancellation failure from the getDependencies funnel`() = runTest {
        stubCanceledBuild("")

        val error = assertFailsWith<IllegalStateException> {
            with(ProgressReporter.NONE) {
                service.downloadAllSources(GradleProjectRoot("."), null, false)
            }
        }

        assertContains(error.message!!, "cancel")
    }
}
