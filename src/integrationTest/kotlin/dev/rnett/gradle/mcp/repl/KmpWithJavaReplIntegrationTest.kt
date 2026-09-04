package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.fixtures.gradle.testGradleProject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Regression test for KMP projects with Java sources compiled (the historical `withJava()` setup).
 *
 * The jvm target's `test` compilation is routed to via its Kotlin source set name `jvmTest`
 * (the init script's KMP branch matches compilations by `getAllKotlinSourceSets` names). For that
 * compilation, main (`jvmMain`) output is present in `runtimeDependencyFiles` but NOT in the
 * resolvable `jvmTestRuntimeClasspath` configuration, so the repl-env init script must prefer the
 * FileCollection for a `jvmTest` REPL to see main classes.
 *
 * Note on `withJava()`: since the Kotlin version used by these fixtures (2.4.10), the multiplatform
 * plugin always configures Java sources compilation and `withJava()` is deprecated. A plain `jvm()`
 * target therefore provides the same java-plugin source-set wiring (java source sets `jvmMain` and
 * `jvmTest`, with main output on the test compilation's runtime classpath).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KmpWithJavaReplIntegrationTest : BaseReplIntegrationTest() {

    @BeforeAll
    fun setupKmpWithJavaProject() = runBlocking {
        initProject(testGradleProject {
            buildScript(
                """
            plugins {
                kotlin("multiplatform") version "${TestFixturesBuildConfig.KOTLIN_VERSION}"
            }
            
            repositories {
                mavenCentral()
            }
            
            kotlin {
                jvm()
            }
        """.trimIndent()
            )
            file(
                "src/jvmMain/kotlin/com/example/MainClass.kt", """
                package com.example

                object MainClass {
                    val message = "Hello from KMP JVM main"

                    fun describe() = "MainClass says ${'$'}message"
                }
                """.trimIndent()
            )
            file(
                "src/jvmTest/kotlin/com/example/TestMain.kt", """
                package com.example

                object TestMain {
                    // Static init referencing a jvmMain class: loading TestMain fails with
                    // NoClassDefFoundError: com.example.MainClass when jvmMain output is missing
                    // from the REPL classpath (i.e. only the jvmTestRuntimeClasspath configuration
                    // was used).
                    val initialized = MainClass.message
                }
                """.trimIndent()
            )
        })
        startRepl(sourceSet = "jvmTest")
    }

    @Test
    @Order(1)
    fun `jvmTest source set REPL can load jvmMain classes`() = runTest(timeout = 10.minutes) {
        val result = runSnippet("com.example.MainClass.message")
        assertTrue(result.contains("Hello from KMP JVM main"), "Expected 'Hello from KMP JVM main', but got: $result")
    }

    @Test
    @Order(2)
    fun `jvmTest class static init referencing jvmMain classes works`() = runTest(timeout = 10.minutes) {
        val result = runSnippet("com.example.TestMain.initialized")
        assertTrue(result.contains("Hello from KMP JVM main"), "Expected 'Hello from KMP JVM main', but got: $result")
    }

    @Test
    @Order(3)
    fun `jvmMain REPL with additional dependencies resolves main classes and the additional dependency`() = runTest(timeout = 10.minutes) {
        // Re-start on the main source set WITH additionalDependencies to exercise the detached
        // gradleMcpReplClasspath configuration union with the compilation's runtime files on main.
        startRepl(
            sourceSet = "jvmMain",
            additionalDependencies = listOf("org.jetbrains.kotlin:kotlin-reflect:${TestFixturesBuildConfig.KOTLIN_VERSION}")
        )

        val mainResult = runSnippet("com.example.MainClass.describe()")
        assertTrue(
            mainResult.contains("MainClass says Hello from KMP JVM main"),
            "Expected project main class to resolve on the jvmMain REPL, but got: $mainResult"
        )

        // kotlin-reflect is only on the classpath via additionalDependencies: memberFunctions
        // resolving the project class proves the additional dependency made it into the union.
        val reflectResult = runSnippet(
            """
            import kotlin.reflect.full.memberFunctions
            com.example.MainClass::class.memberFunctions.map { it.name }
            """.trimIndent()
        )
        assertTrue(
            reflectResult.contains("describe"),
            "Expected kotlin-reflect memberFunctions to see 'describe' on MainClass, but got: $reflectResult"
        )
    }
}
