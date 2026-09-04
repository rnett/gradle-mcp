package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.fixtures.gradle.testGradleProject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Regression test: for kotlin("jvm") projects, main output is present in the `test` source set's
 * runtimeClasspath FileCollection (mirrored by the test compilation's `runtimeDependencyFiles`) but NOT
 * in any resolvable configuration. The repl-env init script must prefer the FileCollection so that a
 * test-source-set REPL can see main classes (historically it died with NoClassDefFoundError on main
 * classes because only the testRuntimeClasspath configuration was used).
 */
class TestSourceSetReplIntegrationTest : BaseReplIntegrationTest() {

    @BeforeAll
    fun setupKotlinProject() = runBlocking {
        initProject(testGradleProject {
            buildScript(
                """
            plugins {
                kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}"
            }
            
            repositories {
                mavenCentral()
            }
        """.trimIndent()
            )
            file(
                "src/main/kotlin/com/example/MainClass.kt", """
                package com.example

                object MainClass {
                    val message = "Hello from Main"
                }
                """.trimIndent()
            )

            file(
                "src/test/kotlin/com/example/TestMain.kt", """
                package com.example

                object TestMain {
                    // Static init referencing a main class: loading TestMain used to fail with
                    // NoClassDefFoundError: com.example.MainClass when main output was missing from
                    // the REPL classpath.
                    val initialized = MainClass.message
                }
                """.trimIndent()
            )
        })
        startRepl(sourceSet = "test")
    }

    @Test
    fun `test source set REPL can load main classes`() = runTest(timeout = 10.minutes) {
        val result = runSnippet("com.example.MainClass.message")
        assertTrue(result.contains("Hello from Main"), "Expected 'Hello from Main', but got: $result")
    }

    @Test
    fun `test source set class static init referencing main classes works`() = runTest(timeout = 10.minutes) {
        val result = runSnippet("com.example.TestMain.initialized")
        assertTrue(result.contains("Hello from Main"), "Expected 'Hello from Main', but got: $result")
    }

    @Test
    fun `test source set REPL with additional dependencies still sees main classes`() = runTest(timeout = 10.minutes) {
        // Re-start with additionalDependencies to exercise the detached gradleMcpReplClasspath
        // configuration union with runtimeDependencyFiles.
        startRepl(sourceSet = "test", additionalDependencies = listOf("org.jetbrains.kotlin:kotlin-reflect:${TestFixturesBuildConfig.KOTLIN_VERSION}"))

        val result = runSnippet("com.example.TestMain.initialized")
        assertTrue(result.contains("Hello from Main"), "Expected 'Hello from Main', but got: $result")
    }
}
