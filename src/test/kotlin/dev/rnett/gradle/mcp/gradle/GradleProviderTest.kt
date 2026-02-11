package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.fixtures.testJavaProject
import dev.rnett.gradle.mcp.gradle.fixtures.testKotlinProject
import dev.rnett.gradle.mcp.gradle.fixtures.testMultiProjectBuild
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.gradle.tooling.model.build.BuildEnvironment
import org.junit.jupiter.api.Assumptions
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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
            assertTrue(!scope.coroutineContext[Job]!!.isActive)
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
            initScriptProvider = DefaultInitScriptProvider(tempDir)
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
            ).awaitFinished()

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
                additionalArguments = listOf("help"),
                additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
            )

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )
            val result = runningBuild.awaitFinished()

            assertTrue(runningBuild.status == BuildStatus.SUCCESSFUL)
            assertTrue(result.buildResult.isSuccessful == true)
            assertTrue(result.buildResult.consoleOutput.isNotEmpty())
        }
    }

    @Test
    fun `build applies task-out init script`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help", "-Pgradle-mcp.init-scripts.hello"),
                additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
            )

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )
            val result = runningBuild.awaitFinished()

            assertTrue(result.buildResult.isSuccessful == true)
            assertContains(result.buildResult.consoleOutput, "Gradle MCP init script task-out.init.gradle.kts loaded")
        }
    }

    @Test
    fun `can compile java project`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("compileJava"),
                additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
            )

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )
            val result = runningBuild.awaitFinished()

            assertTrue(runningBuild.status == BuildStatus.SUCCESSFUL)
            assertTrue(result.buildResult.isSuccessful == true)
            assertTrue(result.buildResult.consoleOutput.contains("BUILD SUCCESSFUL"))
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

                assertNotNull(runningBuild)
                val buildId = runningBuild.id

                // Verify it's in the manager WHILE it's running (or just after start)
                assertNotNull(provider.backgroundBuildManager.getBuild(buildId))

                val buildResult = runningBuild.awaitFinished()

                assertTrue(buildResult.buildResult.isSuccessful == true)
                assertTrue(runningBuild.status == BuildStatus.SUCCESSFUL)
                assertTrue(runningBuild.logBuffer.isNotEmpty())

                // Verify it's removed from the manager after finish
                kotlin.test.assertNull(provider.backgroundBuildManager.getBuild(buildId))
            }
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

            val runningBuild = provider.runTests(
                projectRoot = projectRoot,
                testPatterns = mapOf(":test" to emptySet()),
                args = args,
                tosAccepter = { false }
            )
            val result = runningBuild.awaitFinished()

            assertTrue(result.buildResult.isSuccessful == true)
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

            val runningBuild = provider.runTests(
                projectRoot = projectRoot,
                testPatterns = mapOf(":test" to setOf("com.example.HelloTest.testGreet")),
                args = args,
                tosAccepter = { false }
            )
            val result = runningBuild.awaitFinished()

            assertTrue(result.buildResult.isSuccessful == true)
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

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )

            val result = runningBuild.awaitFinished()
            assertTrue(result.buildResult.isSuccessful == false)
            assertTrue((result.buildResult as BuildResult).buildFailures!!.isNotEmpty())
            assertTrue(runningBuild.status == BuildStatus.FAILED)
        }
    }

    @Test
    fun `can work with kotlin project`() = runTest(timeout = 120.seconds) {
        testKotlinProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help"),
                additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
            )

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )
            val buildResult = runningBuild.awaitFinished()

            assertTrue(buildResult.buildResult.isSuccessful == true)
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

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )
            val buildResult = runningBuild.awaitFinished()

            assertTrue(buildResult.buildResult.isSuccessful == true)
            assertTrue(buildResult.buildResult.consoleOutput.contains("subproject-a"))
            assertTrue(buildResult.buildResult.consoleOutput.contains("subproject-b"))
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

                    assertNotNull(runningBuild1)
                    assertNotNull(runningBuild2)

                    // Both should be in the manager
                    assertNotNull(provider.backgroundBuildManager.getBuild(runningBuild1.id))
                    assertNotNull(provider.backgroundBuildManager.getBuild(runningBuild2.id))

                    val result1 = runningBuild1.awaitFinished()
                    val result2 = runningBuild2.awaitFinished()

                    assertTrue(result1.buildResult.isSuccessful == true)
                    assertTrue(result2.buildResult.isSuccessful == true)
                    assertTrue(runningBuild1.status == BuildStatus.SUCCESSFUL)
                    assertTrue(runningBuild2.status == BuildStatus.SUCCESSFUL)

                    // Both should be removed from the manager
                    kotlin.test.assertNull(provider.backgroundBuildManager.getBuild(runningBuild1.id))
                    kotlin.test.assertNull(provider.backgroundBuildManager.getBuild(runningBuild2.id))
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

                assertNotNull(runningBuild)
                runningBuild.stop()

                val result = runningBuild.awaitFinished()
                assertTrue(runningBuild.status == BuildStatus.CANCELLED)
                kotlin.test.assertNull(provider.backgroundBuildManager.getBuild(runningBuild.id))
            }
        }
    }

    @Test
    fun `can capture stdout during build`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help"),
                additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
            )

            val capturedLines = mutableListOf<String>()
            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false },
                stdoutLineHandler = { line -> capturedLines.add(line) }
            )
            runningBuild.awaitFinished()

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

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )
            val buildResult = runningBuild.awaitFinished()

            assertTrue(buildResult.buildResult.isSuccessful == true)
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

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )
            val buildResult = runningBuild.awaitFinished()

            assertTrue(buildResult.buildResult.isSuccessful == true)
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

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )
            val buildResult = runningBuild.awaitFinished()

            assertTrue(buildResult.buildResult.isSuccessful == true)
        }
    }

    @Test
    fun `result contains build id`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())
            val args = GradleInvocationArguments(
                additionalArguments = listOf("help"),
                additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
            )

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = args,
                tosAccepter = { false }
            )
            val buildResult = runningBuild.awaitFinished()

            assertNotNull(buildResult.buildResult.id)
        }
    }


    @Test
    fun `build should use provisioned JVM via gradle-daemon-jvm properties`() = runTest(timeout = 120.seconds) {
        testJavaProject(hasTests = false, additionalConfig = {
            // Provision JDK 21
            file("gradle/gradle-daemon-jvm.properties", "toolchainVersion=21")

            buildScript(
                """
                plugins {
                    java
                }
                
                repositories {
                    mavenCentral()
                }
                
                println("JAVA_HOME_IN_BUILD=" + System.getProperty("java.home"))
                println("JAVA_VERSION_IN_BUILD=" + System.getProperty("java.version"))
            """.trimIndent()
            )
        }).use { project ->
            val provider = createTestProvider()
            val projectRoot = GradleProjectRoot(project.pathString())

            val runningBuild = provider.runBuild(
                projectRoot = projectRoot,
                args = GradleInvocationArguments(
                    additionalArguments = listOf("help"),
                    additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
                ),
                tosAccepter = { false }
            )
            val buildResult = runningBuild.awaitFinished()

            val buildJavaVersion = buildResult.buildResult.consoleOutputLines
                .find { it.contains("JAVA_VERSION_IN_BUILD=") }
                ?.substringAfter("JAVA_VERSION_IN_BUILD=")

            println("Build JAVA version: $buildJavaVersion")

            assertTrue(buildResult.buildResult.isSuccessful == true)
            assertNotNull(buildJavaVersion)
            assertTrue(buildJavaVersion.startsWith("21"), "Build should have used JDK 21, but used $buildJavaVersion")
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
                initScriptProvider = DefaultInitScriptProvider(tempDir)
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

            val scans = buildResult.buildResult.publishedScans
            assertTrue(scans.isNotEmpty())
            assertTrue(scans.all { it.url.contains("https://scans.gradle.com/s/") || it.develocityInstance.isNotBlank() })
        }
    }
}
