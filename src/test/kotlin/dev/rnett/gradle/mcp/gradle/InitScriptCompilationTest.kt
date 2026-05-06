package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.fixtures.gradle.GradleProjectFixture
import dev.rnett.gradle.mcp.fixtures.gradle.testGradleProject
import dev.rnett.gradle.mcp.fixtures.gradle.withTestGradleDefaults
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.tools.toOutputString
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InitScriptCompilationTest {

    private lateinit var buildManager: BuildManager
    private lateinit var provider: DefaultGradleProvider
    private lateinit var project: GradleProjectFixture

    @BeforeAll
    fun setupAll() {
        buildManager = BuildManager()
        provider = DefaultGradleProvider(GradleConfiguration(), buildManager = buildManager)
        project = testGradleProject {
            buildScript("")
        }
    }

    @AfterAll
    fun cleanupAll() {
        provider.close()
        buildManager.close()
        if (::project.isInitialized) {
            project.close()
        }
    }

    companion object {
        @JvmStatic
        fun ktsInitScriptNames() = InitScriptProvider.allInitScripts().filter { it.endsWith(".kts") }.map { it.substringBefore('.') }
    }

    @ParameterizedTest(name = "{0} init script compiles with allWarningsAsErrors=true")
    @MethodSource("ktsInitScriptNames")
    fun `init script compiles with allWarningsAsErrors=true`(scriptName: String) = runTest(timeout = 300.seconds) {
        val projectRoot = GradleProjectRoot(project.pathString())
        val args = GradleInvocationArguments(
            additionalArguments = listOf("help"),
        ).withTestGradleDefaults(
            additionalSystemProps = mapOf("org.gradle.kotlin.dsl.allWarningsAsErrors" to "true")
        ).withInitScript(scriptName)

        val result = provider.runBuild(projectRoot, args).awaitFinished()
        assert(result.outcome is BuildOutcome.Success) {
            "$scriptName init script failed to compile with allWarningsAsErrors=true:\n${result.toOutputString()}"
        }
    }
}
