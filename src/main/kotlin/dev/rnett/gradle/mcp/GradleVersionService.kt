package dev.rnett.gradle.mcp

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/** The resolved latest stable Gradle version and how it was obtained. */
data class LatestStableGradleVersion(val version: String, val source: Source) {
    enum class Source { FETCHED_LIVE, BUNDLED_FALLBACK }
}

interface GradleVersionService {
    /**
     * Resolves a Gradle version alias (like "current" or "latest") to a concrete version string (e.g., "8.6.1").
     * If [version] is already a concrete version, it is returned as-is.
     * If [version] is null, it defaults to resolving "current".
     */
    suspend fun resolveVersion(version: String?): String

    /**
     * Resolves the latest stable Gradle version, live from services.gradle.org when reachable, falling back to the
     * bundled Gradle version otherwise. Never throws.
     */
    suspend fun resolveLatestStable(): LatestStableGradleVersion
}

@Serializable
private data class GradleVersionResponse(val version: String)

class DefaultGradleVersionService(
    private val httpClient: HttpClient,
    private val versionConnectTimeoutMillis: Long = 2_000,
    private val versionRequestTimeoutMillis: Long = 5_000
) : GradleVersionService {

    private val logger = LoggerFactory.getLogger(DefaultGradleVersionService::class.java)
    private val cache = ConcurrentHashMap<String, LatestStableGradleVersion>()

    override suspend fun resolveVersion(version: String?): String {
        val target = if (version == null || version.lowercase() == "current" || version.lowercase() == "latest") "current" else version

        // If it's not a known alias, assume it's a concrete version
        if (target != "current") {
            return target
        }

        return resolveLatestStable().version
    }

    override suspend fun resolveLatestStable(): LatestStableGradleVersion {
        cache["current"]?.let { return it }

        return try {
            logger.debug("Fetching latest stable Gradle version from services.gradle.org...")
            val response: GradleVersionResponse = httpClient.get("https://services.gradle.org/versions/current") {
                timeout {
                    connectTimeoutMillis = versionConnectTimeoutMillis
                    requestTimeoutMillis = versionRequestTimeoutMillis
                }
            }.body()
            logger.info("Resolved 'current' Gradle version to ${response.version}")
            val fetched = LatestStableGradleVersion(response.version, LatestStableGradleVersion.Source.FETCHED_LIVE)
            cache.putIfAbsent("current", fetched)
            fetched
        } catch (e: Exception) {
            logger.warn("Failed to fetch latest stable Gradle version, falling back to ${BuildConfig.GRADLE_VERSION}", e)
            LatestStableGradleVersion(BuildConfig.GRADLE_VERSION, LatestStableGradleVersion.Source.BUNDLED_FALLBACK)
        }
    }
}
