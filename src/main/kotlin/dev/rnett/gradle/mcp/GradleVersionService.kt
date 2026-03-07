package dev.rnett.gradle.mcp

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

interface GradleVersionService {
    /**
     * Resolves a Gradle version alias (like "current" or "latest") to a concrete version string (e.g., "8.6.1").
     * If [version] is already a concrete version, it is returned as-is.
     * If [version] is null, it defaults to resolving "current".
     */
    suspend fun resolveVersion(version: String?): String
}

@Serializable
private data class GradleVersionResponse(val version: String)

class DefaultGradleVersionService(
    private val httpClient: HttpClient
) : GradleVersionService {

    private val logger = LoggerFactory.getLogger(DefaultGradleVersionService::class.java)
    private val cache = ConcurrentHashMap<String, String>()

    override suspend fun resolveVersion(version: String?): String {
        val target = if (version == null || version.lowercase() == "current" || version.lowercase() == "latest") "current" else version

        // If it's not a known alias, assume it's a concrete version
        if (target != "current") {
            return target
        }

        return cache.getOrPut(target) {
            fetchLatestStableVersion()
        }
    }

    private suspend fun fetchLatestStableVersion(): String {
        return try {
            logger.debug("Fetching latest stable Gradle version from services.gradle.org...")
            val response: GradleVersionResponse = httpClient.get("https://services.gradle.org/versions/current").body()
            logger.info("Resolved 'current' Gradle version to ${response.version}")
            response.version
        } catch (e: Exception) {
            logger.warn("Failed to fetch latest stable Gradle version, falling back to ${BuildConfig.GRADLE_VERSION}", e)
            BuildConfig.GRADLE_VERSION
        }
    }
}
