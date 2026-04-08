package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.fixtures.gradle.testGradleProject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class JvmTargetReplIntegrationTest : BaseReplIntegrationTest() {

    @BeforeAll
    fun setupProject() = runTest(timeout = 10.minutes) {
        initProject(testGradleProject {
            settings(
                """
                rootProject.name = "root"
                include("lib", "app")
            """.trimIndent()
            )

            subproject(
                "lib", buildScript = """
                plugins { kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
                repositories { mavenCentral() }
                kotlin { jvmToolchain(11) }
            """.trimIndent()
            )

            file(
                "lib/src/main/kotlin/lib/Lib.kt", """
                package lib
                inline fun <T> libInline(block: () -> T): T = block()
                fun getLibMessage() = "Hello from Lib"
            """.trimIndent()
            )

            subproject(
                "app", buildScript = """
                plugins { kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
                repositories { mavenCentral() }
                kotlin { jvmToolchain(17) }
                dependencies { implementation(project(":lib")) }
            """.trimIndent()
            )

            file(
                "app/src/main/kotlin/app/App.kt", """
                package app
                fun getAppMessage() = "Hello from App"
            """.trimIndent()
            )
        })
    }

    @Test
    fun `repl propagates jvm target and allows inlining from higher target dependencies`() = runTest(timeout = 10.minutes) {
        startRepl(projectPath = ":app")

        val result = runSnippet(
            """
            import lib.*
            libInline { "Inline works" }
        """.trimIndent()
        )

        assertTrue(result.contains("Inline works"), "Expected 'Inline works', but got: $result")
    }
}
