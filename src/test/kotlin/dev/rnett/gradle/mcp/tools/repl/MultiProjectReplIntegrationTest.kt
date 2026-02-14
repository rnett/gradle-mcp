package dev.rnett.gradle.mcp.tools.repl

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.gradle.fixtures.testMultiProjectBuild
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class MultiProjectReplIntegrationTest : BaseReplIntegrationTest() {

    @BeforeAll
    fun setupMultiProject() = runBlocking {
        initProject(testMultiProjectBuild(listOf("frontend", "backend")) {
            subproject(
                "frontend",
                buildScript = """
                    plugins {
                        kotlin("jvm") version "${BuildConfig.KOTLIN_VERSION}"
                    }
                    
                    repositories {
                        mavenCentral()
                    }
                """.trimIndent()
            )
            file(
                "frontend/src/main/kotlin/com/example/frontend/FrontendUtil.kt", """
                    package com.example.frontend
                    object FrontendUtil {
                        fun getMessage() = "Hello from Frontend"
                    }
                """.trimIndent()
            )
        })
    }

    @Test
    fun `repl starts in subproject`() = runTest(timeout = 10.minutes) {
        startRepl(projectPath = ":frontend", sourceSet = "main")
        val result = runSnippet("com.example.frontend.FrontendUtil.getMessage()")
        assertTrue(result.contains("Hello from Frontend"), "Expected 'Hello from Frontend', but got: $result")
    }
}
