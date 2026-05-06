package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.fixtures.gradle.testJavaProject
import dev.rnett.gradle.mcp.fixtures.gradle.withTestGradleDefaults
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.failuresIfFailed
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrentSameProjectTest {

    private lateinit var buildManager: BuildManager
    private lateinit var provider: DefaultGradleProvider

    @BeforeAll
    fun setupAll() {
        buildManager = BuildManager()
        val config = GradleConfiguration()
        provider = DefaultGradleProvider(config, buildManager = buildManager)
    }

    @AfterAll
    fun cleanupAll() {
        provider.close()
        buildManager.close()
    }

    @Test
    fun `concurrent builds on same project should not close each others connection`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false) {
            buildScript(
                """
                plugins {
                    java
                }
                tasks.register("longTask") {
                    doLast {
                        val startedFile = file(System.getProperty("gradle.mcp.longTaskStartedFile")!!)
                        startedFile.parentFile.mkdirs()
                        startedFile.writeText("started")
                        println("Starting long task")
                        Thread.sleep(2000)
                        println("Finished long task")
                    }
                }
            """.trimIndent()
            )
        }.use { project ->
            val projectRoot = GradleProjectRoot(project.path().toString())
            val startedFile = project.path().resolve("build").resolve("long-task-started.txt")
            Files.deleteIfExists(startedFile)

            // Build 1: Long task
            val runningBuild1 = provider.runBuild(
                projectRoot = projectRoot,
                args = GradleInvocationArguments(additionalArguments = listOf("longTask")).withTestGradleDefaults(
                    additionalSystemProps = mapOf(
                        "gradle.mcp.longTaskStartedFile" to startedFile.toString(),
                        "org.gradle.configuration-cache" to "false"
                    )
                )
            )

            waitForFile(startedFile)

            // Build 2: Fast task (help)
            val runningBuild2 = provider.runBuild(
                projectRoot = projectRoot,
                args = GradleInvocationArguments(additionalArguments = listOf("help")).withTestGradleDefaults(
                    additionalSystemProps = mapOf("gradle.mcp.longTaskStartedFile" to startedFile.toString())
                )
            )

            // Wait for Build 2 to finish. It should finish before Build 1.
            val build2 = runningBuild2.awaitFinished()
            assertTrue(build2.outcome is BuildOutcome.Success, "Build 2 should be successful. Console: ${build2.consoleOutput}")

            // Now check Build 1. It should still be successful when it finishes.
            val build1 = runningBuild1.awaitFinished()
            if (build1.outcome !is BuildOutcome.Success) {
                println("Build 1 failed. Console output:")
                println(build1.consoleOutput)
                build1.outcome.failuresIfFailed?.forEach { f ->
                    val sb = StringBuilder()
                    f.writeFailureTree(sb, "Failure: ")
                    println(sb.toString())
                }
            }
            assertTrue(build1.outcome is BuildOutcome.Success, "Build 1 should be successful but failed with: ${build1.outcome.failuresIfFailed}")
        }
    }

    private suspend fun waitForFile(path: java.nio.file.Path, timeoutMillis: Long = 90_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!Files.exists(path)) {
            assertTrue(System.currentTimeMillis() < deadline, "Timed out waiting for Gradle start signal at $path")
            Thread.sleep(100)
        }
    }

}
