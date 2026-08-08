package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.DependencyRequestOptions
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.model.ConsumerEdge
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
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleDependencyToolsTest : BaseMcpServerTest() {

    private lateinit var dependencyService: GradleDependencyService
    private lateinit var tools: GradleDependencyTools

    @BeforeEach
    fun setupTest() = runTest {
        dependencyService = server.koin.get()
        tools = GradleDependencyTools(dependencyService)
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
                    options = match { it.checkUpdates && it.onlyDirect }
                )
            }
        } returns report

        val response = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("projectRoot", tempDir.toString())
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
        assertTrue(result.contains("- : (main)"), "Each dep should list project path and source set. Output:\n$result")
        assertTrue(result.contains("- : (test)"), "Each dep should list project path and source set. Output:\n$result")
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
                    options = any()
                )
            }
        } returns report

        server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("projectRoot", tempDir.toString())
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
                    options = match { it.checkUpdates }
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
                dependencyService.getDependencies(any(), any(), any())
            }
        } returns report

        val response = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES,
            mapOf("projectRoot" to tempDir.toString())
        ) as CallToolResult
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
                dependencyService.getDependencies(any(), any(), any())
            }
        } returns report

        val response = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("projectRoot", tempDir.toString())
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
                    options = any()
                )
            }
        } returns report

        server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("projectRoot", tempDir.toString())
                put("dependency", "org.example:artifact")
            }
        ) as CallToolResult

        coVerify {
            with(any<ProgressReporter>()) {
                dependencyService.getDependencies(
                    projectRoot = any(),
                    projectPath = any(),
                    options = match { it.dependency == "org.example:artifact" }
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
                dependencyService.getDependencies(any(), any(), any())
            }
        } returns report

        val response = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("projectRoot", tempDir.toString())
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

    @Test
    fun `inspect_dependencies includeConsumers=true forces onlyDirect=false and emits override note`() = runTest {
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
                                    id = "org.example:lib:1.0",
                                    group = "org.example",
                                    name = "lib",
                                    version = "1.0"
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
                    options = match { !it.onlyDirect && it.includeConsumers }
                )
            }
        } returns report

        val response = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("projectRoot", tempDir.toString())
                put("onlyDirect", true)
                put("includeConsumers", true)
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text!!
        assertTrue(
            result.contains("Note: onlyDirect overridden to false for consumers inversion"),
            "Response should carry the override note. Output:\n$result"
        )
        coVerify {
            with(any<ProgressReporter>()) {
                dependencyService.getDependencies(
                    projectRoot = any(),
                    projectPath = any(),
                    options = match { !it.onlyDirect && it.includeConsumers }
                )
            }
        }
    }

    @Test
    fun `inspect_dependencies override note survives an empty report`() = runTest {
        // Empty report (no projects): the override note is part of the response contract and must
        // not be dropped by the "No projects found." early return.
        val emptyReport = GradleDependencyReport(projects = emptyList())

        coEvery {
            with(any<ProgressReporter>()) {
                dependencyService.getDependencies(
                    projectRoot = any(),
                    projectPath = any(),
                    options = match { !it.onlyDirect && it.includeConsumers }
                )
            }
        } returns emptyReport

        val response = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("projectRoot", tempDir.toString())
                put("onlyDirect", true)
                put("includeConsumers", true)
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text!!
        assertTrue(result.contains("No projects found."), "Empty report should still render. Output:\n$result")
        assertTrue(
            result.contains("Note: onlyDirect overridden to false for consumers inversion"),
            "Override note must survive an empty report. Output:\n$result"
        )
    }

    @Test
    fun `inspect_dependencies includeConsumers=true with onlyDirect=false has no override note`() = runTest {
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
                                    id = "org.example:lib:1.0",
                                    group = "org.example",
                                    name = "lib",
                                    version = "1.0"
                                )
                            )
                        )
                    )
                )
            )
        )

        coEvery {
            with(any<ProgressReporter>()) {
                dependencyService.getDependencies(any(), any(), any())
            }
        } returns report

        val response = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("projectRoot", tempDir.toString())
                put("onlyDirect", false)
                put("includeConsumers", true)
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text!!
        assertFalse(result.contains("onlyDirect overridden"), "No override note expected. Output:\n$result")
    }

    @Test
    fun `inspect_dependencies renders consumers when present`() = runTest {
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
                                    id = "org.example:lib-c:1.0",
                                    group = "org.example",
                                    name = "lib-c",
                                    version = "1.0",
                                    consumers = listOf(
                                        ConsumerEdge(
                                            id = "org.example:lib-b:1.0",
                                            group = "org.example",
                                            name = "lib-b",
                                            version = "1.0",
                                            variant = "jvm",
                                            fromConfiguration = "implementation",
                                            path = "org.example:lib-b:1.0"
                                        )
                                    )
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
                    options = match { it.includeConsumers }
                )
            }
        } returns report

        val response = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("projectRoot", tempDir.toString())
                put("includeConsumers", true)
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text!!
        assertTrue(result.contains("Consumers:"), "Should render the Consumers block. Output:\n$result")
        assertTrue(
            result.contains("- org.example:lib-b:1.0 (variant: jvm) (from: implementation)"),
            "Should render the consumer edge with variant and configuration. Output:\n$result"
        )
    }

    @Test
    fun `inspect_dependencies omits consumers by default`() = runTest {
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
                                    id = "org.example:lib-c:1.0",
                                    group = "org.example",
                                    name = "lib-c",
                                    version = "1.0"
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
                    options = match { it.onlyDirect && !it.includeConsumers }
                )
            }
        } returns report

        val response = server.client.callTool(
            ToolNames.INSPECT_DEPENDENCIES, buildJsonObject {
                put("projectRoot", tempDir.toString())
            }
        ) as CallToolResult

        val result = (response.content.first() as TextContent).text!!
        assertTrue(result.contains("org.example:lib-c:1.0"), "Dependency should still render. Output:\n$result")
        assertFalse(result.contains("Consumers:"), "Consumers must be absent by default. Output:\n$result")
    }
}
