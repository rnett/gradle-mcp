package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.dependencies.DefaultGradleDependencyService
import dev.rnett.gradle.mcp.dependencies.DependencyRequestOptions
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.fixtures.gradle.GradleProjectFixture
import dev.rnett.gradle.mcp.fixtures.gradle.testGradleProject
import dev.rnett.gradle.mcp.fixtures.gradle.withTestGradleDefaults
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.fixtures.mcp.McpServerFixture
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.tools.GradleBuildLookupTools
import dev.rnett.gradle.mcp.tools.GradleExecutionTools
import dev.rnett.gradle.mcp.tools.ToolNames
import dev.rnett.gradle.mcp.tools.dependencies.GradleDependencyTools
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module
import kotlin.io.path.absolutePathString
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * End-to-end coverage for the opt-in `includeConsumers` reverse-edge behavior of
 * `inspect_dependencies` (change `dependency-provenance-consumers`, task 3.3).
 *
 * Exercises a real Gradle resolution graph: two direct guava requests at different versions
 * (33.6.0-jre vs 32.1.3-jre) so Gradle conflict-resolves to the higher version, and guava's
 * transitive `failureaccess` dependency is the target for reverse consumer edges.
 */
class GradleDependencyConsumersIntegrationTest : BaseMcpServerTest() {

    private lateinit var project: GradleProjectFixture

    // The class-scoped real provider + build manager are owned outside the per-method fixture
    // (see [GradleDependencySharedComponents]); the fixture close chain must not close them, so
    // the components whose close() would close the shared provider/build manager are excluded.
    override fun Scope.createProvider(): GradleProvider = sharedComponents.value.provider

    override fun createTestModule(): Module = module {
        single { sharedComponents.value.buildManager }
        single<GradleDependencyService> { sharedComponents.value.dependencyService }
        single { GradleDependencyTools(get()) }
    }

    override fun createTestModules(): List<Module> = listOf(super.createTestModule(), createTestModule())

    override fun createFixture(): McpServerFixture = McpServerFixture(
        koinModules = listOf(DI.createModule(createTestConfig())) + createTestModules(),
        excludeFromClose = setOf(GradleExecutionTools::class, GradleBuildLookupTools::class)
    )

