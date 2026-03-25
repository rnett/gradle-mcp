package dev.rnett.gradle.mcp.dependencies.model

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime

/**
 * Represents a directory containing dependency sources and its associated search index.
 */
interface SourcesDir {
    val sources: Path
    val rootForSearch: Path
    fun lastRefresh(): kotlin.time.Instant?
    fun resolveIndexDirs(providerName: String): List<Path>
}

/**
 * Represents a single dependency's content in the global Content-Addressable Storage (CAS) cache.
 * Identified by the content hash of its source archive.
 */
data class CASDependencySourcesDir(
    val hash: String,
    val baseDir: Path
) : SourcesDir {
    override val sources: Path = baseDir.resolve("sources")
    val index: Path = baseDir.resolve("index")
    override val rootForSearch: Path = sources

    val advisoryLockFile: Path = baseDir.resolveSibling("$hash.lock")
    val completionMarker: Path = baseDir.resolve(".completed")

    override fun lastRefresh(): kotlin.time.Instant? {
        if (completionMarker.exists()) {
            return try {
                kotlin.time.Instant.fromEpochMilliseconds(completionMarker.getLastModifiedTime().toMillis())
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    override fun resolveIndexDirs(providerName: String): List<Path> {
        val idxDir = index.resolve(providerName)
        return if (idxDir.exists()) listOf(idxDir) else emptyList()
    }
}

/**
 * Represents an ephemeral project-level view for a specific resolution session.
 * Contains junctions/symlinks to CAS entries and a manifest describing the project state.
 */
data class SessionViewSourcesDir(
    val sessionId: String,
    val baseDir: Path,
    override val sources: Path,
    val manifest: ProjectManifest,
    val casBaseDir: Path
) : SourcesDir {
    override val rootForSearch: Path = sources

    override fun lastRefresh(): kotlin.time.Instant? {
        return try {
            kotlin.time.Instant.parse(manifest.timestamp)
        } catch (e: Exception) {
            null
        }
    }

    override fun resolveIndexDirs(providerName: String): List<Path> {
        return manifest.dependencies.mapNotNull { dep ->
            val idxDir = casBaseDir.resolve(dep.hash.take(2)).resolve(dep.hash).resolve("index").resolve(providerName)
            if (idxDir.exists()) idxDir else null
        }
    }
}

/**
 * Legacy compatibility model for merged project sources.
 * Redefined to represent the new virtual view layout where possible.
 */
data class MergedSourcesDir(
    val storagePath: Path,
    val sourcesPath: Path,
    val metadataPath: Path
) : SourcesDir {
    override val sources = sourcesPath
    val index = metadataPath.resolve("index")
    override val rootForSearch = sourcesPath

    override fun lastRefresh(): kotlin.time.Instant? = null

    override fun resolveIndexDirs(providerName: String): List<Path> {
        val idxDir = index.resolve(providerName)
        return if (idxDir.exists()) listOf(idxDir) else emptyList()
    }
}

/**
 * Legacy compatibility model for single dependency sources.
 */
data class SingleDependencySourcesDir(
    val dependencyId: String,
    override val sources: Path,
    val index: Path,
    val lockFile: Path
) : SourcesDir {
    override val rootForSearch: Path = sources

    override fun lastRefresh(): kotlin.time.Instant? {
        val marker = sources.resolve(".extracted")
        if (marker.exists()) {
            return kotlin.time.Instant.fromEpochMilliseconds(marker.getLastModifiedTime().toMillis())
        }
        return null
    }

    override fun resolveIndexDirs(providerName: String): List<Path> {
        val idxDir = index.resolve(providerName)
        return if (idxDir.exists()) listOf(idxDir) else emptyList()
    }
}
