package dev.rnett.gradle.mcp.dependencies.model

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.readText

interface SourcesDir {
    val sources: Path
    val index: Path
    val lockFile: Path
    val rootForSearch: Path
    fun lastRefresh(): kotlin.time.Instant?
}

data class MergedSourcesDir(
    val storagePath: Path,
    override val lockFile: Path,
    override val rootForSearch: Path
) : SourcesDir {
    override val sources = storagePath.resolve("sources")
    val metadata = storagePath.resolve("metadata")
    override val index = metadata.resolve("index")
    val lastRefreshFile = metadata.resolve(".last_refresh")

    override fun lastRefresh(): kotlin.time.Instant? {
        if (lastRefreshFile.exists()) {
            return try {
                kotlin.time.Instant.parse(lastRefreshFile.readText())
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}

data class SingleDependencySourcesDir(
    val dependencyId: String,
    override val sources: Path,
    override val index: Path,
    override val lockFile: Path
) : SourcesDir {
    override val rootForSearch: Path = sources

    @OptIn(ExperimentalPathApi::class)
    override fun lastRefresh(): kotlin.time.Instant? {
        val marker = sources.resolve(".extracted")
        if (marker.exists()) {
            return kotlin.time.Instant.fromEpochMilliseconds(marker.getLastModifiedTime().toMillis())
        }
        return null
    }
}
