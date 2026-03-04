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
        @Description("The search query (e.g., 'kotlinx-serialization') or authoritative GAV identifier (e.g., 'org.jetbrains.kotlinx:kotlinx-serialization-json').")
        val query: String,
        @Description("If true, lists all available versions for the specified 'group:artifact'. This is the authoritative way to explore an artifact's release history.")
        val versions: Boolean = false,
        @Description("The offset to start from in the results. Use this with 'limit' for efficient pagination through large search results.")
        val offset: Int? = null,
        @Description("The maximum number of results to return. Use a smaller limit to maintain token efficiency and reduce noise in your context.")
        val limit: Int? = null
    )

    val searchMavenCentral by tool<SearchMavenArtifactsArgs, String>(
        ToolNames.SEARCH_MAVEN_CENTRAL,
        """
            |The authoritative tool for discovering libraries and exploring version histories on Maven Central.
            |It provides surgical access to the world's largest repository of Java and Kotlin artifacts directly from your agentic workflow.
            |
            |### High-Performance Features
            |- **Precision Artifact Discovery**: Search for libraries by name, group, or description. Identify the exact Group:Artifact:Version (GAV) coordinates needed for your build file.
            |- **Exhaustive Version Auditing**: Set `versions=true` to retrieve the complete release history for any artifact. Ideal for identifying stable update paths or researching legacy versions.
            |- **Managed Pagination**: Efficiently browse large result sets using `offset` and `limit` to maintain optimal context usage.
            |
            |### Common Usage Patterns
            |- **General Search**: `search_maven_central(query="serialization")`
            |- **Find Latest GAV**: `search_maven_central(query="org.jetbrains.kotlinx:kotlinx-serialization-json")`
            |- **List All Versions**: `search_maven_central(query="org.junit.jupiter:junit-jupiter", versions=true)`
            |
            |Once you have identified a dependency, use the `inspect_dependencies` tool to check if it is already used in your project and audit its integration status.
            |For detailed discovery strategies, refer to the `gradle-dependencies` skill.
        """.trimMargin()
    ) { args ->
        if (args.versions) {
            val parts = args.query.split(":")
            if (parts.size < 2) {
                throw IllegalArgumentException("When versions=true, query must be in format 'group:artifact'")
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
