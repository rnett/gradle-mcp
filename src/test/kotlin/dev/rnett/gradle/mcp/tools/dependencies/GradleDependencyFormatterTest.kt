package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.tools.PaginationInput
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure unit tests for [GradleDependencyTools] formatting logic.
 * These tests call formatting methods directly and do not require the MCP server infrastructure.
 *
 * Note: [GradleDependencyTools.formatDependencyReport] and [GradleDependencyTools.formatUpdatesSummary]
 * exercise the Kotlin renderer only. The init-script logic that populates [GradleDependency.updatesChecked]
 * is verified via integration tests that run against a real Gradle project.
 */
class GradleDependencyFormatterTest {

    private lateinit var tools: GradleDependencyTools

    @BeforeEach
    fun setup() {
        // No server infrastructure needed — these tests exercise pure formatting logic.
        // relaxed = true because the service is never called by formatting methods.
        tools = GradleDependencyTools(mockk(relaxed = true))
    }

    @Test
    fun `formatUpdatesSummary deduplicates same dep across multiple configurations`() {
        val report = GradleDependencyReport(
            projects = listOf(
                GradleProjectDependencies(
                    path = ":app",
                    sourceSets = emptyList(),
                    repositories = emptyList(),
                    configurations = listOf(
                        GradleConfigurationDependencies(
                            name = "compileClasspath",
                            description = null,
                            isResolvable = true,
                            dependencies = listOf(
                                GradleDependency(
                                    id = "com.example:lib:1.0",
                                    group = "com.example",
                                    name = "lib",
                                    version = "1.0",
                                    latestVersion = "2.0"
                                )
                            )
                        ),
                        GradleConfigurationDependencies(
                            name = "testCompileClasspath",
                            description = null,
                            isResolvable = true,
                            dependencies = listOf(
                                GradleDependency(
                                    id = "com.example:lib:1.0",
                                    group = "com.example",
                                    name = "lib",
                                    version = "1.0",
                                    latestVersion = "2.0"
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = tools.formatUpdatesSummary(report, PaginationInput.DEFAULT_ITEMS)

        // Same dep in multiple configs should appear only once (deduplication).
        // New format: "- group:artifact: current → latest" (no version in key).
        assertEquals(
            1, result.lines().count { it.startsWith("- com.example:lib: ") },
            "Dep should appear exactly once (deduplicated). Output:\n$result"
        )
        // Version must NOT appear a second time before the colon separator (old format was group:artifact:version:).
        assertFalse(
            result.contains("com.example:lib:1.0:"),
            "Version should not appear in the dep key. Output:\n$result"
        )
        // Project path should appear exactly once in the "Found in:" section.
        assertEquals(1, result.lines().count { it.trim() == "- :app" })
        assertFalse(result.contains("Configurations"), "Should not show configuration column")
        assertFalse(result.contains("Source Sets"), "Should not show source sets column")
    }

    @Test
    fun `formatUpdatesSummary returns no updates message when all deps are current`() {
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
                                    id = "com.example:lib:1.0",
                                    group = "com.example",
                                    name = "lib",
                                    version = "1.0",
                                    latestVersion = "1.0" // same as current — no update
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = tools.formatUpdatesSummary(report, PaginationInput.DEFAULT_ITEMS)
        assertEquals("No dependency updates found.", result)
    }

    @Test
    fun `formatUpdatesSummary returns no updates message when report has no projects`() {
        val result = tools.formatUpdatesSummary(GradleDependencyReport(emptyList()), PaginationInput.DEFAULT_ITEMS)
        assertEquals("No dependency updates found.", result)
    }

    @Test
    fun `formatUpdatesSummary output uses group-artifact format without version in key`() {
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

        val result = tools.formatUpdatesSummary(report, PaginationInput.DEFAULT_ITEMS)
        // Key should be group:artifact (no version), then ": current → latest" with Unicode arrow.
        assertTrue(
            result.contains("- org.slf4j:slf4j-api: 1.7.30 → 2.0.0"),
            "Expected group:artifact format with Unicode arrow. Output:\n$result"
        )
        // The version must NOT appear embedded in the key before the separator colon.
        assertFalse(
            result.contains("org.slf4j:slf4j-api:1.7.30:"),
            "Version should not appear in the dep key. Output:\n$result"
        )
    }

    @Test
    fun `formatDependencyReport shows UPDATE CHECK SKIPPED only for genuine failures`() {
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
                                // Genuine failure: was in scope but check failed
                                GradleDependency(
                                    id = "com.example:failed:1.0",
                                    group = "com.example",
                                    name = "failed",
                                    version = "1.0",
                                    updatesChecked = false
                                ),
                                // Intentionally excluded or check succeeded (set by init script)
                                GradleDependency(
                                    id = "com.example:ok:1.0",
                                    group = "com.example",
                                    name = "ok",
                                    version = "1.0",
                                    updatesChecked = true
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = tools.formatDependencyReport(report, PaginationInput.DEFAULT_ITEMS, checkUpdatesEnabled = true)

        assertTrue(
            result.contains("failed:1.0 [UPDATE CHECK SKIPPED]"),
            "Genuine failure should show annotation. Output:\n$result"
        )
        assertFalse(
            result.contains("ok:1.0 [UPDATE CHECK SKIPPED]"),
            "Non-failure should NOT show annotation. Output:\n$result"
        )
        assertTrue(
            result.contains("com.example:ok:1.0"),
            "Non-failure dep should still appear. Output:\n$result"
        )
    }

    @Test
    fun `formatDependencyReport does not show UPDATE CHECK SKIPPED when checks are disabled`() {
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
                                    id = "com.example:lib:1.0",
                                    group = "com.example",
                                    name = "lib",
                                    version = "1.0",
                                    updatesChecked = false // would annotate if checkUpdatesEnabled=true
                                )
                            )
                        )
                    )
                )
            )
        )

        // With checkUpdatesEnabled=false the annotation must never appear regardless of updatesChecked.
        val result = tools.formatDependencyReport(report, PaginationInput.DEFAULT_ITEMS, checkUpdatesEnabled = false)
        assertFalse(
            result.contains("[UPDATE CHECK SKIPPED]"),
            "No annotations when checks are disabled. Output:\n$result"
        )
    }

    @Test
    fun `formatDependencyReport does not annotate dep with updatesChecked=true even when checks enabled`() {
        // This models the init-script behaviour: transitive deps excluded from scope (onlyDirect=true)
        // have updatesChecked=true set by isUpdateCheckComplete, so they are never annotated.
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
                                    id = "com.example:transitive:1.0",
                                    group = "com.example",
                                    name = "transitive",
                                    version = "1.0",
                                    // updatesChecked=true means "excluded from scope or succeeded" —
                                    // set by the init script for deps intentionally out of scope.
                                    updatesChecked = true
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = tools.formatDependencyReport(report, PaginationInput.DEFAULT_ITEMS, checkUpdatesEnabled = true)
        assertFalse(
            result.contains("[UPDATE CHECK SKIPPED]"),
            "Out-of-scope dep must NOT show annotation. Output:\n$result"
        )
    }

    @Test
    fun `formatDependencyReport shows UPDATE CHECK SKIPPED for transitive dep with genuine failure when onlyDirect=false`() {
        // When onlyDirect=false, transitive deps are present in the model and subject to update checking.
        // A transitive dep whose check genuinely failed (updatesChecked=false) must show the annotation.
        val transitiveChild = GradleDependency(
            id = "com.example:transitive:1.0",
            group = "com.example",
            name = "transitive",
            version = "1.0",
            updatesChecked = false // genuine failure at the transitive level
        )
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
                                    id = "com.example:direct:1.0",
                                    group = "com.example",
                                    name = "direct",
                                    version = "1.0",
                                    updatesChecked = true,
                                    children = listOf(transitiveChild)
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = tools.formatDependencyReport(report, PaginationInput.DEFAULT_ITEMS, checkUpdatesEnabled = true)
        assertTrue(
            result.contains("transitive:1.0 [UPDATE CHECK SKIPPED]"),
            "Transitive dep with genuine failure must show annotation when onlyDirect=false. Output:\n$result"
        )
        assertFalse(
            result.contains("direct:1.0 [UPDATE CHECK SKIPPED]"),
            "Direct dep with updatesChecked=true must NOT show annotation. Output:\n$result"
        )
    }

    @Test
    fun `formatDependencyReport does not annotate dep excluded by dependency filter`() {
        // Deps excluded by a dependency filter have updatesChecked=true set by the init script
        // (they were not in scope, so isUpdateCheckComplete returns true for them).
        // This test verifies the Kotlin renderer correctly suppresses the annotation for such deps.
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
                                // Matches filter — check genuinely failed
                                GradleDependency(
                                    id = "com.example:targeted:1.0",
                                    group = "com.example",
                                    name = "targeted",
                                    version = "1.0",
                                    updatesChecked = false
                                ),
                                // Does NOT match filter — init script sets updatesChecked=true
                                GradleDependency(
                                    id = "com.example:excluded:1.0",
                                    group = "com.example",
                                    name = "excluded",
                                    version = "1.0",
                                    updatesChecked = true
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = tools.formatDependencyReport(report, PaginationInput.DEFAULT_ITEMS, checkUpdatesEnabled = true)
        assertTrue(
            result.contains("targeted:1.0 [UPDATE CHECK SKIPPED]"),
            "In-scope dep with failed check must show annotation. Output:\n$result"
        )
        assertFalse(
            result.contains("excluded:1.0 [UPDATE CHECK SKIPPED]"),
            "Filter-excluded dep must NOT show annotation. Output:\n$result"
        )
    }
}
