package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.dependencies.DefaultGradleDependencyService
import dev.rnett.gradle.mcp.dependencies.DefaultSourceStorageService
import dev.rnett.gradle.mcp.dependencies.DefaultSourcesService
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.SourceStorageService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.fixtures.dependencies.NoJdkSourceService
import dev.rnett.gradle.mcp.fixtures.gradle.GradleProjectBuilder
import dev.rnett.gradle.mcp.fixtures.gradle.GradleProjectFixture
import dev.rnett.gradle.mcp.fixtures.gradle.testKotlinProject
import dev.rnett.gradle.mcp.fixtures.gradle.withTestGradleDefaults
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
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module
import java.util.ArrayDeque
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ConfigurationCacheIntegrationTest : BaseMcpServerTest() {

    private lateinit var _project: GradleProjectFixture
    private val extraProjects = ArrayDeque<GradleProjectFixture>()

    override fun Scope.createProvider(): GradleProvider {
        return DefaultGradleProvider(
            config = get(),
            buildManager = get(),
            initScriptProvider = get<InitScriptProvider>() as DefaultInitScriptProvider
        ).withTestGradleDefaults(
            additionalSystemProps = mapOf("org.gradle.configuration-cache.problems" to "fail")
        )
    }

    override fun createTestModule(): Module = module {
        single<GradleDependencyService> { DefaultGradleDependencyService(get()) }
        single<SourceStorageService> { DefaultSourceStorageService(get()) }
        single<IndexService> { mockk(relaxed = true) }
        single<SourcesService> { DefaultSourcesService(get(), get(), get(), NoJdkSourceService) }
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
            buildScript(defaultBuildScript())
        }
        resetProjectDefaults()
        super.setup()
        server.setServerRoots(Root(_project.path().toUri().toString(), "root"))
    }

    @AfterEach
    override fun cleanup() = runTest {
        while (extraProjects.isNotEmpty()) {
            extraProjects.removeLast().close()
        }
        _project.close()
        super.cleanup()
    }

    private fun resetProjectDefaults() {
        _project.projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-project\"")
        _project.projectDir.resolve("build.gradle.kts").writeText(defaultBuildScript())
        _project.projectDir.resolve("gradle.properties").deleteIfExists()
    }

    private fun createIsolatedProject(builder: GradleProjectBuilder.() -> Unit = {}): GradleProjectFixture {
        return testKotlinProject {
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
            builder()
        }.also(extraProjects::addLast)
    }

    @Test
    fun `inspect_dependencies works with configuration cache`() = runTest(timeout = 5.minutes) {
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
        assertLatestBuildReusedConfigurationCache()
    }

    @Test
    fun `inspect_dependencies with updates works with configuration cache`() = runTest(timeout = 10.minutes) {
        val project = createIsolatedProject {
            buildScript(
                """
                plugins { kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
                repositories { mavenCentral() }
                dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0") }
                """.trimIndent()
            )
        }
        server.setServerRoots(Root(project.path().toUri().toString(), "root"))

        val result = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            buildJsonObject {
                put("projectRoot", project.path().absolutePathString())
                put("checkUpdates", true)
            }
        ) as CallToolResult

        assertFalse(result.isError == true, "Tool call with updates failed. Error: ${(result.content.firstOrNull() as? TextContent)?.text}")
        val text = (result.content.first() as TextContent).text!!
        assertTrue(text.contains("kotlinx-coroutines-core"), "Report should contain coroutines. Output: $text")
        assertTrue(text.contains("[UPDATE AVAILABLE:"), "Report should contain updates for older coroutines. Output: $text")
    }

    @Test
    fun `read_dependency_sources with source set and dependency works with configuration cache reuse`() = runTest(timeout = 10.minutes) {
        _project.projectDir.resolve("gradle.properties").writeText("org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=fail")
        _project.projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins { kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
            repositories { mavenCentral() }
            dependencies {
                implementation("org.slf4j:slf4j-api:2.0.13")
                testImplementation("commons-io:commons-io:2.15.1")
            }
        """.trimIndent()
        )

        val result1 = callReadDependencySources(
            dependency = "^org\\.slf4j:slf4j-api(:.*)?$",
            sourceSetPath = ":main"
        )
        server.close()
        server = createFixture()
        server.start()
        server.setServerRoots(Root(_project.path().toUri().toString(), "root"))
        val result2 = callReadDependencySources(
            dependency = "^org\\.slf4j:slf4j-api(:.*)?$",
            sourceSetPath = ":main"
        )

        assertFalse(result1.isError == true, "First filtered source-set call failed. Error: ${resultText(result1)}")
        assertFalse(result2.isError == true, "Second filtered source-set call failed. Error: ${resultText(result2)}")
        val text1 = resultText(result1)
        val text2 = resultText(result2)
        assertContains(text1, "slf4j/")
        assertContains(text2, "slf4j/")
        assertFalse(text1.contains("commons-io/"), "First filtered source-set result should not include excluded test dependency sources. Output: $text1")
        assertFalse(text2.contains("commons-io/"), "Second filtered source-set result should not include excluded test dependency sources. Output: $text2")
        assertLatestBuildReusedConfigurationCache()
    }


    @Test
    fun `inspect_dependencies works with multi-project build and configuration cache`() = runTest(timeout = 10.minutes) {
        val project = createIsolatedProject {
            settings("rootProject.name = \"test-root\"\ninclude(\":sub\")")
            subproject(
                "sub",
                buildScript = """
                plugins { kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}" }
                repositories { mavenCentral() }
                dependencies { implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0") }
                """.trimIndent()
            )
        }
        server.setServerRoots(Root(project.path().toUri().toString(), "root"))

        val result = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            buildJsonObject {
                put("projectRoot", project.path().absolutePathString())
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

    private suspend fun callReadDependencySources(dependency: String, sourceSetPath: String? = null): CallToolResult {
        return server.client.callTool(
            ToolNames.READ_DEPENDENCY_SOURCES,
            buildJsonObject {
                put("projectRoot", _project.path().absolutePathString())
                if (sourceSetPath == null) {
                    put("projectPath", ":")
                } else {
                    put("sourceSetPath", sourceSetPath)
                }
                put("dependency", dependency)
            }
        ) as CallToolResult
    }

    private fun resultText(result: CallToolResult): String {
        return result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text.orEmpty() }
    }

    private fun defaultBuildScript(): String {
        return """
            plugins {
                kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}"
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.slf4j:slf4j-api:2.0.13")
            }
        """.trimIndent()
    }

    private fun assertLatestBuildReusedConfigurationCache() {
        val console = buildManager.latestFinished(1).single().consoleOutput.toString()
        assertTrue(
            console.contains("Reusing configuration cache.") || console.contains("Configuration cache entry reused."),
            "Second Gradle invocation should reuse the configuration cache. Console output:\n$console"
        )
    }
}
