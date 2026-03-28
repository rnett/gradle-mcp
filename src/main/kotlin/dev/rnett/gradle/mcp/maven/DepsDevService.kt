package dev.rnett.gradle.mcp.maven

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

interface DepsDevService {
    suspend fun getMavenVersions(group: String, artifact: String): List<DepsDevVersion>
}

data class DepsDevVersion(
    val version: String,
    val publishedAt: LocalDate
)

class DefaultDepsDevService(private val client: HttpClient) : DepsDevService {
    override suspend fun getMavenVersions(group: String, artifact: String): List<DepsDevVersion> {
        val url = "https://api.deps.dev/v3/systems/maven/packages/$group%3A$artifact"
        return client.get(url)
            .body<DepsDevPackageResponse>()
            .versions
            .mapNotNull { v ->
                val publishedAt = v.publishedAt ?: return@mapNotNull null
                DepsDevVersion(
                    version = v.versionKey.version,
                    publishedAt = Instant.parse(publishedAt).atZone(ZoneOffset.UTC).toLocalDate()
                )
            }
            .sortedByDescending { it.publishedAt }
    }
}

@Serializable
data class DepsDevPackageResponse(
    val versions: List<DepsDevVersionEntry> = emptyList()
)

@Serializable
data class DepsDevVersionEntry(
    val versionKey: DepsDevVersionKey,
    val publishedAt: String? = null
)

@Serializable
data class DepsDevVersionKey(
    val system: String = "",
    val name: String = "",
    val version: String
)
