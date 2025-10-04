package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.fixtures.testJavaProject
import dev.rnett.gradle.mcp.gradle.fixtures.testKotlinProject
import dev.rnett.gradle.mcp.gradle.fixtures.testMultiProjectBuild
import kotlinx.coroutines.test.runTest
import org.gradle.tooling.model.build.BuildEnvironment
import org.junit.jupiter.api.Assumptions
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class GradleProviderTest {

    private fun createTestProvider(): DefaultGradleProvider {
        return DefaultGradleProvider(
            GradleConfiguration(
                maxConnections = 5,
                ttl = 60.seconds,
                allowPublicScansPublishing = false
            )
        )
    }

    @Test
    fun `can get connection to gradle project`() = runTest(timeout = 120.seconds) {
        testJavaProject().use { project ->
            val provider = createTestProvider()
            val connection = provider.getConnection(project.path())
            assertNotNull(connection)
        }
    }

    @Test
    fun `can validate and get connection for gradle project`() = runTest(timeout = 120.seconds) {
        testJavaProject().use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val connection = provider.validateAndGetConnection(projectRoot, requiresGradleProject = true)
            assertNotNull(connection)
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

            assertTrue(result.value.isSuccess)
            val buildEnv = result.value.getOrThrow()
            assertNotNull(buildEnv)
            assertNotNull(buildEnv.gradle)
        }
    }

    @Test
    fun `can run simple gradle build`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help")
            )

            val result = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )

            assertTrue(result.value.isSuccess)
            assertTrue(result.buildResult.isSuccessful)
            assertTrue(result.buildResult.consoleOutput.isNotEmpty())
        }
    }

    @Test
    fun `can compile java project`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("compileJava")
            )

            val result = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )

            assertTrue(result.value.isSuccess)
            assertTrue(result.buildResult.isSuccessful)
            assertTrue(result.buildResult.consoleOutput.contains("BUILD SUCCESSFUL"))
        }
    }

    @Test
    fun `can run tests in java project`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = true).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = emptyList()
            )

            val result = provider.runTests(
                projectRoot = projectRoot,
                testPatterns = mapOf(":test" to emptySet()),
                args = args,
                tosAccepter = { false }
            )

            assertTrue(result.value.isSuccess)
            assertTrue(result.buildResult.isSuccessful)
            assertTrue(result.buildResult.testResults.passed.isNotEmpty())
            assertTrue(result.buildResult.testResults.failed.isEmpty())
        }
    }

    @Test
    fun `can run tests with test pattern filter`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = true).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = emptyList()
            )

            val result = provider.runTests(
                projectRoot = projectRoot,
                testPatterns = mapOf(":test" to setOf("com.example.HelloTest.testGreet")),
                args = args,
                tosAccepter = { false }
            )

            assertTrue(result.value.isSuccess)
            assertTrue(result.buildResult.isSuccessful)
            assertTrue(result.buildResult.testResults.totalCount > 0)
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

            val result = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )

            assertTrue(result.value.isFailure)
            assertTrue(result.buildResult.consoleOutput.isNotEmpty())
        }
    }

    @Test
    fun `can work with kotlin project`() = runTest(timeout = 120.seconds) {
        testKotlinProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help")
            )

            val result = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )

            assertTrue(result.value.isSuccess)
            assertTrue(result.buildResult.isSuccessful)
        }
    }

    @Test
    fun `can work with multi-project build`() = runTest(timeout = 120.seconds) {
        testMultiProjectBuild().use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("projects")
            )

            val result = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )

            assertTrue(result.value.isSuccess)
            assertTrue(result.buildResult.isSuccessful)
            assertTrue(result.buildResult.consoleOutput.contains("subproject-a"))
            assertTrue(result.buildResult.consoleOutput.contains("subproject-b"))
        }
    }

    @Test
    fun `can capture stdout during build`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help")
            )

            val capturedLines = mutableListOf<String>()
            val result = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false },
                stdoutLineHandler = { line -> capturedLines.add(line) }
            )

            assertTrue(result.value.isSuccess)
            assertTrue(capturedLines.isNotEmpty())
        }
    }

    @Test
    fun `can pass additional environment variables`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalEnvVars = mapOf("TEST_VAR" to "test_value"),
                additionalArguments = listOf("help")
            )

            val result = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )

            assertTrue(result.value.isSuccess)
        }
    }

    @Test
    fun `can pass additional system properties`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalSystemProps = mapOf("test.prop" to "test_value"),
                additionalArguments = listOf("help")
            )

            val result = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )

            assertTrue(result.value.isSuccess)
        }
    }

    @Test
    fun `can pass additional JVM arguments`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalJvmArgs = listOf("-Xmx512m"),
                additionalArguments = listOf("help")
            )

            val result = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )

            assertTrue(result.value.isSuccess)
        }
    }

    @Test
    fun `result contains build id`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help")
            )

            val result = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )

            assertTrue(result.value.isSuccess)
            assertNotNull(result.buildResult.id)
        }
    }

    @Test
    fun `captures published build scans when enabled`() = runTest(timeout = 180.seconds) {

        Assumptions.assumeTrue(System.getenv("CI") != null, "Only publish scans from CI")

        testJavaProject(hasTests = false).use { project ->
            val provider = DefaultGradleProvider(
                GradleConfiguration(
                    maxConnections = 2,
                    ttl = 60.seconds,
                    allowPublicScansPublishing = true
                )
            )
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help"),
                publishScan = true
            )

            val result = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { true }
            )

            assertTrue(result.value.isSuccess)
            val scans = result.buildResult.publishedScans
            assertTrue(scans.isNotEmpty())
            assertTrue(scans.all { it.url.contains("https://scans.gradle.com/s/") || it.develocityInstance.isNotBlank() })
        }
    }
}
