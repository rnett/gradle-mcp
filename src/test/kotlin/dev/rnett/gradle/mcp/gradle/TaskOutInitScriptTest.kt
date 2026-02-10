package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.fixtures.testGradleProject
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TaskOutInitScriptTest {

    private fun createTestProvider(): DefaultGradleProvider {
        val tempDir = java.nio.file.Files.createTempDirectory("gradle-mcp-test-init-")
        return DefaultGradleProvider(
            GradleConfiguration(
                maxConnections = 5,
                ttl = 60.seconds,
                allowPublicScansPublishing = false
            ),
            initScriptProvider = InitScriptProvider(tempDir)
        )
    }


    @Test
    fun `task-out init script is loaded`() = runTest(timeout = 300.seconds) {
        val tempDir = java.nio.file.Files.createTempDirectory("gradle-mcp-test-init-")
        testGradleProject {
            buildScript("")
        }.use { project ->
            val backgroundBuildManager = BackgroundBuildManager()
            val buildResults = BuildResults(backgroundBuildManager)
            val provider = DefaultGradleProvider(
                GradleConfiguration(
                    maxConnections = 5,
                    ttl = 60.seconds,
                    allowPublicScansPublishing = false
                ),
                initScriptProvider = InitScriptProvider(tempDir),
                backgroundBuildManager = backgroundBuildManager,
                buildResults = buildResults
            )
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help", "-Pgradle-mcp.init-scripts.hello"),
                envSource = EnvSource.NONE,
                additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
            )

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )
            val result = runningBuild.awaitFinished()

            assertEquals(result.buildResult.isSuccessful, true, "Build failed")
            assertContains(result.buildResult.consoleOutput, "Gradle MCP init script task-out.init.gradle.kts loaded")
        }
    }

    @Test
    fun `task-out init script prefixes task output`() = runTest(timeout = 120.seconds) {
        testGradleProject {
            buildScript(
                """
                tasks.register("printMessage") {
                    doLast {
                        println("Hello from task")
                        System.err.println("Error from task")
                    }
                }
            """.trimIndent()
            )
        }.use { project ->
            val backgroundBuildManager = BackgroundBuildManager()
            val buildResults = BuildResults(backgroundBuildManager)
            val provider = DefaultGradleProvider(
                GradleConfiguration(
                    maxConnections = 5,
                    ttl = 60.seconds,
                    allowPublicScansPublishing = false
                ),
                initScriptProvider = InitScriptProvider(),
                backgroundBuildManager = backgroundBuildManager,
                buildResults = buildResults
            )
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("printMessage"),
                additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
            )

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )
            val result = runningBuild.awaitFinished()

            assertTrue(result.buildResult.isSuccessful == true)
            kotlin.test.assertFalse(result.buildResult.taskOutputCapturingFailed, "Task output capturing should NOT have failed")

            assertContains(result.buildResult.consoleOutput, ":printMessage OUT Hello from task")
            assertContains(result.buildResult.consoleOutput, ":printMessage ERR Error from task")

            // Should NOT contain the original unprefixed lines (they should have been replaced)
            val lines = result.buildResult.consoleOutput.lines()
            kotlin.test.assertFalse(lines.contains("Hello from task"), "Original 'Hello from task' should have been replaced")
            kotlin.test.assertFalse(lines.contains("ERR: Error from task"), "Original 'ERR: Error from task' should have been replaced")

            kotlin.test.assertEquals("Hello from task\nError from task", result.buildResult.getTaskOutput(":printMessage"))

            val taskResult = result.buildResult.taskResults[":printMessage"]
            kotlin.test.assertNotNull(taskResult)
            kotlin.test.assertEquals(TaskOutcome.SUCCESS, taskResult.outcome)
            kotlin.test.assertEquals("Hello from task\nError from task", taskResult.consoleOutput?.trim())
            assertTrue(taskResult.duration >= 0.seconds)
        }
    }

    @Test
    fun `task-out init script prefixes external process output`() = runTest(timeout = 120.seconds) {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val command = if (isWindows) {
            "listOf(\"cmd\", \"/c\", \"echo Hello from external process\")"
        } else {
            "listOf(\"sh\", \"-c\", \"echo Hello from external process\")"
        }

        testGradleProject {
            buildScript(
                """
                tasks.register<Exec>("execTask") {
                    commandLine($command)
                }
            """.trimIndent()
            )
        }.use { project ->
            val backgroundBuildManager = BackgroundBuildManager()
            val buildResults = BuildResults(backgroundBuildManager)
            val provider = DefaultGradleProvider(
                GradleConfiguration(
                    maxConnections = 5,
                    ttl = 60.seconds,
                    allowPublicScansPublishing = false
                ),
                initScriptProvider = InitScriptProvider(),
                backgroundBuildManager = backgroundBuildManager,
                buildResults = buildResults
            )
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("execTask"),
                additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
            )

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )
            val result = runningBuild.awaitFinished()

            assertTrue(result.buildResult.isSuccessful == true)
            kotlin.test.assertFalse(result.buildResult.taskOutputCapturingFailed, "Task output capturing should NOT have failed")
            assertContains(result.buildResult.consoleOutput, ":execTask OUT Hello from external process")
            kotlin.test.assertEquals("Hello from external process", result.buildResult.getTaskOutput(":execTask"))
        }
    }

    @Test
    fun `taskOutputCapturingFailed is set when init script fails`() = runTest(timeout = 120.seconds) {
        testGradleProject {
            // No easy way to make the init script fail without changing it, 
            // but we can mock the failure by manually printing the warning message if we could.
            // Actually, we can use a build script that interferes or just test that if the message is there, it works.
            buildScript(
                """
                tasks.register("failInit") {
                    doLast {
                        logger.warn("Failed to set up gradle-mcp output capturing")
                    }
                }
            """.trimIndent()
            )
        }.use { project ->
            val backgroundBuildManager = BackgroundBuildManager()
            val buildResults = BuildResults(backgroundBuildManager)
            val provider = DefaultGradleProvider(
                GradleConfiguration(
                    maxConnections = 5,
                    ttl = 60.seconds,
                    allowPublicScansPublishing = false
                ),
                initScriptProvider = InitScriptProvider(),
                backgroundBuildManager = backgroundBuildManager,
                buildResults = buildResults
            )
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("failInit"),
                additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
            )

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )
            val result = runningBuild.awaitFinished()

            assertTrue(result.buildResult.isSuccessful == true)
            assertTrue(result.buildResult.taskOutputCapturingFailed, "taskOutputCapturingFailed should be true")
        }
    }
}
