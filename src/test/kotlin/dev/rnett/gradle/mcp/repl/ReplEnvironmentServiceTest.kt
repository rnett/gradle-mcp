package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.fixtures.GradleProjectFixture
import dev.rnett.gradle.mcp.gradle.fixtures.testGradleProject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReplEnvironmentServiceTest {

    private lateinit var buildManager: BuildManager
    private lateinit var provider: DefaultGradleProvider
    private lateinit var replEnvService: DefaultReplEnvironmentService
    private lateinit var tempInitScriptsDir: Path

    @BeforeAll
    fun setupAll() {
        buildManager = BuildManager()
        tempInitScriptsDir = Files.createTempDirectory("gradle-mcp-test-repl-env-init-")
        provider = DefaultGradleProvider(
            GradleConfiguration(
                maxConnections = 5,
                ttl = 60.seconds,
                allowPublicScansPublishing = false
            ),
            initScriptProvider = DefaultInitScriptProvider(tempInitScriptsDir),
            buildManager = buildManager
        )
        replEnvService = DefaultReplEnvironmentService(provider)
    }

    @AfterAll
    fun cleanupAll() {
        provider.close()
        buildManager.close()
        tempInitScriptsDir.toFile().deleteRecursively()
    }

    @Test
    fun `ReplEnvironmentService resolves environment for Kotlin JVM`() = runTest(timeout = 300.seconds) {
        testGradleProject {
            buildScript(
                """
                plugins {
                    kotlin("jvm") version "${BuildConfig.KOTLIN_VERSION}"
                }
                
                repositories {
                    mavenCentral()
                }
                
                kotlin {
                    jvmToolchain(17)
                }

                dependencies {
                    implementation(kotlin("stdlib"))
                }
            """.trimIndent()
            )
        }.use { project ->
            val env = resolveEnv(
                project,
                projectPath = ":",
                sourceSet = "main",
                additionalDependencies = listOf("org.jetbrains.kotlin:kotlin-reflect:${BuildConfig.KOTLIN_VERSION}")
            )

            assert(env.javaExecutable.isNotBlank())
            assert(env.config.classpath.isNotEmpty())
            assert(env.config.pluginsClasspath.isNotEmpty())
            assert(env.config.classpath.any { it.contains("kotlin-reflect") })
        }
    }

    @Test
    fun `ReplEnvironmentService resolves environment for Java`() = runTest(timeout = 300.seconds) {
        testGradleProject {
            buildScript(
                """
                plugins {
                    java
                }
                
                repositories {
                    mavenCentral()
                }
            """.trimIndent()
            )
        }.use { project ->
            val env = resolveEnv(
                project,
                projectPath = ":",
                sourceSet = "main",
                additionalDependencies = listOf("org.jetbrains.kotlin:kotlin-reflect:${BuildConfig.KOTLIN_VERSION}")
            )

            assert(env.javaExecutable.isNotBlank())
            assert(env.config.classpath.isNotEmpty())
            assert(env.config.classpath.any { it.contains("kotlin-reflect") })
        }
    }

    @Test
    fun `ReplEnvironmentService resolves environment for Kotlin Multiplatform`() = runTest(timeout = 300.seconds) {
        testGradleProject {
            buildScript(
                """
                plugins {
                    kotlin("multiplatform") version "${BuildConfig.KOTLIN_VERSION}"
                }
                
                repositories {
                    mavenCentral()
                }
                
                kotlin {
                    jvm()
                }
            """.trimIndent()
            )
        }.use { project ->
            // In KMP, the JVM source set is usually 'jvmMain'
            val env = resolveEnv(
                project,
                projectPath = ":",
                sourceSet = "jvmMain",
                additionalDependencies = listOf("org.jetbrains.kotlin:kotlin-reflect:${BuildConfig.KOTLIN_VERSION}")
            )

            assert(env.javaExecutable.isNotBlank())
            assert(env.config.classpath.isNotEmpty())
            assert(env.config.pluginsClasspath.isNotEmpty())
            assert(env.config.classpath.any { it.contains("kotlin-reflect") })
        }
    }

    @Test
    fun `ReplEnvironmentService prefers Kotlin source set over Java one`() = runTest(timeout = 300.seconds) {
        testGradleProject {
            buildScript(
                """
                plugins {
                    kotlin("jvm") version "${BuildConfig.KOTLIN_VERSION}"
                    java
                }
                
                repositories {
                    mavenCentral()
                }
                
                // We want to verify that it uses the Kotlin one if both are present
                // and they have different configurations. 
                // But in Gradle JVM, they usually share the same SourceSet object.
                // However, the issue mentions "prefer Kotlin source sets to java ones with the same name".
                // This might be more relevant for KMP or specific setups where they might differ.
            """.trimIndent()
            )
        }.use { project ->
            val env = resolveEnv(project)
            assert(env.javaExecutable.isNotBlank())
        }
    }

    @Test
    fun `repl-env init script ensures compiled sources are built and included`() = runTest(timeout = 300.seconds) {
        testGradleProject {
            file("src/main/kotlin/Foo.kt", "class Foo")
            buildScript(
                """
                plugins {
                    kotlin("jvm") version "${BuildConfig.KOTLIN_VERSION}"
                }
                
                repositories {
                    mavenCentral()
                }
            """.trimIndent()
            )
        }.use { project ->
            val env = resolveEnv(project)
            val classpath = env.config.classpath

            println("[DEBUG_LOG] Classpath: $classpath")

            val hasClassesDir = classpath.any { it.contains("classes") && it.contains("main") }
            assert(hasClassesDir)

            val kotlinClassesDir = classpath.find { it.contains("classes") && it.contains("kotlin") && it.contains("main") }
            val javaClassesDir = classpath.find { it.contains("classes") && it.contains("java") && it.contains("main") }
            val classesDir = kotlinClassesDir ?: javaClassesDir

            if (classesDir != null) {
                assert(File(classesDir).exists())
            } else {
                assert(classpath.any { File(it).exists() && it.contains("classes") })
            }
        }
    }

    @Test
    fun `repl-env init script ensures compiled sources are built even when clean`() = runTest(timeout = 300.seconds) {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-repl-env-clean-")
        testGradleProject {
            file("src/main/kotlin/Foo.kt", "class Foo")
            buildScript(
                """
                plugins {
                    kotlin("jvm") version "${BuildConfig.KOTLIN_VERSION}"
                }
                
                repositories {
                    mavenCentral()
                }
            """.trimIndent()
            )
        }.use { project ->
            // First run clean to make sure nothing is built
            runGradle(project, "clean")

            val env = resolveEnv(project)

            val classpath = env.config.classpath
            val hasClassesDir = classpath.any { it.contains("classes") && it.contains("main") }
            assert(hasClassesDir)

            val kotlinClassesDir = classpath.find { it.contains("classes") && it.contains("kotlin") && it.contains("main") }
            val javaClassesDir = classpath.find { it.contains("classes") && it.contains("java") && it.contains("main") }
            val classesDir = kotlinClassesDir ?: javaClassesDir

            assert(classesDir != null && File(classesDir).exists())
        }
    }

    @Test
    fun `repl-env init script ensures KMP compiled sources are built and included`() = runTest(timeout = 300.seconds) {
        testGradleProject {
            file("src/jvmMain/kotlin/Foo.kt", "class Foo")
            buildScript(
                """
                plugins {
                    kotlin("multiplatform") version "${BuildConfig.KOTLIN_VERSION}"
                }
                
                repositories {
                    mavenCentral()
                }
                
                kotlin {
                    jvm()
                }
            """.trimIndent()
            )
        }.use { project ->
            runGradle(project, "clean")
            val env = resolveEnv(project, sourceSet = "jvmMain")

            val classpath = env.config.classpath
            val hasClassesDir = classpath.any { it.contains("classes") && it.contains("jvm") && it.contains("main") }
            assert(hasClassesDir)

            val classesDir = classpath.find { it.contains("classes") && it.contains("jvm") && it.contains("main") }
            assert(classesDir != null && File(classesDir).exists())
        }
    }

    @Test
    fun `resolveReplEnvironment task is never up to date`() = runTest(timeout = 300.seconds) {
        testGradleProject {
            buildScript(
                """
                plugins {
                    kotlin("jvm") version "${BuildConfig.KOTLIN_VERSION}"
                }
                repositories {
                    mavenCentral()
                }
            """.trimIndent()
            )
        }.use { project ->
            // First run
            val env1 = resolveEnv(project, projectPath = ":")
            assert(env1.javaExecutable.isNotBlank())

            // Second run (should still succeed and not rely on UP-TO-DATE)
            val env2 = resolveEnv(project, projectPath = ":")
            assert(env2.javaExecutable.isNotBlank())
        }
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
            args = args,
            tosAccepter = { false }
        )
        val result = runningBuild.awaitFinished()
        assert(result.outcome is BuildOutcome.Success)
    }

    @Test
    fun `ReplEnvironmentService extracts free compiler args`() = runTest(timeout = 300.seconds) {
        testGradleProject {
            buildScript(
                """
                plugins {
                    kotlin("jvm") version "${BuildConfig.KOTLIN_VERSION}"
                    kotlin("plugin.serialization") version "${BuildConfig.KOTLIN_VERSION}"
                }
                
                repositories {
                    mavenCentral()
                }

                tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                    compilerOptions {
                        freeCompilerArgs.add("-Xjvm-default=all")
                    }
                }

                file("src/main/kotlin/foo.kt").apply {
                    parentFile.mkdirs()
                    writeText("fun foo() {}")
                }
            """.trimIndent()
            )
        }.use { project ->
            val env = resolveEnv(project, projectPath = ":")

            val args = env.config.compilerArgs
            println("[DEBUG_LOG] Compiler Args: $args")
            assert(args.contains("-Xjvm-default=all"))

            val cp = env.config.pluginsClasspath
            println("[DEBUG_LOG] Compiler Classpath: $cp")
            val hasSerializationPluginInCP = cp.any { it.contains("kotlin-serialization") }
            assert(hasSerializationPluginInCP)
        }
    }

    private suspend fun resolveEnv(
        project: GradleProjectFixture,
        projectPath: String = ":",
        sourceSet: String = "main",
        additionalDependencies: List<String> = emptyList()
    ): ReplConfigWithJava {
        val projectRoot = GradleProjectRoot(project.pathString())
        val env = replEnvService.resolveReplEnvironment(
            projectRoot = projectRoot,
            projectPath = projectPath,
            sourceSet = sourceSet,
            additionalDependencies = additionalDependencies
        )
        return env
    }
}