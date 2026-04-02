package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.fixtures.gradle.GradleProjectFixture
import dev.rnett.gradle.mcp.fixtures.gradle.testGradleProject
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.tools.toOutputString
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReplEnvironmentServiceTest {

    private lateinit var buildManager: BuildManager
    private lateinit var provider: DefaultGradleProvider
    private lateinit var replEnvService: DefaultReplEnvironmentService
    private lateinit var complexProject: GradleProjectFixture

    @BeforeAll
    fun setupAll() {
        buildManager = BuildManager()
        provider = DefaultGradleProvider(
            GradleConfiguration(),
            buildManager = buildManager
        )
        replEnvService = DefaultReplEnvironmentService(provider)

        complexProject = testGradleProject {
            settings(
                """
                rootProject.name = "root"
                include("kotlin-jvm", "java-only", "kmp-project", "kotlin-java-mixed", "clean-test", "compiler-args")
            """.trimIndent()
            )

            subproject(
                "kotlin-jvm", buildScript = """
                plugins { kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
                repositories { mavenCentral() }
                kotlin { jvmToolchain(17) }
                dependencies { implementation(kotlin("stdlib")) }
                tasks.register("createSource") {
                    doLast {
                        file("src/main/kotlin/Foo.kt").apply {
                            parentFile.mkdirs()
                            writeText("class Foo")
                        }
                    }
                }
            """.trimIndent()
            )

            subproject(
                "java-only", buildScript = """
                plugins { java }
                repositories { mavenCentral() }
                java {
                    toolchain {
                        languageVersion.set(JavaLanguageVersion.of(17))
                    }
                }
            """.trimIndent()
            )

            subproject(
                "kmp-project", buildScript = """
                plugins { kotlin("multiplatform") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
                repositories { mavenCentral() }
                kotlin { jvm() }
                tasks.register("createSource") {
                    doLast {
                        file("src/jvmMain/kotlin/Foo.kt").apply {
                            parentFile.mkdirs()
                            writeText("class Foo")
                        }
                    }
                }
            """.trimIndent()
            )

            subproject(
                "kotlin-java-mixed", buildScript = """
                plugins { 
                    kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}"
                    java
                }
                repositories { mavenCentral() }
            """.trimIndent()
            )

            subproject(
                "clean-test", buildScript = """
                plugins { kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
                repositories { mavenCentral() }
                tasks.register("createSource") {
                    doLast {
                        file("src/main/kotlin/Foo.kt").apply {
                            parentFile.mkdirs()
                            writeText("class Foo")
                        }
                    }
                }
            """.trimIndent()
            )

            subproject(
                "compiler-args", buildScript = """
                plugins {
                    kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}"
                    kotlin("plugin.serialization") version "${TestFixturesBuildConfig.KOTLIN_VERSION}"
                }
                repositories { mavenCentral() }
                tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                    compilerOptions { freeCompilerArgs.add("-Xjvm-default=all") }
                }
                tasks.register("createSource") {
                    doLast {
                        file("src/main/kotlin/foo.kt").apply {
                            parentFile.mkdirs()
                            writeText("fun foo() {}")
                        }
                    }
                }
            """.trimIndent()
            )
        }

        // Initialize source files
        runBlocking {
            val projectRoot = GradleProjectRoot(complexProject.pathString())
            val res = provider.runBuild(
                projectRoot,
                GradleInvocationArguments(
                    additionalArguments = listOf(":kotlin-jvm:createSource", ":kmp-project:createSource", ":clean-test:createSource", ":compiler-args:createSource"),
                    additionalEnvVars = mapOf("GRADLE_USER_HOME" to complexProject.gradleUserHome().toString())
                )
            ).awaitFinished()
            if (res.outcome !is BuildOutcome.Success) {
                error("Failed to initialize source files: ${res.toOutputString()}")
            }
        }
    }

    @AfterAll
    fun cleanupAll() {
        provider.close()
        buildManager.close()
        if (::complexProject.isInitialized) {
            complexProject.close()
        }
    }

    @Test
    fun `ReplEnvironmentService resolves environment for Kotlin JVM`() = runTest(timeout = 300.seconds) {
        val env = resolveEnv(
            complexProject,
            projectPath = ":kotlin-jvm",
            sourceSet = "main",
            additionalDependencies = listOf("org.jetbrains.kotlin:kotlin-reflect:${TestFixturesBuildConfig.KOTLIN_VERSION}")
        )

        assert(env.javaExecutable.isNotBlank())
        assert(env.config.classpath.isNotEmpty())
        assert(env.config.pluginsClasspath.isNotEmpty())
        assertTrue(env.config.classpath.any { it.contains("kotlin-reflect") }, "Classpath should contain kotlin-reflect")
    }

    @Test
    fun `ReplEnvironmentService extracts JVM target from toolchain`() = runTest(timeout = 300.seconds) {
        val env = resolveEnv(complexProject, projectPath = ":kotlin-jvm")
        val args = env.config.compilerArgs
        println("[DEBUG_LOG] Kotlin-JVM Compiler Args: ${args}")
        assertTrue(args.contains("-jvm-target"), "Compiler args should contain -jvm-target. Args: $args")
        assertTrue(args.contains("17"), "JVM target should be 17. Args: $args")
    }

    @Test
    fun `ReplEnvironmentService resolves environment for Java`() = runTest(timeout = 300.seconds) {
        val env = resolveEnv(
            complexProject,
            projectPath = ":java-only",
            sourceSet = "main",
            additionalDependencies = listOf("org.jetbrains.kotlin:kotlin-reflect:${TestFixturesBuildConfig.KOTLIN_VERSION}")
        )

        assert(env.javaExecutable.isNotBlank())
        assert(env.config.classpath.isNotEmpty())
        assertTrue(env.config.classpath.any { it.contains("kotlin-reflect") }, "Classpath should contain kotlin-reflect")

        val args = env.config.compilerArgs
        println("[DEBUG_LOG] Java-only Compiler Args: ${args}")
    }

    @Test
    fun `ReplEnvironmentService resolves environment for Kotlin Multiplatform`() = runTest(timeout = 300.seconds) {
        val env = resolveEnv(
            complexProject,
            projectPath = ":kmp-project",
            sourceSet = "jvmMain",
            additionalDependencies = listOf("org.jetbrains.kotlin:kotlin-reflect:${TestFixturesBuildConfig.KOTLIN_VERSION}")
        )

        assert(env.javaExecutable.isNotBlank())
        assert(env.config.classpath.isNotEmpty())
        assert(env.config.pluginsClasspath.isNotEmpty())
        assertTrue(env.config.classpath.any { it.contains("kotlin-reflect") }, "Classpath should contain kotlin-reflect")
    }

    @Test
    fun `ReplEnvironmentService prefers Kotlin source set over Java one`() = runTest(timeout = 300.seconds) {
        val env = resolveEnv(complexProject, projectPath = ":kotlin-java-mixed")
        assert(env.javaExecutable.isNotBlank())
    }

    @Test
    fun `repl-env init script ensures compiled sources are built and included`() = runTest(timeout = 300.seconds) {
        val env = resolveEnv(complexProject, projectPath = ":kotlin-jvm")
        val classpath = env.config.classpath

        val matchingClassesDirs = classpath.filter { (it.contains("classes") || it.contains("bin")) && (it.contains("main") || it.contains("kotlin-jvm")) }
        assertTrue(matchingClassesDirs.isNotEmpty(), "Classpath should contain classes directory for kotlin-jvm. Classpath: $classpath")
        assertTrue(matchingClassesDirs.any { File(it).exists() }, "At least one classes directory should exist. Matching: $matchingClassesDirs")
    }

    @Test
    fun `repl-env init script ensures compiled sources are built even when clean`() = runTest(timeout = 300.seconds) {
        runGradle(complexProject, ":clean-test:clean")
        val env = resolveEnv(complexProject, projectPath = ":clean-test")

        val classpath = env.config.classpath
        val matchingClassesDirs = classpath.filter { (it.contains("classes") || it.contains("bin")) && (it.contains("main") || it.contains("clean-test")) }
        assertTrue(matchingClassesDirs.isNotEmpty(), "Classpath should contain classes directory after clean. Classpath: $classpath")
        assertTrue(matchingClassesDirs.any { File(it).exists() }, "At least one classes directory should exist after clean. Matching: $matchingClassesDirs")
    }

    @Test
    fun `repl-env init script ensures KMP compiled sources are built and included`() = runTest(timeout = 300.seconds) {
        runGradle(complexProject, ":kmp-project:clean")
        val env = resolveEnv(complexProject, projectPath = ":kmp-project", sourceSet = "jvmMain")

        val classpath = env.config.classpath
        val matchingClassesDirs = classpath.filter { (it.contains("classes") || it.contains("bin")) && (it.contains("jvm") || it.contains("main") || it.contains("kmp-project")) }
        assertTrue(matchingClassesDirs.isNotEmpty(), "Classpath should contain KMP classes directory. Classpath: $classpath")
        assertTrue(matchingClassesDirs.any { File(it).exists() }, "At least one KMP classes directory should exist. Matching: $matchingClassesDirs")
    }

    @Test
    fun `resolveReplEnvironment task is never up to date`() = runTest(timeout = 300.seconds) {
        val env1 = resolveEnv(complexProject, projectPath = ":kotlin-jvm")
        assert(env1.javaExecutable.isNotBlank())

        val env2 = resolveEnv(complexProject, projectPath = ":kotlin-jvm")
        assert(env2.javaExecutable.isNotBlank())
    }

    private suspend fun runGradle(
        project: GradleProjectFixture,
        vararg tasks: String
    ) {
        val projectRoot = GradleProjectRoot(project.pathString())
        val args = GradleInvocationArguments(
            additionalArguments = tasks.toList(),
            additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
        ).withInitScript("task-out")

        val runningBuild = provider.runBuild(
            projectRoot = projectRoot,
            args = args
        )
        val result = runningBuild.awaitFinished()
        assert(result.outcome is BuildOutcome.Success)
    }

    @Test
    fun `ReplEnvironmentService extracts free compiler args`() = runTest(timeout = 300.seconds) {
        val env = resolveEnv(complexProject, projectPath = ":compiler-args")

        val args = env.config.compilerArgs
        println("[DEBUG_LOG] Compiler Args: $args")
        assert(args.contains("-Xjvm-default=all"))

        val cp = env.config.pluginsClasspath
        println("[DEBUG_LOG] Compiler Classpath: $cp")
        val hasSerializationPluginInCP = cp.any { it.contains("kotlin-serialization") }
        assert(hasSerializationPluginInCP)
    }

    private suspend fun resolveEnv(
        project: GradleProjectFixture,
        projectPath: String = ":",
        sourceSet: String = "main",
        additionalDependencies: List<String> = emptyList()
    ): ReplConfigWithJava {
        val projectRoot = GradleProjectRoot(project.pathString())
        val env = with(dev.rnett.gradle.mcp.ProgressReporter.PRINTLN) {
            replEnvService.resolveReplEnvironment(
                projectRoot = projectRoot,
                projectPath = projectPath,
                sourceSet = sourceSet,
                additionalDependencies = additionalDependencies
            )
        }
        return env
    }
}
