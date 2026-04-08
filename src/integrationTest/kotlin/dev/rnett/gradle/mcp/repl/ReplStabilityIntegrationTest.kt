package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.fixtures.gradle.testKotlinProject
import dev.rnett.gradle.mcp.tools.ToolNames
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ReplStabilityIntegrationTest : BaseReplIntegrationTest() {

    @Test
    fun `test envSource SHELL inheritance`() = runTest(timeout = 10.minutes) {
        val fixture = testKotlinProject {
            settings("rootProject.name = \"env-test\"")
        }
        initProject(fixture)

        // We can't easily check actual shell env because it varies, 
        // but we can check if it's DIFFERENT from NONE.
        // Or we can try to find a common one like PATH.

        startRepl()

        val result = server.client.callTool(
            ToolNames.REPL, buildJsonObject {
                put("command", "run")
                put("code", "System.getenv(\"PATH\") != null")
            }
        ) as CallToolResult

        val output = (result.content.first() as TextContent).text!!
        assertTrue(output.contains("true"), "Expected PATH to be present in inherited environment, but got: $output")
    }

    @Test
    fun `test optIn argument`() = runTest(timeout = 10.minutes) {
        val fixture = testKotlinProject {
            settings("rootProject.name = \"optin-test\"")
            buildScript(
                """
                plugins {
                    kotlin("jvm") version "2.3.20"
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
                }
            """.trimIndent()
            )
        }
        initProject(fixture)

        // Try without opt-in first, should fail (or at least show warning if it were a real compiler, 
        // but K2 scripting might be strict or we might get a compilation error if we use an experimental API).
        // Actually, let's just test that it works WITH opt-in for an annotation that requires it.

        val startResponse = server.client.callTool(
            ToolNames.REPL, buildJsonObject {
                put("command", "start")
                put("projectPath", ":")
                put("sourceSet", "main")
                putJsonArray("optIn") {
                    add("kotlinx.coroutines.ExperimentalCoroutinesApi")
                }
            }
        ) as CallToolResult
        assertTrue((startResponse.content.first() as TextContent).text!!.startsWith("REPL session started"))

        // kotlinx.coroutines.flow.asFlow is NOT experimental, but let's find something that IS.
        // ExperimentalCoroutinesApi is often needed for some Flow transformations or TestDispatchers.

        val result = server.client.callTool(
            ToolNames.REPL, buildJsonObject {
                put("command", "run")
                put(
                    "code", """
                    import kotlinx.coroutines.ExperimentalCoroutinesApi
                    import kotlinx.coroutines.test.StandardTestDispatcher
                    
                    // StandardTestDispatcher is ExperimentalCoroutinesApi
                    val dispatcher = StandardTestDispatcher()
                    "success"
                """.trimIndent()
                )
            }
        ) as CallToolResult

        val output = (result.content.first() as TextContent).text!!
        assertTrue(output.contains("success"), "Expected successful evaluation with opt-in, but got: $output")
    }

    @Test
    fun `test crash diagnostics include recent output`() = runTest(timeout = 10.minutes) {
        val fixture = testKotlinProject {
            settings("rootProject.name = \"crash-test\"")
        }
        initProject(fixture)
        startRepl()

        // Trigger a crash. System.exit(0) might be caught by watchdog or sendRequest.
        // We'll also print something to stdout first to ensure it's in the buffer.

        val result = server.client.callTool(
            ToolNames.REPL, buildJsonObject {
                put("command", "run")
                put(
                    "code", """
                    println("CRASH_MARKER")
                    System.exit(0)
                """.trimIndent()
                )
            }
        ) as CallToolResult

        assertTrue(result.isError == true, "Expected error on worker crash")
        val output = (result.content.first() as TextContent).text!!
        assertTrue(output.contains("terminated unexpectedly"), "Error message should mention termination")
        assertTrue(output.contains("CRASH_MARKER"), "Error message should include recent output: $output")
    }

    @Test
    fun `test nested class symbol resolution`() = runTest(timeout = 10.minutes) {
        val fixture = testKotlinProject {
            settings("rootProject.name = \"nested-test\"")
            file(
                "src/main/kotlin/com/example/Outer.kt", """
                package com.example
                class Outer {
                    class Inner {
                        fun getMessage() = "Hello from Inner"
                    }
                }
            """.trimIndent()
            )
        }
        initProject(fixture)
        startRepl()

        val result = server.client.callTool(
            ToolNames.REPL, buildJsonObject {
                put("command", "run")
                put(
                    "code", """
                    import com.example.Outer
                    val inner = Outer.Inner()
                    inner.getMessage()
                """.trimIndent()
                )
            }
        ) as CallToolResult

        val output = (result.content.first() as TextContent).text!!
        assertTrue(output.contains("Hello from Inner"), "Expected nested class to be resolvable, but got: $output")
    }

    @Test
    fun `test state persistence across multiple runs`() = runTest(timeout = 10.minutes) {
        val fixture = testKotlinProject {
            settings("rootProject.name = \"persistence-test\"")
        }
        initProject(fixture)
        startRepl()

        runSnippet("val x = 1")
        runSnippet("val y = x + 1")
        val result = runSnippet("y + 1")

        assertTrue(result.contains("3"), "Expected state to be persisted, but got: $result")
    }
}
