package dev.rnett.gradle.mcp.tools.dependencies

import dev.rnett.gradle.mcp.maven.MAVEN_CENTRAL_URL
import dev.rnett.gradle.mcp.maven.MavenCentralService
import dev.rnett.gradle.mcp.maven.MavenRepoService
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

class DependencySearchTools(
    private val mavenRepoService: MavenRepoService,
    private val mavenCentralService: MavenCentralService
) : McpServerComponent("Dependency Search Tools", "Tools for querying maven repositories for dependency information.") {

    @Serializable
    data class SearchMavenCentralInput(
        val query: String,
        val start: Int? = null,
        val limit: Int? = null
    )

    @Serializable
    data class ArtifactVersions(
        val groupId: String,
        val artifactId: String,
        val versions: List<String>,
        val classifier: String
    )

    @Serializable
    data class SearchMavenCentralOutput(
        val numFound: Int,
        val start: Int,
        val docs: List<ArtifactVersions>
    )

    val searchMavenCentral by tool<SearchMavenCentralInput, SearchMavenCentralOutput>(
        "search_maven_central",
        "Search Maven Central for artifacts. Replaces the latest version in the standard response with a full list of known versions."
    ) { input ->
        val start = input.start ?: 0
        val limit = input.limit ?: 20

        val response = mavenCentralService.searchCentral(input.query, start, limit)

        val docsWithVersions = coroutineScope {
            response.docs.map { doc ->
                async {
                    val versions = try {
                        mavenRepoService.getVersions(MAVEN_CENTRAL_URL, doc.groupId, doc.artifactId).reversed()
                    } catch (e: Exception) {
                        listOf(doc.latestVersion)
                    }
                    ArtifactVersions(
                        groupId = doc.groupId,
                        artifactId = doc.artifactId,
                        versions = versions,
                        classifier = doc.classifier
                    )
                }
            }.awaitAll()
        }

        SearchMavenCentralOutput(
            numFound = response.numFound,
            start = response.start,
            docs = docsWithVersions
        )
    }

    @Serializable
    data class SearchMavenVersionsInput(
        val group: String,
        val artifact: String,
        val repositories: List<String>? = null,
        val offset: Int? = null,
        val limit: Int? = null
    )

    @Serializable
    data class SearchMavenVersionsOutput(
        val versions: List<String>,
        val totalVersions: Int,
        val offset: Int,
        val limit: Int?
    )

    val searchMavenVersions by tool<SearchMavenVersionsInput, SearchMavenVersionsOutput>(
        "search_maven_versions",
        "Search for versions of a specific group and artifact across one or more Maven repositories. Returns versions sorted latest first."
    ) { input ->
        val repos = if (input.repositories.isNullOrEmpty()) listOf(MAVEN_CENTRAL_URL) else input.repositories
        val offset = input.offset ?: 0
        val limit = input.limit

        val allVersions = coroutineScope {
            repos.map { repo ->
                async {
                    try {
                        mavenRepoService.getVersions(repo, input.group, input.artifact)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()
                .flatten()
                .distinct()
                // Maven metadata returns versions in ascending order, we want descending (latest first)
                .reversed()
        }

        val totalVersions = allVersions.size
        val paginatedVersions = if (limit != null) {
            allVersions.drop(offset).take(limit)
        } else {
            allVersions.drop(offset)
        }

        SearchMavenVersionsOutput(
            versions = paginatedVersions,
            totalVersions = totalVersions,
            offset = offset,
            limit = limit
        )
    }

}
