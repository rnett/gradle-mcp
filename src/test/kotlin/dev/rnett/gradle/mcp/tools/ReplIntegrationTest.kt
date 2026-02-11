package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.gradle.BackgroundBuildManager
import dev.rnett.gradle.mcp.gradle.BuildResults
import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.gradle.fixtures.testGradleProject
import dev.rnett.gradle.mcp.gradle.fixtures.testJavaProject
import dev.rnett.gradle.mcp.gradle.fixtures.testKotlinProject
import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import dev.rnett.gradle.mcp.repl.DefaultReplManager
import dev.rnett.gradle.mcp.repl.ReplManager
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ReplIntegrationTest : BaseMcpServerTest() {

    override fun createTestModule() = module {
        single { DI.json }
        single { GradleConfiguration(4, 10.minutes, false) }
        single { DefaultInitScriptProvider(tempDir.resolve("init-scripts")) } bind InitScriptProvider::class
        single { DefaultBundledJarProvider(tempDir.resolve("jars")) } bind BundledJarProvider::class
        single { BackgroundBuildManager() }
        single { BuildResults(get()) }
        single<ReplManager> { DefaultReplManager(get()) }
        single<GradleProvider> {
            DefaultGradleProvider(
                get(),
                initScriptProvider = get(),
                backgroundBuildManager = get(),
                buildResults = get()
            )
        }
        single {
            DI.components(get(), get())
        }
        single {
            DI.createServer(get(), get())
        }
    }

    private suspend fun runSnippet(code: String): String {
        val response = server.client.callTool(
            "repl", mapOf(
                "command" to "run",
                "code" to code
            )
        ) as CallToolResult
        assert(!response.isError!!) { "Snippet failed: ${(response.content.first() as TextContent).text}" }
        return (response.content.first() as TextContent).text!!
    }

    @Test
    fun `REPL works in a Java project`() = runTest(timeout = 10.minutes) {
        println("[DEBUG_LOG] Starting Java project REPL test")
        testJavaProject {
            file(
                "src/main/java/com/example/Util.java", """
                package com.example;
                public class Util {
                    public static String getMessage() { return "Hello from Java"; }
                }
            """.trimIndent()
            )
        }.use { project ->
            try {
                server.setServerRoots(Root(project.path().toUri().toString(), "root"))

                val startResponse = server.client.callTool(
                    "repl", mapOf(
                        "command" to "start",
                        "projectPath" to ":",
                        "sourceSet" to "main"
                    )
                ) as CallToolResult
                assertTrue((startResponse.content.first() as TextContent).text!!.startsWith("REPL session started"), "Expected REPL to start, but got: ${(startResponse.content.first() as TextContent).text}")

                // Test basic execution
                val result1 = runSnippet("1 + 1")
                assertTrue(result1.contains("2"), "Expected '2', but got: $result1")

                // Test access to project classes
                val result2 = runSnippet("com.example.Util.getMessage()")
                assertTrue(result2.contains("Hello from Java"), "Expected 'Hello from Java', but got: $result2")

                // Test Responder
                val result3 = runSnippet("responder.respond(\"manual response\")")
                assertTrue(result3.contains("manual response"), "Expected 'manual response', but got: $result3")

                // Test println (captured via stdout)
                val result4 = runSnippet("println(\"printed message\")")
                assertTrue(result4.contains("printed message"), "Expected 'printed message', but got: $result4")

                // Test SLF4J logging
                val result5 = runSnippet(
                    """
                val logger = org.slf4j.LoggerFactory.getLogger("test-logger")
                logger.info("slf4j info message")
            """.trimIndent()
                )
            } finally {
                server.client.callTool("repl", mapOf("command" to "stop"))
            }
        }
    }

    @Test
    fun `REPL works in a Kotlin project`() = runTest(timeout = 5.minutes) {
        testKotlinProject {
            file(
                "src/main/kotlin/com/example/Util.kt", """
                package com.example
                object Util {
                    fun getMessage() = "Hello from Kotlin"
                }
            """.trimIndent()
            )
        }.use { project ->
            try {
                server.setServerRoots(Root(project.path().toUri().toString(), "root"))

                val startResponse = server.client.callTool(
                    "repl", mapOf(
                        "command" to "start",
                        "projectPath" to ":",
                        "sourceSet" to "main"
                    )
                ) as CallToolResult
                assertTrue((startResponse.content.first() as TextContent).text!!.startsWith("REPL session started"), "Expected REPL to start, but got: ${(startResponse.content.first() as TextContent).text}")

                val result = runSnippet("com.example.Util.getMessage()")
                assertTrue(result.contains("Hello from Kotlin"), "Expected 'Hello from Kotlin', but got: $result")
            } finally {
                server.client.callTool("repl", mapOf("command" to "stop"))
            }
        }
    }

    @Test
    fun `REPL works in a KMP project with JVM target`() = runTest(timeout = 5.minutes) {
        testGradleProject {
            buildScript(
                """
                plugins {
                    kotlin("multiplatform") version "2.0.0"
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
        }.use { project ->
            try {
                server.setServerRoots(Root(project.path().toUri().toString(), "root"))

                val startResponse = server.client.callTool(
                    "repl", mapOf(
                        "command" to "start",
                        "projectPath" to ":",
                        "sourceSet" to "jvmMain"
                    )
                ) as CallToolResult
                assertTrue((startResponse.content.first() as TextContent).text!!.startsWith("REPL session started"), "Expected REPL to start, but got: ${(startResponse.content.first() as TextContent).text}")

                val result = runSnippet("com.example.Util.getMessage()")
                assertTrue(result.contains("Hello from KMP JVM"), "Expected 'Hello from KMP JVM', but got: $result")
            } finally {
                server.client.callTool("repl", mapOf("command" to "stop"))
            }
        }
    }
}
