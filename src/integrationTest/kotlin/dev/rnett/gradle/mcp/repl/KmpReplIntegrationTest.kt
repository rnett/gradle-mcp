package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.fixtures.gradle.testGradleProject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KmpReplIntegrationTest : BaseReplIntegrationTest() {

    @BeforeAll
    fun setupKmpProject() = runBlocking {
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
                sourceSets {
                    val jvmMain by getting {
                        dependencies {
                        }
                    }
                }
            }
        """.trimIndent()
            )

            file(
                "src/jvmMain/kotlin/com/example/Util.kt", """
            package com.example
            object Util {
                fun getMessage() = "Hello from KMP JVM"
            }
        """.trimIndent()
            )
        })
        startRepl(sourceSet = "jvmMain")
    }

    @Test
    @Order(1)
    fun `basic execution works`() = runTest(timeout = 10.minutes) {
        val result = runSnippet("com.example.Util.getMessage()")
        assertTrue(result.contains("Hello from KMP JVM"), "Expected 'Hello from KMP JVM', but got: $result")
    }
}
