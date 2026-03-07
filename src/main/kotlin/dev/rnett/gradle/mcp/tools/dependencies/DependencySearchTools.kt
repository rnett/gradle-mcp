package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.maven.MAVEN_CENTRAL_URL
import dev.rnett.gradle.mcp.maven.MavenCentralService
import dev.rnett.gradle.mcp.maven.MavenRepoService
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.tools.ToolNames
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

class DependencySearchTools(
    private val mavenRepoService: MavenRepoService,
    private val mavenCentralService: MavenCentralService
) : McpServerComponent("Dependency Search Tools", "Tools for querying maven repositories for dependency information.") {

    @Serializable
    data class SearchMavenArtifactsArgs(
        @Description("Searching for artifacts by name, group, or coordinates. If `versions=true`, MUST be exactly 'group:artifact' (e.g. 'org.jetbrains.kotlinx:kotlinx-serialization-json').")
        val query: String,
        @Description("Setting to true retrieves all available versions for a 'group:artifact'. Ideal for researching release history.")
        val versions: Boolean = false,
        @Description("Specifying an offset for large results to enable efficient pagination.")
        val offset: Int? = null,
        @Description("Limiting the number of results to maintain token efficiency and reduce noise. Default is 10.")
        val limit: Int? = null
    )

    val searchMavenCentral by tool<SearchMavenArtifactsArgs, String>(
        ToolNames.SEARCH_MAVEN_CENTRAL,
        """
            |ALWAYS use this tool to search Maven Central for library coordinates and version histories instead of relying on hallucinated versions or web searches.
            |It provides direct, paginated access to the authoritative artifact repository.
            |
            |### Discovery Best Practices
            |
            |1.  **Coordinate Discovery**: Search by name, group, or a snippet of the artifact ID.
            |2.  **Version Research**: Set `versions=true` and provide a `group:artifact` query to list ALL released versions. This is the professionally recommended way to find stable versions.
            |3.  **Auditing Project Usage**: Once identified, use `${ToolNames.INSPECT_DEPENDENCIES}` to check if the project already uses the library.
            |4.  **Pagination**: Use `offset` and `limit` to browse numerous matches for large queries.
        """.trimMargin()
    ) { args ->
        if (args.versions) {
            val parts = args.query.split(":")
            if (parts.size < 2) {
                isError = true
                return@tool "When versions=true, query must be in format 'group:artifact' (e.g., 'org.jetbrains.kotlinx:kotlinx-serialization-json')"
            }
            val group = parts[0]
            val artifact = parts[1]
            val offset = args.offset ?: 0
            val limit = args.limit

            val allVersions = try {
                mavenRepoService.getVersions(MAVEN_CENTRAL_URL, group, artifact).reversed()
            } catch (e: Exception) {
                emptyList()
            }

            if (allVersions.isEmpty()) {
                return@tool "No versions found for $group:$artifact"
            }

            val totalVersions = allVersions.size
            val paginatedVersions = if (limit != null) {
                allVersions.drop(offset).take(limit)
            } else {
                allVersions.drop(offset)
            }

            buildString {
                appendLine("Versions for $group:$artifact (Total: $totalVersions):")
                paginatedVersions.forEach { appendLine("- $it") }
                if (limit != null && totalVersions > offset + limit) {
                    appendLine("... and ${totalVersions - (offset + limit)} more versions")
                }
            }
        } else {
            val start = args.offset ?: 0
            val limit = args.limit ?: 10

            val response = mavenCentralService.searchCentral(args.query, start, limit)

            if (response.docs.isEmpty()) {
                return@tool "No artifacts found for '${args.query}'"
            }

            buildString {
                appendLine("Found ${response.numFound} artifacts for '${args.query}':\n")
                response.docs.forEach { doc ->
                    val latestVersion = try {
                        mavenRepoService.getVersions(MAVEN_CENTRAL_URL, doc.groupId, doc.artifactId).reversed().firstOrNull()
                    } catch (e: Exception) {
                        null
                    }

                    appendLine("- ${doc.groupId}:${doc.artifactId}")
                    appendLine("  Latest Version: ${latestVersion ?: doc.latestVersion}")
                    if (doc.classifier.isNotBlank()) {
                        appendLine("  Classifier: ${doc.classifier}")
                    }
                    appendLine()
                }
                if (response.numFound > start + response.docs.size) {
                    appendLine("... and ${response.numFound - (start + response.docs.size)} more results")
                }
            }
        }
    }
}
