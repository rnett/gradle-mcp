package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleSourceSetDependencies
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.tools.ToolNames
import io.mockk.coEvery
import io.mockk.coVerify
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleDependencyToolsTest : BaseMcpServerTest() {

    private lateinit var dependencyService: GradleDependencyService
    private lateinit var tools: GradleDependencyTools

    @BeforeEach
    fun setupTest() = runTest {
        dependencyService = server.koin.get()
        tools = GradleDependencyTools(dependencyService)
        server.setServerRoots(Root(tempDir.toUri().toString(), "root"))
    }

    @Test
    fun `inspect_dependencies updatesOnly produces flat summary without configuration columns`() = runTest {
        val report = GradleDependencyReport(
            projects = listOf(
                GradleProjectDependencies(
                    path = ":",
                    sourceSets = listOf(
                        GradleSourceSetDependencies("main", listOf("implementation", "compileClasspath")),
                        GradleSourceSetDependencies("test", listOf("testImplementation", "testCompileClasspath"))
                    ),
                    repositories = emptyList(),
                    configurations = listOf(
                        GradleConfigurationDependencies(
                            name = "compileClasspath",
                            description = "Compile classpath",
                            isResolvable = true,
                            dependencies = listOf(
                                GradleDependency(
                                    id = "org.slf4j:slf4j-api:1.7.30",
                                    group = "org.slf4j",
                                    name = "slf4j-api",
                                    version = "1.7.30",
                                    latestVersion = "2.0.0",
                                    isDirect = true
                                )
                            )
                        ),
                        GradleConfigurationDependencies(
                            name = "testCompileClasspath",
                            description = "Test compile classpath",
                            isResolvable = true,
                            dependencies = listOf(
                                GradleDependency(
                                    id = "junit:junit:4.12",
                                    group = "junit",
                                    name = "junit",
                                    version = "4.12",
                                    latestVersion = "4.13.2",
                                    isDirect = true
                                )
                            )
                        )
                    )
                )
            )
        )

        coEvery {
            with(any<ProgressReporter>()) {
                dependencyService.getDependencies(
                    projectRoot = any(),
                    projectPath = any(),
                    configuration = any(),
                    sourceSet = any(),
                    dependency = any(),
                    checkUpdates = true,
                    versionFilter = any(),
                    onlyDirect = true
                )
            }
        } returns report

        val response = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("projectPath", ":")
                put("onlyDirect", true)
                put("updatesOnly", true)
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text!!

        // Verify header
        assertTrue(result.contains("Available Dependency Updates:"), "Should have header. Output:\n$result")
        // Verify group:artifact format with Unicode arrow (no version in key)
        assertTrue(result.contains("- org.slf4j:slf4j-api: 1.7.30 → 2.0.0"), "Should contain slf4j entry. Output:\n$result")
        assertTrue(result.contains("- junit:junit: 4.12 → 4.13.2"), "Should contain junit entry. Output:\n$result")
        // Verify project path listed under each dep
        assertEquals(2, result.lines().count { it.trim() == "- :" }, "Each dep should list project path. Output:\n$result")
        // Verify old format is absent
        assertFalse(result.contains("Configurations"), "Should not contain configuration columns. Output:\n$result")
        assertFalse(result.contains("Source Sets"), "Should not contain source set columns. Output:\n$result")
        assertFalse(result.contains("->"), "Should use Unicode → not ASCII ->. Output:\n$result")
    }

    @Test
    fun `inspect_dependencies updatesOnly=true forces checkUpdates=true even when checkUpdates=false`() = runTest {
        val report = GradleDependencyReport(projects = emptyList())

        coEvery {
            with(any<ProgressReporter>()) {
                dependencyService.getDependencies(
                    projectRoot = any(),
                    projectPath = any(),
                    configuration = any(),
                    sourceSet = any(),
                    dependency = any(),
                    checkUpdates = any(),
                    versionFilter = any(),
                    stableOnly = any(),
                    onlyDirect = any()
                )
            }
        } returns report

        server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("updatesOnly", true)
                put("checkUpdates", false) // explicitly false — must be overridden by updatesOnly
            }
        ) as CallToolResult

        // Verify the service was called with checkUpdates=true despite checkUpdates=false in args.
        coVerify {
            with(any<ProgressReporter>()) {
                dependencyService.getDependencies(
                    projectRoot = any(),
                    projectPath = any(),
                    configuration = any(),
                    sourceSet = any(),
                    dependency = any(),
                    checkUpdates = true,
                    versionFilter = any(),
                    stableOnly = any(),
                    onlyDirect = any()
                )
            }
        }
    }

    @Test
    fun `inspect_dependencies marks repeated dependencies`() = runTest {
        val report = GradleDependencyReport(
            projects = listOf(
                GradleProjectDependencies(
                    path = ":",
                    sourceSets = emptyList(),
                    repositories = emptyList(),
                    configurations = listOf(
                        GradleConfigurationDependencies(
                            name = "compileClasspath",
                            description = null,
                            isResolvable = true,
                            dependencies = listOf(
                                GradleDependency(
                                    id = "A", group = "g", name = "A", version = "1",
                                    variant = "v",
                                    children = listOf(
                                        GradleDependency(id = "B", group = "g", name = "B", version = "1", variant = "v")
                                    )
                                ),
                                GradleDependency(
                                    id = "B", group = "g", name = "B", version = "1", variant = "v"
                                )
                            )
                        )
                    )
                )
            )
        )

        coEvery {
            with(any<ProgressReporter>()) {
                dependencyService.getDependencies(any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        } returns report

        val response = server.client.callTool(ToolNames.INSPECT_DEPENDENCIES, emptyMap()) as CallToolResult
        val result = (response.content.first() as TextContent).text!!

        assertTrue(result.contains("Dependency Report"), "Should contain report header")
        assertTrue(result.contains("Note: (*) indicates a dependency that has already been listed"), "Should contain (*) explanation")
        assertTrue(result.contains("B (*)"), "Repeated dependency should be marked with (*)")
    }

    @Test
    fun `output is correctly sorted and filtered`() = runTest {
        val report = GradleDependencyReport(
            projects = listOf(
                GradleProjectDependencies(
                    path = ":",
                    sourceSets = emptyList(),
                    repositories = emptyList(),
                    configurations = listOf(
                        GradleConfigurationDependencies(
                            name = "parentConf",
                            description = null,
                            isResolvable = false,
                            dependencies = listOf(
                                GradleDependency(id = "org.slf4j:slf4j-api:1.7.30", group = "org.slf4j", name = "slf4j-api", version = "1.7.30")
                            )
                        ),
                        GradleConfigurationDependencies(
                            name = "childConf",
                            description = null,
                            isResolvable = false,
                            extendsFrom = listOf("parentConf"),
                            dependencies = listOf(
                                GradleDependency(id = "com.google.guava:guava:30.1-jre", group = "com.google.guava", name = "guava", version = "30.1-jre")
                            )
                        )
                    )
                )
            )
        )
        val output = tools.formatDependencyReport(report, PaginationInput.DEFAULT_ITEMS)

        // Verify sorting: parentConf (depth 0) should be before childConf (depth 1)
        val parentIdx = output.indexOf("Configuration: parentConf")
        val childIdx = output.indexOf("Configuration: childConf")
        assertTrue(parentIdx != -1, "Should contain parentConf")
        assertTrue(childIdx != -1, "Should contain childConf")
        assertTrue(parentIdx < childIdx, "parentConf should be before childConf. Output:\n$output")

        // Verify extends from
        assertTrue(output.contains("Extends from: parentConf"), "Should show inheritance in childConf section")

        // Verify filtering: slf4j should be in parentConf but NOT in childConf (because it's inherited from unresolvable to unresolvable)
        val parentPart = output.substring(parentIdx, childIdx)
        val childPart = output.substring(childIdx)

        assertTrue(parentPart.contains("slf4j-api:1.7.30"), "parentConf should contain slf4j-api")
        assertFalse(childPart.contains("slf4j-api:1.7.30"), "childConf should NOT contain slf4j-api (inherited). Child part:\n$childPart")
        assertTrue(childPart.contains("guava:30.1-jre"), "childConf should contain guava")
    }

    @Test
    fun `resolvable configuration shows inherited dependencies with graphs`() = runTest {
        val report = GradleDependencyReport(
            projects = listOf(
                GradleProjectDependencies(
                    path = ":",
                    sourceSets = emptyList(),
                    repositories = emptyList(),
                    configurations = listOf(
                        GradleConfigurationDependencies(
                            name = "implementation",
                            description = null,
                            isResolvable = false,
                            dependencies = listOf(
                                GradleDependency(id = "org.slf4j:slf4j-api:1.7.30", group = "org.slf4j", name = "slf4j-api", version = "1.7.30")
                            )
                        ),
                        GradleConfigurationDependencies(
                            name = "compileClasspath",
                            description = null,
                            isResolvable = true,
                            extendsFrom = listOf("implementation"),
                            dependencies = listOf(
                                GradleDependency(id = "org.slf4j:slf4j-api:1.7.30", group = "org.slf4j", name = "slf4j-api", version = "1.7.30")
                            )
                        )
                    )
                )
            )
        )
        val output = tools.formatDependencyReport(report, PaginationInput.DEFAULT_ITEMS)

        val implIdx = output.indexOf("Configuration: implementation")
        val compileClasspathIdx = output.indexOf("Configuration: compileClasspath")

        assertTrue(implIdx != -1 && compileClasspathIdx != -1)

        val implPart = output.substring(implIdx, compileClasspathIdx)
        val compilePart = output.substring(compileClasspathIdx)

        assertTrue(implPart.contains("slf4j-api:1.7.30"), "implementation should show slf4j")
        assertTrue(compilePart.contains("slf4j-api:1.7.30"), "compileClasspath should ALSO show slf4j because it's the first resolvable one. Output:\n$output")
    }

    @Test
    fun `shows note when version differs from parent`() = runTest {
        val report = GradleDependencyReport(
            projects = listOf(
                GradleProjectDependencies(
                    path = ":",
                    sourceSets = emptyList(),
                    repositories = emptyList(),
                    configurations = listOf(
                        GradleConfigurationDependencies(
                            name = "implementation",
                            description = null,
                            isResolvable = false,
                            dependencies = listOf(
                                GradleDependency(id = "org.slf4j:slf4j-api:1.7.30", group = "org.slf4j", name = "slf4j-api", version = "1.7.30")
                            )
                        ),
                        GradleConfigurationDependencies(
                            name = "compileClasspath",
                            description = null,
                            isResolvable = true,
                            extendsFrom = listOf("implementation"),
                            dependencies = listOf(
                                GradleDependency(
                                    id = "org.slf4j:slf4j-api:1.7.31",
                                    group = "org.slf4j",
                                    name = "slf4j-api",
                                    version = "1.7.31",
                                    fromConfiguration = "implementation"
                                )
                            )
                        )
                    )
                )
            )
        )
        val output = tools.formatDependencyReport(report, PaginationInput.DEFAULT_ITEMS)

        assertTrue(output.contains("slf4j-api:1.7.31 (was 1.7.30 in implementation)"), "Should show version difference note. Output:\n$output")
    }

    @Test
    fun `inspect_dependencies shows update message when checkUpdates is true`() = runTest {
        val report = GradleDependencyReport(
            projects = listOf(
                GradleProjectDependencies(
                    path = ":",
                    sourceSets = emptyList(),
                    repositories = emptyList(),
                    configurations = listOf(
                        GradleConfigurationDependencies(
                            name = "implementation",
                            description = null,
                            isResolvable = true,
                            dependencies = listOf(
                                GradleDependency(
                                    id = "org.slf4j:slf4j-api:1.7.30",
                                    group = "org.slf4j",
                                    name = "slf4j-api",
                                    version = "1.7.30",
                                    latestVersion = "2.0.0"
                                )
                            )
                        )
                    )
                )
            )
        )

        coEvery {
            with(any<ProgressReporter>()) {
                dependencyService.getDependencies(any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        } returns report

        val response = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("checkUpdates", true)
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text!!
        assertTrue(result.contains("[UPDATE AVAILABLE: 2.0.0]"), "Output should contain update message. Result:\n$result")
    }

    @Test
    fun `inspect_dependencies with dependency filter calls correct service method`() = runTest {
        val report = GradleDependencyReport(emptyList())

        coEvery {
            with(any<ProgressReporter>()) {
                dependencyService.getDependencies(
                    projectRoot = any(),
                    projectPath = any(),
                    configuration = any(),
                    sourceSet = any(),
                    dependency = any(),
                    checkUpdates = any(),
                    versionFilter = any(),
                    stableOnly = any(),
                    onlyDirect = any(),
                    downloadSources = any()
                )
            }
        } returns report

        server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("dependency", "org.example:artifact")
            }
        ) as CallToolResult

        coVerify {
            with(any<ProgressReporter>()) {
                dependencyService.getDependencies(
                    projectRoot = any(),
                    projectPath = any(),
                    configuration = any(),
                    sourceSet = any(),
                    dependency = "org.example:artifact",
                    checkUpdates = any(),
                    versionFilter = any(),
                    stableOnly = any(),
                    onlyDirect = any(),
                    downloadSources = any()
                )
            }
        }
    }

    @Test
    fun `inspect_dependencies supports pagination`() = runTest {
        val report = GradleDependencyReport(
            projects = (1..5).map { i ->
                GradleProjectDependencies(
                    path = ":p$i",
                    sourceSets = emptyList(),
                    repositories = emptyList(),
                    configurations = emptyList()
                )
            }
        )

        coEvery {
            with(any<ProgressReporter>()) {
                dependencyService.getDependencies(any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        } returns report

        val response = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("pagination", buildJsonObject {
                    put("offset", 1)
                    put("limit", 2)
                })
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text!!
        assertTrue(result.contains("Project: :p2"), "Should contain second project")
        assertTrue(result.contains("Project: :p3"), "Should contain third project")
        assertFalse(result.contains("Project: :p1"), "Should NOT contain first project")
        assertTrue(result.contains("Showing projects 2 to 3 of 5"), "Should contain pagination metadata")
    }
}
