package dev.rnett.gradle.mcp.maven

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2/"

interface MavenCentralService {
    suspend fun searchCentral(
        query: String,
        start: Int,
        results: Int
    ): MavenCentralSearchResponse.Response
}

class DefaultMavenCentralService(private val client: HttpClient) : MavenCentralService {
    override suspend fun searchCentral(
        query: String,
        start: Int,
        results: Int
    ): MavenCentralSearchResponse.Response {
        val url = URLBuilder("https://search.maven.org/solrsearch/select").apply {
            parameters.append("q", query)
            parameters.append("rows", results.toString())
            parameters.append("start", start.toString())
            parameters.append("wt", "json")
        }.build()
        return client.get(url)
            .body<MavenCentralSearchResponse>().response
    }
}

@Serializable
data class MavenCentralSearchResponse(
    val response: Response,
) {
    @Serializable
    data class Response(
        val numFound: Int,
        val start: Int,
        val docs: List<ArtifactResult>
    )

    @Serializable
    data class ArtifactResult(
        @SerialName("g")
        val groupId: String,
        @SerialName("a")
        val artifactId: String,
        @SerialName("latestVersion")
        val latestVersion: String,
        @SerialName("p")
        val classifier: String
    )
}
