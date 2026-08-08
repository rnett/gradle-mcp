package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.dependencies.DefaultGradleDependencyService
import dev.rnett.gradle.mcp.dependencies.DefaultSourceStorageService
import dev.rnett.gradle.mcp.dependencies.DefaultSourcesService
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.SourceStorageService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.fixtures.SharedTestInfrastructure
import dev.rnett.gradle.mcp.fixtures.dependencies.NoJdkSourceService
import dev.rnett.gradle.mcp.fixtures.gradle.GradleProjectFixture
import dev.rnett.gradle.mcp.fixtures.gradle.testKotlinProject
import dev.rnett.gradle.mcp.fixtures.gradle.withTestGradleDefaults
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.fixtures.mcp.McpServerFixture
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.tools.GradleBuildLookupTools
import dev.rnett.gradle.mcp.tools.GradleExecutionTools
import dev.rnett.gradle.mcp.tools.ToolNames
import dev.rnett.gradle.mcp.tools.dependencies.GradleDependencyTools
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module
import java.util.ArrayDeque
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Integration tests exercising the bundled Gradle init scripts under Gradle's incubating
 * [Isolated Projects](https://docs.gradle.org/current/userguide/isolated_projects.html) feature.
 *
 * These are tests-first: they assert the INTENDED behavior (the MCP tools keep working with project
 * isolation enabled and the init scripts active). Where current behavior deviates, the test fails and
 * the failure output is the evidence of the project-isolation issue. Production code is intentionally
 * NOT modified.
 *
 * ### Isolation enablement channel
 *
 * Isolation is enabled per-provider by passing `org.gradle.isolated-projects=true` through
 * [withTestGradleDefaults] (see [ProjectIsolationSharedComponentsHolder]). The fixture derives the
 * `--isolated-projects` CLI start argument from that property exactly like it derives
 * `--configuration-cache` from `org.gradle.configuration-cache` (see the `isolatedProjectsArgs`
 * derivation in TestGradleProvider.kt). This channel is used because Isolated Projects is a
 * start-parameter / `gradle.properties` mechanism on Gradle 9.7.0: forwarding
 * `org.gradle.isolated-projects` as a Tooling API daemon JVM system property
 * (`launcher.withSystemProperties(...)`) does NOT activate it — unlike the configuration cache,
 * whose property the fixture already synthesizes into a CLI flag, so the provider channel works for
 * that one but not for isolation. Every build's console output is additionally checked for the
 * `Isolated Projects is an incubating feature.` marker (verified empirically) so the tests prove
 * isolation was genuinely active rather than silently disabled.
 *
 * ### Multi-project fixture
 *
 * The fixture is a multi-project build (root `:` plus `:sub`). The single-project, root-only tests
 * pass vacuously: the real Isolated Projects violations surface only when subprojects exist.
 * `dependencies-report.init.gradle.kts` registers `mcpDependencyReport` via
 * `allprojects { tasks.register(...) }`, which violates `Project ':' cannot access 'Project.tasks'
 * functionality on subprojects via 'allprojects'` for every project as soon as `:sub` exists (even
 * for the root `:`), and `repl-env.init.gradle.kts` registers `resolveReplEnvironment` via
 * `allprojects { if (path == targetProject) { afterEvaluate { ... } } }`, which passes for `:` but
 * violates `Project ':' cannot access 'Project.afterEvaluate' functionality on subprojects via
 * 'allprojects'` for `:sub`. The failing tests below are the tests-first evidence of the init-script
 * issues documented in `reports/isolated-projects-compatibility.md`.
 */
class ProjectIsolationIntegrationTest : BaseMcpServerTest() {

    private lateinit var _project: GradleProjectFixture
    private val extraProjects = ArrayDeque<GradleProjectFixture>()

    // The class-scoped real provider + sources service are owned outside the per-method fixture
    // (see [ProjectIsolationSharedComponentsHolder]); the fixture close chain must not close them, so the fixture
    // excludes the components whose close() would close the shared provider and build manager.
    override fun Scope.createProvider(): GradleProvider = sharedComponents.value.provider

    override fun createTestModule(): Module = module {
        single { sharedComponents.value.buildManager }
        single<GradleDependencyService> { sharedComponents.value.dependencyService }
        single<SourceStorageService> { DefaultSourceStorageService(get()) }
        single<IndexService> { mockk(relaxed = true) }
        single<SourcesService> { sharedComponents.value.sourcesService }
        single { GradleDependencyTools(get()) }
    }

    override fun createTestModules(): List<Module> = listOf(super.createTestModule(), createTestModule())

    override fun createFixture(): McpServerFixture = McpServerFixture(
        koinModules = listOf(DI.createModule(createTestConfig())) + createTestModules(),
        excludeFromClose = setOf(GradleExecutionTools::class, GradleBuildLookupTools::class)
    )

    @BeforeEach
    override fun setup() = runTest {
        _project = testKotlinProject {
            buildScript(defaultBuildScript())
        }
        resetProjectDefaults()
        super.setup()
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
        // Multi-project fixture: the real Isolated Projects init-script violations only surface when
        // a subproject exists (dependencies-report fails for every project, repl-env fails for `:sub`).
        _project.projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "test-project"
            include(":sub")
            """.trimIndent()
        )
        _project.projectDir.resolve("build.gradle.kts").writeText(defaultBuildScript())
        _project.projectDir.resolve("sub").createDirectories()
        _project.projectDir.resolve("sub").resolve("build.gradle.kts").writeText(defaultSubprojectBuildScript())
        // No gradle.properties side channel: isolation is enabled via the provider's start-parameter
        // derivation (withTestGradleDefaults + `org.gradle.isolated-projects=true`), so make sure no
        // stale property file from earlier runs lingers in the shared fixture directory.
        _project.projectDir.resolve("gradle.properties").deleteIfExists()
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
            tasks.register("printMessage") {
                doLast {
                    println("Hello from task")
                }
            }
        """.trimIndent()
    }

    private fun defaultSubprojectBuildScript(): String {
        return """
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
    }

    @Test
    fun `gradle task output capture works with isolated projects on root project`() = runTest(timeout = 10.minutes) {
        val result = server.client.callTool(
            ToolNames.GRADLE,
            buildJsonObject {
                put("projectRoot", _project.path().absolutePathString())
                put("commandLine", buildJsonArray { add("printMessage") })
            }
        ) as CallToolResult

        assertFalse(result.isError == true, "GRADLE tool call should not fail. Error: ${(result.content.firstOrNull() as? TextContent)?.text}")
        val text = (result.content.first() as TextContent).text!!
        // task-out.init.gradle.kts prefixes task output as ":<taskPath> OUT <text>".
        assertTrue(text.contains(":printMessage OUT Hello from task"), "Task output capture should work under isolated projects. Output: $text")
        assertIsolationWasActive()
    }

    @Test
    fun `inspect_dependencies works with isolated projects on root project`() = runTest(timeout = 10.minutes) {
        val result = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            buildJsonObject {
                put("projectRoot", _project.path().absolutePathString())
                put("checkUpdates", false)
            }
        ) as CallToolResult

        // Expected to FAIL under isolated projects (multi-project): dependencies-report.init.gradle.kts
        // registers mcpDependencyReport via `allprojects { tasks.register(...) }`, which fails for every
        // project (including `:`) as soon as a subproject exists.
        assertFalse(
            result.isError == true,
            "INSPECT_DEPENDENCIES on ':' should work under isolated projects. " +
                "Error: ${(result.content.firstOrNull() as? TextContent)?.text}" +
                "\nRecent build console:\n${recentConsoleOutput()}"
        )
        val text = (result.content.first() as TextContent).text!!
        assertTrue(text.contains("Project: :"), "Report should contain the root project. Output: $text")
        assertTrue(text.contains("kotlinx-coroutines-core"), "Report should contain coroutines. Output: $text")
        assertIsolationWasActive()
    }

    @Test
    fun `inspect_dependencies works with isolated projects on subproject`() = runTest(timeout = 10.minutes) {
        val result = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            buildJsonObject {
                put("projectRoot", _project.path().absolutePathString())
                put("projectPath", ":sub")
                put("checkUpdates", false)
            }
        ) as CallToolResult

        // Expected to FAIL under isolated projects: same allprojects { tasks.register(...) } violation,
        // now targeting the subproject path.
        assertFalse(
            result.isError == true,
            "INSPECT_DEPENDENCIES on ':sub' should work under isolated projects. " +
                "Error: ${(result.content.firstOrNull() as? TextContent)?.text}" +
                "\nRecent build console:\n${recentConsoleOutput()}"
        )
        val text = (result.content.first() as TextContent).text!!
        assertTrue(text.contains("Project: :sub"), "Report should contain the subproject. Output: $text")
        assertTrue(text.contains("kotlinx-coroutines-core"), "Report should contain coroutines. Output: $text")
        assertIsolationWasActive()
    }

    @Test
    fun `kotlin_repl start works with isolated projects on root project`() = runTest(timeout = 5.minutes) {
        val result = server.client.callTool(
            ToolNames.REPL,
            buildJsonObject {
                put("command", "start")
                put("projectRoot", _project.path().absolutePathString())
                put("projectPath", ":")
                put("sourceSet", "main")
            }
        ) as CallToolResult

        // Expected to PASS: repl-env.init.gradle.kts only touches the target project's tasks; for `:`
        // the afterEvaluate block runs on the root project itself, so no subproject access occurs.
        assertFalse(result.isError == true, "REPL start on ':' should not fail. Error: ${(result.content.firstOrNull() as? TextContent)?.text}")
        val text = (result.content.first() as TextContent).text!!
        assertTrue(text.contains("REPL session started"), "REPL should start")
        assertIsolationWasActive()
    }

    @Test
    fun `kotlin_repl start works with isolated projects on subproject`() = runTest(timeout = 5.minutes) {
        val result = server.client.callTool(
            ToolNames.REPL,
            buildJsonObject {
                put("command", "start")
                put("projectRoot", _project.path().absolutePathString())
                put("projectPath", ":sub")
                put("sourceSet", "main")
            }
        ) as CallToolResult

        // Expected to FAIL under isolated projects: repl-env.init.gradle.kts's
        // `allprojects { if (path == targetProject) { afterEvaluate { ... } } }` registers the task via
        // afterEvaluate on the subproject, attributed to ':' as a cross-project access violation.
        assertFalse(
            result.isError == true,
            "REPL start on ':sub' should not fail. Error: ${(result.content.firstOrNull() as? TextContent)?.text}" +
                "\nRecent build console:\n${recentConsoleOutput()}"
        )
        val text = (result.content.first() as TextContent).text!!
        assertTrue(text.contains("REPL session started"), "REPL should start")
        assertIsolationWasActive()
    }

    /**
     * Evidence that project isolation is genuinely active for the executed builds: Gradle prints the
     * marker line `Isolated Projects is an incubating feature.` on the build console when isolation is
     * enabled (verified empirically with `--isolated-projects` on Gradle 9.7.0). The marker is
     * expected in any recent finished build routed through the class-scoped provider.
     */
    private fun assertIsolationWasActive() {
        val console = recentConsoleOutput()
        assertTrue(
            console.contains("Isolated Projects is an incubating feature."),
            "Isolated Projects should be active for the executed Gradle builds (console marker " +
                "'Isolated Projects is an incubating feature.' expected). Console output of recent builds:\n$console"
        )
    }

    /**
     * Raw console output of the recent finished builds routed through the class-scoped provider.
     * Appended to assertion messages of the expected-to-fail tests so the failure report surfaces the
     * exact Gradle violation line (e.g. `Project ':' cannot access 'Project.tasks' functionality on
     * subprojects via 'allprojects'`) even when the tool-error text does not contain it.
     */
    private fun recentConsoleOutput(): String =
        sharedComponents.value.buildManager.latestFinished(100)
            .joinToString("\n") { it.consoleOutput.toString() }

    companion object {
        // JUnit creates a fresh test instance per method; the companion object is shared across all
        // methods, so the class-scoped components live here and are closed exactly once in @AfterAll.
        private val sharedComponents: Lazy<ProjectIsolationSharedComponentsHolder> = lazy { ProjectIsolationSharedComponentsHolder() }

        @JvmStatic
        @AfterAll
        fun closeSharedComponents() {
            if (sharedComponents.isInitialized()) {
                sharedComponents.value.close()
            }
        }
    }
}

/**
 * Class-scoped components owned outside the per-method [McpServerFixture]: the real
 * [GradleProvider] (with its [BuildManager]) and the real [SourcesService]. The per-method close
 * chain (`GradleExecutionTools.close()` closes the injected provider, `GradleBuildLookupTools.close()`
 * closes its build manager) would make a per-method real provider terminal after the first method
 * (`DefaultGradleProvider.close()` cancels its scope behind an AtomicBoolean with no reopen), so the
 * fixture excludes those components from close and this holder closes them exactly once in the
 * companion `@AfterAll`. Sharing one [SourcesService] keeps its session-view cache (Caffeine, 128
 * keys / 30-min TTL) alive across methods and across the in-method server recreation, cutting
 * repeated `mcpDependencyReport` builds.
 *
 * Project isolation is enabled per-provider by passing `org.gradle.isolated-projects=true` through
 * [withTestGradleDefaults]: the fixture's argument derivation synthesizes the `--isolated-projects`
 * CLI start parameter from that property (mirroring `--configuration-cache`), because Isolated
 * Projects is a start-parameter / `gradle.properties` mechanism — forwarding it as a Tooling API
 * daemon JVM system property does NOT activate it. Isolated Projects implies the configuration cache
 * (Gradle 9.7.0 start-parameter option), and `org.gradle.configuration-cache=true` is delivered by
 * the test defaults, so both are active.
 */
private class ProjectIsolationSharedComponentsHolder {
    val buildManager = BuildManager()
    val provider: GradleProvider = DefaultGradleProvider(
        buildManager = buildManager,
        initScriptProvider = DefaultInitScriptProvider(SharedTestInfrastructure.sharedWorkingDir.resolve("init-scripts"))
    ).withTestGradleDefaults(
        additionalSystemProps = mapOf("org.gradle.isolated-projects" to "true")
    )
    val dependencyService: GradleDependencyService = DefaultGradleDependencyService(provider)
    val sourcesService: SourcesService = DefaultSourcesService(
        depService = dependencyService,
        storageService = DefaultSourceStorageService(GradleMcpEnvironment(SharedTestInfrastructure.sharedMcpWorkingDir)),
        indexService = mockk(relaxed = true),
        jdkSourceService = NoJdkSourceService
    )

    fun close() {
        provider.close()
        buildManager.close()
    }
}
