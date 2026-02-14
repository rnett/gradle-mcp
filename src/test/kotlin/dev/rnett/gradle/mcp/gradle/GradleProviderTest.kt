package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.failuresIfFailed
import dev.rnett.gradle.mcp.gradle.fixtures.testJavaProject
import dev.rnett.gradle.mcp.gradle.fixtures.testKotlinProject
import dev.rnett.gradle.mcp.tools.InitScriptNames
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.gradle.tooling.model.build.BuildEnvironment
import org.junit.jupiter.api.Assumptions
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class GradleProviderTest {

    @Test
    fun `closing provider stops background builds and cancels scope`() = runTest {
        val provider = createTestProvider()
        testJavaProject().use { project ->
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help"),
                additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
            )

            // Start a build
            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )

            // Close provider
            provider.close()

            // Internal scope should be cancelled
            val scope = (provider as DefaultGradleProvider)::class.java.getDeclaredField("scope").let {
                it.isAccessible = true
                it.get(provider) as kotlinx.coroutines.CoroutineScope
            }
            assert(!scope.coroutineContext[Job]!!.isActive)
        }
    }

    private fun createTestProvider(): DefaultGradleProvider {
        val tempDir = java.nio.file.Files.createTempDirectory("gradle-mcp-test-init-")
        return DefaultGradleProvider(
            GradleConfiguration(
                maxConnections = 5,
                ttl = 60.seconds,
                allowPublicScansPublishing = false
            ),
            initScriptProvider = DefaultInitScriptProvider(tempDir),
            buildManager = BuildManager()
        )
    }

    @Test
    fun `can get connection to gradle project`() = runTest(timeout = 120.seconds) {
        testJavaProject().use { project ->
            val provider = createTestProvider()
            val connection = provider.getConnection(project.path())
        }
    }


    @Test
    fun `can get build model from gradle project`() = runTest(timeout = 120.seconds) {
        testJavaProject().use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments.DEFAULT

            val result = provider.getBuildModel(
                projectRoot = projectRoot,
                kClass = BuildEnvironment::class,
                args = args,
                tosAccepter = { false },
                requiresGradleProject = true
            )

            assert(result.value.isSuccess)
            val buildEnv = result.value.getOrThrow()
            assert(buildEnv.gradle != null)
        }
    }

    @Test
    fun `can run gradle build and capture output, environment and properties`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val capturedLines = mutableListOf<String>()
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help", "-Pgradle-mcp.init-scripts.hello"),
                additionalEnvVars = mapOf(
                    "GRADLE_USER_HOME" to project.gradleUserHome().toString(),
                    "TEST_VAR" to "test_value"
                ),
                additionalSystemProps = mapOf("test.prop" to "test_value"),
                additionalJvmArgs = listOf("-Xmx512m"),
                requestedInitScripts = listOf(InitScriptNames.TASK_OUT)
            )

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false },
                stdoutLineHandler = { line -> capturedLines.add(line) }
            )
            val result = runningBuild.awaitFinished()

            assert(runningBuild.status == BuildOutcome.Success)
            assert(result.outcome is BuildOutcome.Success)
            assert(result.consoleOutput.isNotEmpty())
            assert(capturedLines.isNotEmpty())
            assert(result.consoleOutput.contains("Gradle MCP init script task-out.init.gradle.kts loaded"))
        }
    }

    @Test
    fun `can run background build`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            createTestProvider().use { provider ->
                val projectRoot = GradleProjectRoot(project.pathString())
                val args = GradleInvocationArguments(
                    additionalArguments = listOf("help")
                )

                val runningBuild = provider.runBuild(
                    projectRoot = projectRoot,
                    args = args,
                    tosAccepter = { false }
                )

                val buildId = runningBuild.id

                // Verify it's in the manager WHILE it's running (or just after start)
                assert(provider.buildManager.getBuild(buildId) != null)

                val buildResult = runningBuild.awaitFinished()

                assert(buildResult.outcome is BuildOutcome.Success)
                assert(runningBuild.logBuffer.isNotEmpty())

                // Verify it's STILL in the manager after finish, but as a FrozenBuild
                val finalBuild = provider.buildManager.getBuild(buildId)
                assert(finalBuild is FinishedBuild)
            }
        }
    }

    @Test
    fun `can run tests in java project with and without filters`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = true).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments.DEFAULT

            // Run all tests
            val runningBuild = provider.runTests(
                projectRoot = projectRoot,
                testPatterns = mapOf(":test" to emptySet()),
                args = args,
                tosAccepter = { false }
            )
            val result = runningBuild.awaitFinished()

            assert(result.outcome is BuildOutcome.Success)
            assert(result.testResults.passed.isNotEmpty())
            assert(result.testResults.failed.isEmpty())

            // Run with filter
            val runningBuildFiltered = provider.runTests(
                projectRoot = projectRoot,
                testPatterns = mapOf(":test" to setOf("com.example.HelloTest.testGreet")),
                args = args,
                tosAccepter = { false }
            )
            val resultFiltered = runningBuildFiltered.awaitFinished()

            assert(resultFiltered.outcome is BuildOutcome.Success)
            assert(resultFiltered.testResults.totalCount == 1)
        }
    }

    @Test
    fun `can handle build failure`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("nonExistentTask")
            )

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )

            val result = runningBuild.awaitFinished()
            assert(result.outcome is BuildOutcome.Failed)
            assert(result.outcome.failuresIfFailed!!.isNotEmpty())
        }
    }

    @Test
    fun `can run multiple builds concurrently`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project1 ->
            testKotlinProject(hasTests = false).use { project2 ->
                createTestProvider().use { provider ->
                    val projectRoot1 = GradleProjectRoot(project1.pathString())
                    val projectRoot2 = GradleProjectRoot(project2.pathString())
                    val args = GradleInvocationArguments(
                        additionalArguments = listOf("help")
                    )

                    val runningBuild1 = provider.runBuild(
                        projectRoot = projectRoot1,
                        args = args,
                        tosAccepter = { false }
                    )

                    val runningBuild2 = provider.runBuild(
                        projectRoot = projectRoot2,
                        args = args,
                        tosAccepter = { false }
                    )

                    assert(runningBuild1 != null)
                    assert(runningBuild2 != null)

                    // Both should be in the manager
                    assert(provider.buildManager.getBuild(runningBuild1.id) != null)
                    assert(provider.buildManager.getBuild(runningBuild2.id) != null)

                    val result1 = runningBuild1.awaitFinished()
                    val result2 = runningBuild2.awaitFinished()

                    assert(result1.outcome is BuildOutcome.Success)
                    assert(result2.outcome is BuildOutcome.Success)

                    // Both should still be in the manager as FinishedBuilds
                    assert(provider.buildManager.getBuild(runningBuild1.id) is FinishedBuild)
                    assert(provider.buildManager.getBuild(runningBuild2.id) is FinishedBuild)
                }
            }
        }
    }

    @Test
    fun `can cancel running build`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            createTestProvider().use { provider ->
                val projectRoot = GradleProjectRoot(project.pathString())

                // Use a task that takes some time or just cancel immediately
                val runningBuild = provider.runBuild(
                    projectRoot = projectRoot,
                    args = GradleInvocationArguments(additionalArguments = listOf("help")),
                    tosAccepter = { false }
                )

                assert(runningBuild != null)
                runningBuild.stop()

                val result = runningBuild.awaitFinished()
                assert(runningBuild.status == BuildOutcome.Canceled)
                assert(provider.buildManager.getBuild(runningBuild.id) is FinishedBuild)
            }
        }
    }

    @Test
    fun `captures published build scans when enabled`() = runTest(timeout = 300.seconds) {

        Assumptions.assumeTrue(System.getenv("CI") != null, "Only publish scans from CI")

        testJavaProject(hasTests = false).use { project ->
            val tempDir = java.nio.file.Files.createTempDirectory("gradle-mcp-test-init-scans-")
            val provider = DefaultGradleProvider(
                GradleConfiguration(
                    maxConnections = 2,
                    ttl = 60.seconds,
                    allowPublicScansPublishing = true
                ),
                initScriptProvider = DefaultInitScriptProvider(tempDir),
                buildManager = BuildManager()
            )
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help"),
                publishScan = true,
                additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
            )

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { true }
            )
            val buildResult = runningBuild.awaitFinished()

            val scans = buildResult.publishedScans
            assert(scans.isNotEmpty())
            assert(scans.all { it.url.contains("https://scans.gradle.com/s/") || it.develocityInstance.isNotBlank() })
        }
    }
}
