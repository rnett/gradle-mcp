package dev.rnett.gradle.mcp.tools.repl

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.gradle.fixtures.testGradleProject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.Test
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
            import androidx.compose.foundation.background
            import androidx.compose.foundation.layout.Box
            import androidx.compose.foundation.layout.size
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.unit.dp

            @Composable
            fun App() {
                Box(Modifier.size(100.dp).background(Color.Blue))
            }
        """.trimIndent()
            )
        })
    }

    @Test
    @Order(1)
    fun `Compose UI test works in REPL`() = runTest(timeout = 10.minutes) {
        startRepl()
        // Test Compose rendering via UI test
        runSnippetAndAssertImage(
            """
                    import androidx.compose.ui.test.*
                    import androidx.compose.ui.graphics.*
                    import androidx.compose.ui.Modifier
                    import com.example.App
                    import dev.rnett.gradle.mcp.repl.Responder
                    
                    @OptIn(ExperimentalTestApi::class)
                    runComposeUiTest {
                        setContent {
                            App()
                        }
                        val node = onRoot()
                        node.assertExists()
                        
                        val bitmap = node.captureToImage()
                        responder.render(bitmap) // Should be rendered as image/png
                    }
                """.trimIndent(),
            "compose-blue-square.png"
        )
    }
}
