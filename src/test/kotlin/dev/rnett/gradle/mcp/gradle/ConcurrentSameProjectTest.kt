package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.fixtures.testJavaProject
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ConcurrentSameProjectTest {

    private fun createTestProvider(): DefaultGradleProvider {
        val config = GradleConfiguration(
            maxConnections = 10,
            ttl = 60.seconds,
            allowPublicScansPublishing = false
        )
        return DefaultGradleProvider(config)
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
                        println("Starting long task")
                        Thread.sleep(5000)
                        println("Finished long task")
                    }
                }
            """.trimIndent()
            )
        }.use { project ->
            createTestProvider().use { provider ->
                val projectRoot = GradleProjectRoot(project.path().toString())

                // Build 1: Long task
                val runningBuild1 = provider.runBuild(
                    projectRoot = projectRoot,
                    args = GradleInvocationArguments(additionalArguments = listOf("longTask")),
                    tosAccepter = { false }
                )

                // Wait a bit for build 1 to actually start and get connection
                delay(2.seconds)

                // Build 2: Fast task (help)
                val runningBuild2 = provider.runBuild(
                    projectRoot = projectRoot,
                    args = GradleInvocationArguments(additionalArguments = listOf("help")),
                    tosAccepter = { false }
                )

                // Wait for Build 2 to finish. It should finish before Build 1.
                val result2 = runningBuild2.awaitFinished()
                assertTrue(result2.buildResult.isSuccessful == true, "Build 2 should be successful. Console: ${result2.buildResult.consoleOutput}")

                // Now check Build 1. It should still be successful when it finishes.
                val result1 = runningBuild1.awaitFinished()
                if (!result1.buildResult.isSuccessful!!) {
                    println("Build 1 failed. Console output:")
                    println(result1.buildResult.consoleOutput)
                    result1.buildResult.buildFailures?.forEach {
                        it.writeFailureTree(StringBuilder().apply { append("Failure: ") })
                        println(it.message)
                        println(it.description)
                    }
                }
                assertTrue(result1.buildResult.isSuccessful == true, "Build 1 should be successful but failed with: ${result1.buildResult.buildFailures}")
            }
        }
    }
}
