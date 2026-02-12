package dev.rnett.gradle.mcp.tools.repl

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.gradle.fixtures.testGradleProject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ComposeReplIntegrationTest : BaseReplIntegrationTest() {

    @BeforeAll
    fun setupComposeProject() = runBlocking {
        initProject(testGradleProject {
            buildScript(
                """
            plugins {
                kotlin("jvm") version "${BuildConfig.KOTLIN_VERSION}"
                id("org.jetbrains.compose") version "${BuildConfig.COMPOSE_VERSION}"
                id("org.jetbrains.kotlin.plugin.compose") version "${BuildConfig.KOTLIN_VERSION}"
            }
            
            repositories {
                mavenCentral()
                google()
                maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
            }
            
            dependencies {
                implementation(compose.desktop.currentOs)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.desktop.uiTestJUnit4)
            }
        """.trimIndent()
            )

            file(
                "src/main/kotlin/com/example/App.kt", """
            package com.example
            import androidx.compose.material.Text
            import androidx.compose.runtime.Composable

            @Composable
            fun App() {
                Text("Hello Compose")
            }
        """.trimIndent()
            )
        })
        startRepl()
    }

    @Test
    @Order(1)
    @Ignore
    fun `Compose UI test works in REPL`() = runTest(timeout = 10.minutes) {
        // Test Compose rendering via UI test
        val result = runSnippet(
            """
                    import androidx.compose.ui.test.*
                    import androidx.compose.ui.graphics.*
                    import com.example.App
                    import dev.rnett.gradle.mcp.repl.Responder
                    
                    @OptIn(ExperimentalTestApi::class)
                    runComposeUiTest {
                        setContent {
                            App()
                        }
                        val node = onNodeWithText("Hello Compose")
                        node.assertExists()
                        
                        val bitmap = node.captureToImage()
                        bitmap // Should be rendered as image/png
                    }
                """.trimIndent()
        )
        assertTrue(result.contains("image/png"), "Expected image/png response, but got: $result")
    }
}
