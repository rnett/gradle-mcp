package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.dependencies.DefaultGradleDependencyService
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.fixtures.gradle.GradleProjectFixture
import dev.rnett.gradle.mcp.fixtures.gradle.testKotlinProject
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.fixtures.mcp.McpServerFixture
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.tools.ToolNames
import dev.rnett.gradle.mcp.tools.dependencies.GradleDependencyTools
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ConfigurationCacheIntegrationTest : BaseMcpServerTest() {

    private lateinit var _project: GradleProjectFixture

    override fun Scope.createProvider(): GradleProvider {
        return DefaultGradleProvider(
            config = get(),
            buildManager = get(),
            initScriptProvider = get<InitScriptProvider>() as DefaultInitScriptProvider
        )
    }

    override fun createTestModule(): Module = module {
        single<GradleDependencyService> { DefaultGradleDependencyService(get()) }
        single { GradleDependencyTools(get()) }
    }

    override fun createFixture(): McpServerFixture {
        return McpServerFixture(
            clientCapabilities = ClientCapabilities(
                roots = ClientCapabilities.Roots(listChanged = true),
                elicitation = buildJsonObject { }),
            koinModules = listOf(super.createTestModule(), createTestModule())
        )
    }

    @BeforeEach
    override fun setup() = runTest {
        _project = testKotlinProject {
            buildScript(
                """
                plugins {
                    kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}"
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                }
                """.trimIndent()
            )
        }
        super.setup()
        server.setServerRoots(Root(_project.path().toUri().toString(), "root"))
    }

    @AfterEach
    override fun cleanup() = runTest {
        _project.close()
        super.cleanup()
    }

    @Test
    fun `inspect_dependencies works with configuration cache`() = runTest(timeout = 5.minutes) {
        _project.projectDir.resolve("gradle.properties").writeText("org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=fail")

        val result = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            buildJsonObject {
                put("projectRoot", _project.path().absolutePathString())
                put("checkUpdates", false)
            }
        ) as CallToolResult

        assertFalse(result.isError == true, "Tool call should not fail. Error: ${(result.content.firstOrNull() as? TextContent)?.text}")
        val text = (result.content.first() as TextContent).text!!
        if (!text.contains("kotlinx-coroutines-core")) {
            error("Report should contain coroutines. Got:\n$text")
        }
    }

    @Test
    fun `inspect_dependencies works with configuration cache re-use`() = runTest(timeout = 10.minutes) {
        _project.projectDir.resolve("gradle.properties").writeText("org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=fail")

        // First run: Serializing
        val result1 = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            buildJsonObject {
                put("projectRoot", _project.path().absolutePathString())
                put("checkUpdates", false)
            }
        ) as CallToolResult
        assertFalse(result1.isError == true, "First call failed: ${(result1.content.firstOrNull() as? TextContent)?.text}")

        // Second run: Re-using (Deserializing)
        val result2 = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            buildJsonObject {
                put("projectRoot", _project.path().absolutePathString())
                put("checkUpdates", false)
            }
        ) as CallToolResult

        assertFalse(result2.isError == true, "Second call (re-use) failed: ${(result2.content.firstOrNull() as? TextContent)?.text}")
        val text = (result2.content.first() as TextContent).text!!
        assertTrue(text.contains("kotlinx-coroutines-core"), "Cached report should still contain coroutines")
    }

    @Test
    fun `inspect_dependencies with updates works with configuration cache`() = runTest(timeout = 10.minutes) {
        _project.projectDir.resolve("gradle.properties").writeText("org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=fail")
        _project.projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins { kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
            repositories { mavenCentral() }
            dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0") }
        """.trimIndent()
        )

        val result = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            buildJsonObject {
                put("projectRoot", _project.path().absolutePathString())
                put("checkUpdates", true)
            }
        ) as CallToolResult

        assertFalse(result.isError == true, "Tool call with updates failed. Error: ${(result.content.firstOrNull() as? TextContent)?.text}")
        val text = (result.content.first() as TextContent).text!!
        assertTrue(text.contains("kotlinx-coroutines-core"), "Report should contain coroutines. Output: $text")
        assertTrue(text.contains("[UPDATE AVAILABLE:"), "Report should contain updates for older coroutines. Output: $text")
    }

    @Test
    fun `inspect_dependencies works with multi-project build and configuration cache`() = runTest(timeout = 10.minutes) {
        _project.projectDir.resolve("gradle.properties").writeText("org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=fail")
        _project.projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-root\"\ninclude(\":sub\")")
        _project.projectDir.resolve("sub").toFile().mkdirs()
        _project.projectDir.resolve("sub/build.gradle.kts").writeText(
            """
            plugins { kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
            repositories { mavenCentral() }
            dependencies { implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0") }
        """.trimIndent()
        )

        val result = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            buildJsonObject {
                put("projectRoot", _project.path().absolutePathString())
                put("projectPath", ":sub")
            }
        ) as CallToolResult

        assertFalse(result.isError == true, "Multi-project call failed. Error: ${(result.content.firstOrNull() as? TextContent)?.text}")
        val text = (result.content.first() as TextContent).text!!
        assertTrue(text.contains("Project: :sub"), "Report should contain subproject. Output: $text")
        assertTrue(text.contains("kotlin-stdlib"), "Report should contain subproject dependency")
    }

    @Test
    fun `kotlin_repl start works with configuration cache`() = runTest(timeout = 5.minutes) {
        _project.projectDir.resolve("gradle.properties").writeText("org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=fail")

        val result = server.client.callTool(
            ToolNames.REPL,
            buildJsonObject {
                put("command", "start")
                put("projectRoot", _project.path().absolutePathString())
                put("projectPath", ":")
                put("sourceSet", "main")
            }
        ) as CallToolResult

        assertFalse(result.isError == true, "REPL start should not fail. Error: ${(result.content.firstOrNull() as? TextContent)?.text}")
        val text = (result.content.first() as TextContent).text!!
        assertTrue(text.contains("REPL session started"), "REPL should start")
    }
}
