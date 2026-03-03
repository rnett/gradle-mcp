package dev.rnett.gradle.mcp.maven

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import java.net.URI

fun URI.resolveSegment(segment: String) = resolve(segment.trimStart('/').trimEnd('/') + "/")

interface MavenRepoService {
    suspend fun getVersions(
        repository: String,
        group: String,
        artifact: String
    ): List<String>
}

class DefaultMavenRepoService(private val client: HttpClient) : MavenRepoService {

    fun gaUri(group: String, artifact: String) = URI.create("${group.replace('.', '/')}/$artifact/")
    fun gavUri(group: String, artifact: String, version: String) = gaUri(group, artifact).resolveSegment(version)


    override suspend fun getVersions(
        repository: String,
        group: String,
        artifact: String
    ): List<String> {
        val url = URI.create(repository).resolve(gaUri(group, artifact)).resolve("maven-metadata.xml")
        return client.get(url.toString()) {
            header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
        }.body<MavenMetadata>().versioning?.versions ?: emptyList()
    }

}

@Serializable
@SerialName("metadata")
data class MavenMetadata(
    @XmlElement(true)
    val groupId: String? = null,
    @XmlElement(true)
    val artifactId: String? = null,
    @XmlElement(true)
    val versioning: Versioning? = null
) {
    @Serializable
    @SerialName("versioning")
    data class Versioning(
        @XmlElement(true)
        val latest: String? = null,
        @XmlElement(true)
        val release: String? = null,
        @XmlElement(true)
        val lastUpdated: Long? = null,
        @XmlChildrenName("version")
        val versions: List<String> = emptyList(),
        @XmlChildrenName("snapshotVersion")
        val snapshotVersions: List<SnapshotVersion> = emptyList()
    )

    @Serializable
    @SerialName("snapshotVersion")
    data class SnapshotVersion(
        @XmlElement(true)
        val extension: String? = null,
        @XmlElement(true)
        val value: String? = null,
        @XmlElement(true)
        val updated: Long? = null
    )
}
