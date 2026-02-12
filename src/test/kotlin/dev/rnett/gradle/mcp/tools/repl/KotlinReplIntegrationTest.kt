package dev.rnett.gradle.mcp.tools.repl

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.gradle.fixtures.testKotlinProject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KotlinReplIntegrationTest : BaseReplIntegrationTest() {

    @BeforeAll
    fun setupKotlinProject() = runBlocking {
        initProject(testKotlinProject {
            buildScript(
                """
            plugins {
                kotlin("jvm") version "${BuildConfig.KOTLIN_VERSION}"
                kotlin("plugin.serialization") version "${BuildConfig.KOTLIN_VERSION}"
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${BuildConfig.KOTLINX_SERIALIZATION_VERSION}")
            }
        """.trimIndent()
            )
            file(
                "src/main/kotlin/com/example/Util.kt", """
            package com.example
            object Util {
                fun getMessage() = "Hello from Kotlin"
            }
        """.trimIndent()
            )
        })
        startRepl()
    }

    @Test
    @Order(1)
    fun `basic execution works`() = runTest(timeout = 10.minutes) {
        val result = runSnippet("com.example.Util.getMessage()")
        assertTrue(result.contains("Hello from Kotlin"), "Expected 'Hello from Kotlin', but got: $result")
    }

    @Test
    @Order(2)
    fun `Kotlinx Serialization works`() = runTest(timeout = 10.minutes) {
        // Test Kotlinx Serialization
        // We test that it compiles and runs without classloader issues by using a type that doesn't need KType at runtime if possible,
        // or just by making sure the compiler plugin is applied.
        val result = runSnippet(
            """
                    import kotlinx.serialization.*
                    import kotlinx.serialization.json.*
                    
                    @Serializable
                    data class TestData(val name: String, val value: Int)
                    
                    val data = TestData("test", 123)
                    // Just test that @Serializable is recognized by using the serializer extension
                    // If it fails with serializer not found, it means the plugin didn't run.
                    Json.encodeToString(data)
                """.trimIndent()
        )
        assertTrue(result.contains("""{"name":"test","value":123}"""), "Expected JSON, but got: $result")
    }
}
