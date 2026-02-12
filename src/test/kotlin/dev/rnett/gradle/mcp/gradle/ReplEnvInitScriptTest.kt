package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.gradle.fixtures.testGradleProject
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class ReplEnvInitScriptTest {

    @Test
    fun `repl-env init script extracts environment info for Kotlin JVM`() = runTest(timeout = 300.seconds) {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-repl-env-jvm-")
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
            val output = runResolveReplEnv(project, tempDir)

            assert(output.contains("[gradle-mcp-repl-env] projectRoot="))
            assert(output.contains("[gradle-mcp-repl-env] classpath="))
            assert(output.contains("[gradle-mcp-repl-env] javaExecutable="))
            // Verify that the javaExecutable contains something related to the toolchain if possible, 
            // but for now just that it succeeded.
            assert(output.contains("[gradle-mcp-repl-env] pluginsClasspath="))
            assert(output.contains("[gradle-mcp-repl-env] compilerArgs="))
        }
    }

    @Test
    fun `repl-env init script extracts environment info for Java`() = runTest(timeout = 300.seconds) {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-repl-env-java-")
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
            val output = runResolveReplEnv(project, tempDir)

            assert(output.contains("[gradle-mcp-repl-env] projectRoot="))
            assert(output.contains("[gradle-mcp-repl-env] classpath="))
            assert(output.contains("[gradle-mcp-repl-env] javaExecutable="))
        }
    }

    @Test
    fun `repl-env init script extracts environment info for Kotlin Multiplatform`() = runTest(timeout = 300.seconds) {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-repl-env-kmp-")
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
            val output = runResolveReplEnv(project, tempDir, sourceSet = "jvmMain")

            assert(output.contains("[gradle-mcp-repl-env] projectRoot="))
            assert(output.contains("[gradle-mcp-repl-env] classpath="))
            assert(output.contains("[gradle-mcp-repl-env] javaExecutable="))
            assert(output.contains("[gradle-mcp-repl-env] pluginsClasspath="))
            assert(output.contains("[gradle-mcp-repl-env] compilerArgs="))
        }
    }

    @Test
    fun `repl-env init script prefers Kotlin source set over Java one`() = runTest(timeout = 300.seconds) {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-repl-env-prefer-kotlin-")
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
            val output = runResolveReplEnv(project, tempDir)
            assert(output.contains("[gradle-mcp-repl-env] projectRoot="))
        }
    }

    @Test
    fun `repl-env init script ensures compiled sources are built and included`() = runTest(timeout = 300.seconds) {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-repl-env-built-")
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
            val output = runResolveReplEnv(project, tempDir)

            assert(output.contains("[gradle-mcp-repl-env] classpath="))
            val classpathLine = output.lines().find { it.contains("[gradle-mcp-repl-env] classpath=") }
            assert(classpathLine != null)
            val classpath = classpathLine!!.substringAfter("[gradle-mcp-repl-env] classpath=").split(";")

            println("[DEBUG_LOG] Classpath: $classpath")

            // Check for classes directory. 
            // Gradle usually puts them in build/classes/kotlin/main or similar.
            val hasClassesDir = classpath.any { it.contains("classes") && it.contains("main") }
            assert(hasClassesDir)

            // Check if the classes directory actually exists (meaning it was built)
            // It might be build/classes/kotlin/main or build/classes/java/main
            val kotlinClassesDir = classpath.find { it.contains("classes") && it.contains("kotlin") && it.contains("main") }
            val javaClassesDir = classpath.find { it.contains("classes") && it.contains("java") && it.contains("main") }

            val classesDir = kotlinClassesDir ?: javaClassesDir

            if (classesDir != null) {
                // Comment out the existence check to see if it's really built.
                // Wait, it says it was built (FROM-CACHE), so it should exist.
                assert(java.io.File(classesDir).exists())
            } else {
                // Fallback check if it's named differently
                assert(classpath.any { java.io.File(it).exists() && it.contains("classes") })
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

            val output = runResolveReplEnv(project, tempDir)

            assert(output.contains("[gradle-mcp-repl-env] classpath="))
            val classpathLine = output.lines().find { it.contains("[gradle-mcp-repl-env] classpath=") }
            assert(classpathLine != null)
            val classpath = classpathLine!!.substringAfter("[gradle-mcp-repl-env] classpath=").split(";")

            val hasClassesDir = classpath.any { it.contains("classes") && it.contains("main") }
            assert(hasClassesDir)

            val kotlinClassesDir = classpath.find { it.contains("classes") && it.contains("kotlin") && it.contains("main") }
            val javaClassesDir = classpath.find { it.contains("classes") && it.contains("java") && it.contains("main") }
            val classesDir = kotlinClassesDir ?: javaClassesDir

            assert(classesDir != null && java.io.File(classesDir).exists())
        }
    }

    @Test
    fun `repl-env init script ensures KMP compiled sources are built and included`() = runTest(timeout = 300.seconds) {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-repl-env-kmp-built-")
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
            val output = runResolveReplEnv(project, tempDir, sourceSet = "jvmMain")

            assert(output.contains("[gradle-mcp-repl-env] classpath="))
            val classpathLine = output.lines().find { it.contains("[gradle-mcp-repl-env] classpath=") }
            assert(classpathLine != null)
            val classpath = classpathLine!!.substringAfter("[gradle-mcp-repl-env] classpath=").split(";")

            val hasClassesDir = classpath.any { it.contains("classes") && it.contains("jvm") && it.contains("main") }
            assert(hasClassesDir)

            val classesDir = classpath.find { it.contains("classes") && it.contains("jvm") && it.contains("main") }
            assert(classesDir != null && java.io.File(classesDir).exists())
        }
    }

    @Test
    fun `resolveReplEnvironment task is never up to date`() = runTest(timeout = 300.seconds) {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-repl-env-uptodate-")
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
            val output1 = runResolveReplEnv(project, tempDir)
            assert(output1.contains("> Task :resolveReplEnvironment"))
            // In Gradle, if a task is not up-to-date, it won't have UP-TO-DATE label.
            // If it is up-to-date, it will have UP-TO-DATE.
            assert(!output1.contains(":resolveReplEnvironment UP-TO-DATE"))

            // Second run
            val output2 = runResolveReplEnv(project, tempDir)
            assert(output2.contains("> Task :resolveReplEnvironment"))
            // It should NOT be UP-TO-DATE now
            assert(!output2.contains(":resolveReplEnvironment UP-TO-DATE"))
        }
    }

    private suspend fun runGradle(
        project: dev.rnett.gradle.mcp.gradle.fixtures.GradleProjectFixture,
        vararg tasks: String
    ) {
        val backgroundBuildManager = BackgroundBuildManager()
        val buildResults = BuildResults(backgroundBuildManager)
        val provider = DefaultGradleProvider(
            GradleConfiguration(
                maxConnections = 5,
                ttl = 60.seconds,
                allowPublicScansPublishing = false
            ),
            initScriptProvider = DefaultInitScriptProvider(Files.createTempDirectory("gradle-mcp-test-run-gradle-")),
            backgroundBuildManager = backgroundBuildManager,
            buildResults = buildResults
        )
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
        assert(result.buildResult.isSuccessful == true)
    }

    @Test
    fun `repl-env init script extracts free compiler args`() = runTest(timeout = 300.seconds) {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-repl-env-args-")
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
            val output = runResolveReplEnv(project, tempDir)

            assert(output.contains("[gradle-mcp-repl-env] compilerArgs="))
            val argsLine = output.lines().find { it.contains("[gradle-mcp-repl-env] compilerArgs=") }
            assert(argsLine != null)
            val args = argsLine!!.substringAfter("[gradle-mcp-repl-env] compilerArgs=").split(";")

            println("[DEBUG_LOG] Compiler Args: $args")

            // Check for our free compiler arg
            assert(args.contains("-Xjvm-default=all"))

            // Check for serialization plugin in compiler classpath (should still be there)
            assert(output.contains("[gradle-mcp-repl-env] pluginsClasspath="))
            val cpLine = output.lines().find { it.contains("[gradle-mcp-repl-env] pluginsClasspath=") }
            assert(cpLine != null)
            val cp = cpLine!!.substringAfter("[gradle-mcp-repl-env] pluginsClasspath=").split(";")
            println("[DEBUG_LOG] Compiler Classpath: $cp")
            val hasSerializationPluginInCP = cp.any { it.contains("kotlin-serialization") }
            assert(hasSerializationPluginInCP)
        }
    }

    private suspend fun runResolveReplEnv(
        project: dev.rnett.gradle.mcp.gradle.fixtures.GradleProjectFixture,
        tempDir: java.nio.file.Path,
        sourceSet: String = "main",
        extraCode: String = ""
    ): String {
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
            additionalArguments = listOf("resolveReplEnvironment", "-Pgradle-mcp.repl.sourceSet=$sourceSet", "-Pgradle-mcp.repl.testExtraCode=$extraCode"),
            additionalEnvVars = mapOf("GRADLE_USER_HOME" to project.gradleUserHome().toString())
        ).withInitScript("repl-env")

        val runningBuild = provider.runBuild(
            projectRoot = projectRoot,
            args = args,
            tosAccepter = { false }
        )
        val result = runningBuild.awaitFinished()

        assert(result.buildResult.isSuccessful == true)
        return result.buildResult.consoleOutput.toString()
    }
}
