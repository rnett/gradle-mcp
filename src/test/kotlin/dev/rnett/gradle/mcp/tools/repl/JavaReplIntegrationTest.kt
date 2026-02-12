package dev.rnett.gradle.mcp.tools.repl

import dev.rnett.gradle.mcp.gradle.fixtures.testJavaProject
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
class JavaReplIntegrationTest : BaseReplIntegrationTest() {

    @BeforeAll
    fun setupJavaProject() = runBlocking {
        initProject(testJavaProject {
            file(
                "src/main/java/com/example/Util.java", """
            package com.example;
            public class Util {
                public static String getMessage() { return "Hello from Java"; }
            }
        """.trimIndent()
            )
        })
        startRepl()
    }

    @Test
    @Order(1)
    fun `basic execution works`() = runTest(timeout = 10.minutes) {
        val result = runSnippet("1 + 1")
        assertTrue(result.contains("2"), "Expected '2', but got: $result")
    }

    @Test
    @Order(2)
    fun `access to project classes works`() = runTest(timeout = 10.minutes) {
        val result = runSnippet("com.example.Util.getMessage()")
        assertTrue(result.contains("Hello from Java"), "Expected 'Hello from Java', but got: $result")
    }

    @Test
    @Order(3)
    fun `Responder works`() = runTest(timeout = 10.minutes) {
        val result = runSnippet("responder.render(\"manual response\")")
        assertTrue(result.contains("manual response"), "Expected 'manual response', but got: $result")
    }

    @Test
    @Order(4)
    fun `println works`() = runTest(timeout = 10.minutes) {
        val result = runSnippet("println(\"printed message\")")
        assertTrue(result.contains("printed message"), "Expected 'printed message', but got: $result")
    }

    @Test
    @Order(5)
    fun `SLF4J logging works`() = runTest(timeout = 10.minutes) {
        runSnippet(
            """
                val logger = org.slf4j.LoggerFactory.getLogger("test-logger")
                logger.info("slf4j info message")
            """.trimIndent()
        )
    }

    @Test
    @Order(6)
    fun `AWT Image passing works`() = runTest(timeout = 10.minutes) {
        runSnippetAndAssertImage(
            """
                    import java.awt.image.BufferedImage
                    import java.awt.Color
                    val img = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
                    val g = img.createGraphics()
                    g.color = Color.RED
                    g.fillRect(0, 0, 10, 10)
                    g.dispose()
                    img
                """.trimIndent(),
            "java-awt-red.png"
        )
    }
}