    @BeforeEach
    override fun setup() = runTest(timeout = 5.minutes) {
        project = testGradleProject {
            buildScript(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies {
                    implementation("com.google.guava:guava:${TestFixturesBuildConfig.GUAVA_VERSION}")
                    implementation("com.google.guava:guava:32.1.3-jre")
                }
                """.trimIndent()
            )
        }
        super.setup()
    }

    @AfterEach
    override fun cleanup() = runTest(timeout = 5.minutes) {
        project.close()
        super.cleanup()
    }

    @Test
    fun `includeConsumers true emits reverse consumer edges from a real resolved graph`() = runTest(timeout = 10.minutes) {
        val result = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            buildJsonObject {
                put("projectRoot", project.path().absolutePathString())
                put("checkUpdates", false)
                put("includeConsumers", true)
            }
        ) as CallToolResult

        assertFalse(
            result.isError == true,
            "inspect_dependencies with includeConsumers=true should succeed. Error: ${(result.content.firstOrNull() as? TextContent)?.text}"
        )
        val text = (result.content.first() as TextContent).text!!

        // includeConsumers=true implies full-graph processing: the transitive failureaccess
        // dependency (child of guava) is present even though onlyDirect defaults to true.
        assertTrue(text.contains("failureaccess"), "Full graph should include transitive failureaccess. Output:\n$text")
        // Reverse edges are rendered for failureaccess, whose direct parent is guava.
        assertTrue(text.contains("Consumers:"), "Consumers block should be rendered. Output:\n$text")
        assertTrue(
            text.contains("com.google.guava:guava:${TestFixturesBuildConfig.GUAVA_VERSION}"),
            "Guava should render with the conflict-resolved selected version. Output:\n$text"
        )
    }

    @Test
    fun `includeConsumers default disabled omits consumers regardless of onlyDirect`() = runTest(timeout = 10.minutes) {
        // Default call (onlyDirect=true): direct deps only, no reverse edges.
        val defaultResult = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            buildJsonObject {
                put("projectRoot", project.path().absolutePathString())
                put("checkUpdates", false)
            }
        ) as CallToolResult
        val defaultText = (defaultResult.content.first() as TextContent).text!!
        assertTrue(
            defaultText.contains("com.google.guava:guava:${TestFixturesBuildConfig.GUAVA_VERSION}"),
            "Direct guava should still render by default. Output:\n$defaultText"
        )
        assertFalse(defaultText.contains("Consumers:"), "Consumers must be absent by default. Output:\n$defaultText")

        // Explicit onlyDirect=false without includeConsumers: full graph, still no reverse edges.
        val fullResult = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            buildJsonObject {
                put("projectRoot", project.path().absolutePathString())
                put("checkUpdates", false)
                put("onlyDirect", false)
            }
        ) as CallToolResult
        val fullText = (fullResult.content.first() as TextContent).text!!
        assertTrue(fullText.contains("failureaccess"), "onlyDirect=false should show transitive deps. Output:\n$fullText")
        assertFalse(
            fullText.contains("Consumers:"),
            "Consumers must be absent without includeConsumers even when onlyDirect=false. Output:\n$fullText"
        )
    }

    @Test
    fun `includeConsumers true overrides explicit onlyDirect true and emits the override note`() = runTest(timeout = 10.minutes) {
        val result = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            buildJsonObject {
                put("projectRoot", project.path().absolutePathString())
                put("checkUpdates", false)
                put("onlyDirect", true)
                put("includeConsumers", true)
            }
        ) as CallToolResult

        val text = (result.content.first() as TextContent).text!!
        assertTrue(
            text.contains("Note: onlyDirect overridden to false for consumers inversion"),
            "Response should carry the override note. Output:\n$text"
        )
        assertTrue(text.contains("Consumers:"), "Consumers should still be rendered. Output:\n$text")
    }

    @Test
    fun `conflict resolution provenance uses reason not latestVersion to explain selection`() = runTest(timeout = 10.minutes) {
        val service = server.koin.get<GradleDependencyService>()
        val report = with(ProgressReporter.PRINTLN) {
            service.getDependencies(
                projectRoot = GradleProjectRoot(project.pathString()),
                options = DependencyRequestOptions(includeConsumers = true)
            )
        }

        val guava = allDependencies(report).firstOrNull { it.name == "guava" && it.group == "com.google.guava" }
        assertNotNull(guava, "Guava should be present in the resolved graph")
        assertEquals(TestFixturesBuildConfig.GUAVA_VERSION, guava.version, "Selected version should win the conflict")
        // Gradle's conflict cause description is "between versions <winner> and <loser>"; the reason
        // must explain the selection by naming the conflicting versions.
        assertNotNull(guava.reason, "reason must be present to explain the selection")
        assertTrue(
            guava.reason!!.contains(TestFixturesBuildConfig.GUAVA_VERSION) && guava.reason!!.contains("32.1.3-jre"),
            "reason should describe the conflict between the requested versions, got: ${guava.reason}"
        )
        // latestVersion is advisory update-check data; with checkUpdates disabled it is absent, so it
        // cannot be what explains the selection -- reason does.
        assertNull(guava.latestVersion, "latestVersion must not be used to explain the selection (checkUpdates=false)")
        assertTrue(guava.isDirect, "Both conflicting guava requests are direct")

        // failureaccess is transitive under guava, so inverting the full graph gives guava as its
        // direct consumer edge.
        val failureaccess = allDependencies(report).firstOrNull { it.name == "failureaccess" && it.group == "com.google.guava" }
        assertNotNull(failureaccess, "failureaccess should be in the full resolved graph")
        assertNotNull(failureaccess.consumers, "consumers must be computed when includeConsumers=true")
        val consumer = failureaccess.consumers!!.firstOrNull { it.name == "guava" && it.group == "com.google.guava" }
        assertNotNull(consumer, "Guava should be a direct consumer of failureaccess. Consumers: ${failureaccess.consumers}")
        assertEquals(
            consumer.id, consumer.path,
            "ConsumerEdge.path should identify the direct parent edge"
        )
    }

    private fun allDependencies(report: GradleDependencyReport): List<GradleDependency> =
        report.projects.flatMap { it.configurations }.flatMap { it.dependencies }.flatMap { flattenDependency(it) }

    private fun flattenDependency(dep: GradleDependency): List<GradleDependency> =
        listOf(dep) + dep.children.flatMap { flattenDependency(it) }

    companion object {
        // JUnit creates a fresh test instance per method; the companion object is shared across all
        // methods, so the class-scoped components live here and are closed exactly once in @AfterAll.
        private val sharedComponents: Lazy<GradleDependencySharedComponents> = lazy { GradleDependencySharedComponents() }

        @JvmStatic
        @AfterAll
        fun closeSharedComponents() {
            if (sharedComponents.isInitialized()) {
                sharedComponents.value.close()
            }
        }
    }
}

private class GradleDependencySharedComponents {
    val buildManager = BuildManager()
    val provider: GradleProvider = DefaultGradleProvider(
        buildManager = buildManager
    ).withTestGradleDefaults()
    val dependencyService: GradleDependencyService = DefaultGradleDependencyService(provider)

    fun close() {
        provider.close()
        buildManager.close()
    }
}
