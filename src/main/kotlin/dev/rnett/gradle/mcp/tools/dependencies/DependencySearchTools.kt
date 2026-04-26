package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.maven.DepsDevService
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.tools.ToolNames
import dev.rnett.gradle.mcp.tools.paginate
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

class DependencySearchTools(
    private val depsDevService: DepsDevService
) : McpServerComponent("Dependency Search Tools", "Tools for querying maven repositories for dependency information.") {

    @Serializable
    data class LookupMavenVersionsArgs(
        @Description("Maven coordinates in 'group:artifact' format, e.g. 'org.jetbrains.kotlinx:kotlinx-coroutines-core'.")
        val coordinates: String,
        @Description("offset = zero-based start index (default 0); limit = max versions to return (default 5).")
        val pagination: PaginationInput = PaginationInput(limit = 5)
    )

    val lookupMavenVersions by tool<LookupMavenVersionsArgs, String>(
        ToolNames.LOOKUP_MAVEN_VERSIONS,
        """
            |Retrieves all released versions for a Maven `group:artifact` from deps.dev, sorted most-recent first with `yyyy-MM-dd` publish dates.
            |Use to verify exact release history instead of hallucinated version numbers; then use `${ToolNames.INSPECT_DEPENDENCIES}` to check if the project already uses the library.
            |Covers the full Maven package index including packages published via the new Central Portal (central.sonatype.com).
        """.trimMargin()
    ) { args ->
        val parts = args.coordinates.split(":")
        if (parts.size < 2) {
            isError = true
            return@tool "coordinates must be in 'group:artifact' format (e.g. 'org.jetbrains.kotlinx:kotlinx-serialization-json')"
        }
        val group = parts[0]
        val artifact = parts[1]

        val allVersions = try {
            depsDevService.getMavenVersions(group, artifact)
        } catch (e: Exception) {
            emptyList()
        }

        if (allVersions.isEmpty()) {
            return@tool "No versions found for $group:$artifact"
        }

        "Versions for $group:$artifact:\n" + paginate(
            items = allVersions,
            pagination = args.pagination,
            itemName = "versions",
            total = allVersions.size
        ) { v -> "- ${v.version} (${v.publishedAt})" }
    }
}
