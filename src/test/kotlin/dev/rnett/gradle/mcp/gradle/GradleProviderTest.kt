package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.fixtures.gradle.GradleProjectFixture
import dev.rnett.gradle.mcp.fixtures.gradle.testJavaProject
import dev.rnett.gradle.mcp.fixtures.gradle.testKotlinProject
import dev.rnett.gradle.mcp.fixtures.gradle.withTestGradleDefaults
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.failuresIfFailed
import dev.rnett.gradle.mcp.tools.InitScriptNames
import dev.rnett.gradle.mcp.utils.EnvProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.gradle.tooling.model.build.BuildEnvironment
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GradleProviderTest {

    private lateinit var provider: DefaultGradleProvider
    private lateinit var javaProject: GradleProjectFixture
    private lateinit var kotlinProject: GradleProjectFixture

    @BeforeAll
    fun setupAll() {
        provider = createTestProvider()
        javaProject = testJavaProject()
        kotlinProject = testKotlinProject()
    }

    @AfterAll
    fun cleanupAll() {
        provider.close()
        javaProject.close()
        kotlinProject.close()
    }

    @Test
    fun `can run gradle build with explicit java home`() = runTest(timeout = 120.seconds) {
        val projectRoot = GradleProjectRoot(javaProject.pathString())
        val currentJavaHome = System.getProperty("java.home")

        val args = GradleInvocationArguments(
            additionalArguments = listOf("help"),
            javaHome = currentJavaHome
        ).withTestGradleDefaults()

        val runningBuild = provider.runBuild(
            projectRoot = projectRoot,
            args = args
        )
        val result = runningBuild.awaitFinished()

        assert(result.outcome is BuildOutcome.Success)
    }

    @Test
    fun `closing provider stops background builds and cancels scope`() = runTest {
        val testProvider = createTestProvider()
        longRunningJavaProject().use { project ->
            val projectRoot = GradleProjectRoot(project.pathString())
            val startedFile = longTaskStartedFile(project.path())
            Files.deleteIfExists(startedFile)
            val args = GradleInvocationArguments(
                additionalArguments = listOf("longTask")
            ).withTestGradleDefaults(
                additionalSystemProps = mapOf(
                    "gradle.mcp.longTaskStartedFile" to startedFile.toString(),
                    "org.gradle.configuration-cache" to "false"
                )
            )

            val runningBuild = testProvider.runBuild(
                projectRoot = projectRoot,
                args = args
            )

            waitForFile(startedFile)
            assertTrue(testProvider.buildManager.listRunningBuilds().any { it.id == runningBuild.id })
            testProvider.close()

            val result = runningBuild.awaitFinished()
            assertEquals(BuildOutcome.Canceled, result.outcome)
            assertTrue(testProvider.buildManager.getBuild(runningBuild.id) is FinishedBuild)
            assertTrue(testProvider.buildManager.listRunningBuilds().none { it.id == runningBuild.id })
            val scope = testProvider.scope
            assertTrue(!scope.coroutineContext[Job]!!.isActive)
        }
    }

    @Test
    fun `can run gradle build with java home from environment`() = runTest(timeout = 120.seconds) {
        val currentJavaHome = System.getProperty("java.home")
        val mockEnv = object : EnvProvider {
            override fun getShellEnvironment(): Map<String, String> = mapOf("JAVA_HOME" to currentJavaHome)
            override fun getInheritedEnvironment(): Map<String, String> = mapOf("JAVA_HOME" to currentJavaHome)
        }

        val testProvider = DefaultGradleProvider(
            GradleConfiguration(),
            buildManager = BuildManager(),
            envProvider = mockEnv
        )

        testProvider.use { p ->
            val projectRoot = GradleProjectRoot(javaProject.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help"),
                envSource = EnvSource.INHERIT
            ).withTestGradleDefaults()

            val runningBuild = p.runBuild(
                projectRoot = projectRoot,
                args = args
            )
            val result = runningBuild.awaitFinished()

            assert(result.outcome is BuildOutcome.Success)
            // We can't easily verify which JDK was used by the Tooling API from the result, 
            // but ensuring the build succeeds with the environment variable set is a good baseline.
        }
    }

    @Test
    fun `explicit javaHome takes precedence over environment JAVA_HOME`() = runTest(timeout = 120.seconds) {
        val currentJavaHome = System.getProperty("java.home")
        val invalidJavaHome = "/invalid/path/to/jdk"

        val mockEnv = object : EnvProvider {
            override fun getShellEnvironment(): Map<String, String> = mapOf("JAVA_HOME" to invalidJavaHome)
            override fun getInheritedEnvironment(): Map<String, String> = mapOf("JAVA_HOME" to invalidJavaHome)
        }

        val testProvider = DefaultGradleProvider(
            GradleConfiguration(),
            buildManager = BuildManager(),
            envProvider = mockEnv
        )

        testProvider.use { p ->
            val projectRoot = GradleProjectRoot(javaProject.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help"),
                javaHome = currentJavaHome,
                envSource = EnvSource.INHERIT
            ).withTestGradleDefaults()

            val runningBuild = p.runBuild(
                projectRoot = projectRoot,
                args = args
            )
            val result = runningBuild.awaitFinished()

            // If it used JAVA_HOME from environment, it would fail or log a warning.
            // Since we provide the valid currentJavaHome, it should succeed.
            assert(result.outcome is BuildOutcome.Success)
        }
    }

    @Test
    fun `falls back to tooling api default when no java home is provided`() = runTest(timeout = 120.seconds) {
        val mockEnv = object : EnvProvider {
            override fun getShellEnvironment(): Map<String, String> = emptyMap()
            override fun getInheritedEnvironment(): Map<String, String> = emptyMap()
        }

        val testProvider = DefaultGradleProvider(
            GradleConfiguration(),
            buildManager = BuildManager(),
            envProvider = mockEnv
        )

        testProvider.use { p ->
            val projectRoot = GradleProjectRoot(javaProject.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help"),
                envSource = EnvSource.INHERIT
            ).withTestGradleDefaults()

            val runningBuild = p.runBuild(
                projectRoot = projectRoot,
                args = args
            )
            val result = runningBuild.awaitFinished()

            assert(result.outcome is BuildOutcome.Success)
        }
    }

    private fun createTestProvider(): DefaultGradleProvider {
        return DefaultGradleProvider(
            GradleConfiguration(),
            buildManager = BuildManager()
        )
    }

    @Test
    fun `can get connection to gradle project`() = runTest(timeout = 120.seconds) {
        provider.getConnection(javaProject.path()).use { connection ->
            val model = connection.model(BuildEnvironment::class.java).get()
            assertNotNull(model.gradle.gradleVersion)
        }
    }


    @Test
    fun `can get build model from gradle project`() = runTest(timeout = 120.seconds) {
        val projectRoot = GradleProjectRoot(javaProject.pathString())
        val args = GradleInvocationArguments.DEFAULT.withTestGradleDefaults()

        val result = provider.getBuildModel(
            projectRoot = projectRoot,
            kClass = BuildEnvironment::class,
            args = args,
            requiresGradleProject = true
        )

        assert(result.value.isSuccess)
        val buildEnv = result.value.getOrThrow()
        assert(buildEnv.gradle != null)
    }

    @Test
    fun `can run gradle build and capture output, environment and properties`() = runTest(timeout = 120.seconds) {
        val projectRoot = GradleProjectRoot(javaProject.pathString())
        val capturedLines = mutableListOf<String>()
        val args = GradleInvocationArguments(
            additionalArguments = listOf("help", "-Pgradle-mcp.init-scripts.hello"),
            additionalEnvVars = mapOf(
                "TEST_VAR" to "test_value"
            ),
            additionalSystemProps = mapOf("test.prop" to "test_value"),
            additionalJvmArgs = listOf("-Xmx512m"),
            requestedInitScripts = listOf(InitScriptNames.TASK_OUT)
        ).withTestGradleDefaults()

        val runningBuild = provider.runBuild(
            projectRoot = projectRoot,
            args = args,
            stdoutLineHandler = { line -> capturedLines.add(line) }
        )
        val result = runningBuild.awaitFinished()

        assert(runningBuild.status == BuildOutcome.Success)
        assert(result.outcome is BuildOutcome.Success)
        assert(result.consoleOutput.isNotEmpty())
        assert(capturedLines.isNotEmpty())
        assert(result.consoleOutput.contains("Gradle MCP init script task-out.init.gradle.kts loaded"))
    }

    @Test
    fun `can run background build`() = runTest(timeout = 120.seconds) {
        val projectRoot = GradleProjectRoot(javaProject.pathString())
        val args = GradleInvocationArguments(
            additionalArguments = listOf("help")
        ).withTestGradleDefaults()

        val runningBuild = provider.runBuild(
            projectRoot = projectRoot,
            args = args
        )

        val buildId = runningBuild.id

        // Verify it's in the manager WHILE it's running (or just after start)
        assert(provider.buildManager.getBuild(buildId) != null)

        val buildResult = runningBuild.awaitFinished()

        assert(buildResult.outcome is BuildOutcome.Success)
        assert(runningBuild.consoleOutput.isNotEmpty())

        // Verify it's STILL in the manager after finish, but as a FrozenBuild
        val finalBuild = provider.buildManager.getBuild(buildId)
        assert(finalBuild is FinishedBuild)
    }

    @Test
    fun `can run tests in java project with and without filters`() = runTest(timeout = 120.seconds) {
        val projectRoot = GradleProjectRoot(javaProject.pathString())
        val args = GradleInvocationArguments.DEFAULT.withTestGradleDefaults()

        // Run all tests
        val runningBuild = provider.runTests(
            projectRoot = projectRoot,
            testPatterns = mapOf(":test" to emptySet()),
            args = args
        )
        val result = runningBuild.awaitFinished()

        assert(result.outcome is BuildOutcome.Success)
        assert(result.testResults.passed.isNotEmpty())
        assert(result.testResults.failed.isEmpty())

        // Run with filter
        val runningBuildFiltered = provider.runTests(
            projectRoot = projectRoot,
            testPatterns = mapOf(":test" to setOf("com.example.HelloTest.testGreet")),
            args = args
        )
        val resultFiltered = runningBuildFiltered.awaitFinished()

        assert(resultFiltered.outcome is BuildOutcome.Success)
        assert(resultFiltered.testResults.totalCount == 1)
    }

    @Test
    fun `can handle build failure`() = runTest(timeout = 120.seconds) {
        val projectRoot = GradleProjectRoot(javaProject.pathString())
        val args = GradleInvocationArguments(
            additionalArguments = listOf("nonExistentTask")
        ).withTestGradleDefaults()

        val runningBuild = provider.runBuild(
            projectRoot = projectRoot,
            args = args
        )

        val result = runningBuild.awaitFinished()
        assert(result.outcome is BuildOutcome.Failed)
        assert(result.outcome.failuresIfFailed!!.isNotEmpty())
    }

    @Test
    fun `can run multiple builds concurrently`() = runTest(timeout = 120.seconds) {
        val projectRoot1 = GradleProjectRoot(javaProject.pathString())
        val projectRoot2 = GradleProjectRoot(kotlinProject.pathString())
        val args = GradleInvocationArguments(
            additionalArguments = listOf("help")
        ).withTestGradleDefaults()

        val runningBuild1 = provider.runBuild(
            projectRoot = projectRoot1,
            args = args
        )

        val runningBuild2 = provider.runBuild(
            projectRoot = projectRoot2,
            args = args
        )

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

    @Test
    fun `can cancel running build`() = runTest(timeout = 120.seconds) {
        longRunningJavaProject().use { project ->
            val projectRoot = GradleProjectRoot(project.pathString())
            val startedFile = longTaskStartedFile(project.path())
            Files.deleteIfExists(startedFile)
            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = GradleInvocationArguments(additionalArguments = listOf("longTask")).withTestGradleDefaults(
                    additionalSystemProps = mapOf(
                        "gradle.mcp.longTaskStartedFile" to startedFile.toString(),
                        "org.gradle.configuration-cache" to "false"
                    )
                )
            )

            waitForFile(startedFile)
            assertTrue(provider.buildManager.listRunningBuilds().any { it.id == runningBuild.id })
            runningBuild.stop()

            val result = runningBuild.awaitFinished()
            assertEquals(BuildOutcome.Canceled, result.outcome)
            assertEquals(BuildOutcome.Canceled, runningBuild.status)
            assertTrue(provider.buildManager.getBuild(runningBuild.id) is FinishedBuild)
            assertTrue(provider.buildManager.listRunningBuilds().none { it.id == runningBuild.id })
        }
    }

    @Test
    fun `captures published build scans when enabled`() = runTest(timeout = 300.seconds) {

        Assumptions.assumeTrue(System.getenv("CI") != null, "Only publish scans from CI")

        testJavaProject(hasTests = false).use { project ->
            val tempDir = Files.createTempDirectory("gradle-mcp-test-init-scans-")
            DefaultGradleProvider(
                GradleConfiguration(),
                buildManager = BuildManager()
            ).use { provider ->
                val projectRoot = GradleProjectRoot(project.pathString())
                val args = GradleInvocationArguments(
                    additionalArguments = listOf("help"),
                    publishScan = true
                ).withTestGradleDefaults()

                val runningBuild = provider.runBuild(
                    projectRoot = projectRoot,
                    args = args
                )
                val buildResult = runningBuild.awaitFinished()

                val scans = buildResult.publishedScans
                assert(scans.isNotEmpty())
                assert(scans.all { it.url.contains("https://scans.gradle.com/s/") || it.develocityInstance.isNotBlank() })
            }
        }
    }

    private fun longRunningJavaProject() = testJavaProject(hasTests = false) {
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
                    Thread.sleep(30000)
                }
            }
            """.trimIndent()
        )
    }

    private fun longTaskStartedFile(projectPath: Path): Path =
        projectPath.resolve("build").resolve("long-task-started.txt")

    private suspend fun waitForFile(path: Path, timeoutMillis: Long = 30_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!Files.exists(path)) {
            assertTrue(System.currentTimeMillis() < deadline, "Timed out waiting for Gradle start signal at $path")
            Thread.sleep(100)
        }
    }

}
