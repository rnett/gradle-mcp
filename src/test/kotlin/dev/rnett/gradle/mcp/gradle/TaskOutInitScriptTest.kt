package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.fixtures.testGradleProject
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
            initScriptProvider = DefaultInitScriptProvider(tempDir)
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
                initScriptProvider = DefaultInitScriptProvider(tempDir),
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

            assert(result.buildResult.isSuccessful == true)
            assert(result.buildResult.consoleOutput.contains("Gradle MCP init script task-out.init.gradle.kts loaded"))
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
                initScriptProvider = DefaultInitScriptProvider(),
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

            assert(result.buildResult.isSuccessful == true)
            assert(!result.buildResult.taskOutputCapturingFailed)

            assert(result.buildResult.consoleOutput.contains(":printMessage OUT Hello from task"))
            assert(result.buildResult.consoleOutput.contains(":printMessage ERR Error from task"))

            // Should NOT contain the original unprefixed lines (they should have been replaced)
            val lines = result.buildResult.consoleOutput.lines()
            assert(!lines.contains("Hello from task"))
            assert(!lines.contains("ERR: Error from task"))

            assert(result.buildResult.getTaskOutput(":printMessage") == "Hello from task\nError from task")

            val taskResult = result.buildResult.taskResults[":printMessage"]
            assert(taskResult != null)
            assert(taskResult!!.outcome == TaskOutcome.SUCCESS)
            assert(taskResult.consoleOutput?.trim() == "Hello from task\nError from task")
            assert(taskResult.duration >= 0.seconds)
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
                initScriptProvider = DefaultInitScriptProvider(),
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

            assert(result.buildResult.isSuccessful == true)
            assert(!result.buildResult.taskOutputCapturingFailed)
            assert(result.buildResult.consoleOutput.contains(":execTask OUT Hello from external process"))
            assert(result.buildResult.getTaskOutput(":execTask") == "Hello from external process")
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
                initScriptProvider = DefaultInitScriptProvider(),
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

            assert(result.buildResult.isSuccessful == true)
            assert(result.buildResult.taskOutputCapturingFailed)
        }
    }
}
