package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.MergedSourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SingleDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

@OptIn(ExperimentalPathApi::class)
class SourcesCacheManager(private val environment: GradleMcpEnvironment) {
    private val logger = LoggerFactory.getLogger(SourcesCacheManager::class.java)

    val sourcesDir = environment.cacheDir.resolve("sources")
    val globalSourcesDir = environment.cacheDir.resolve("extracted-sources")

    init {
        sourcesDir.createDirectories()
        globalSourcesDir.createDirectories()
    }

    fun sourcesDirectory(projectRoot: GradleProjectRoot, path: String, kind: String): MergedSourcesDir {
        val storagePath = sourcesDir.resolve("${projectRoot.projectRoot.hashCode()}-${path.hashCode()}-${kind.hashCode()}")
        return MergedSourcesDir(storagePath, lockFile(storagePath), globalSourcesDir)
    }

    fun singleDependencyDirectory(dep: GradleDependency): SingleDependencySourcesDir {
        val group = requireNotNull(dep.group)
        val sourcesFile = requireNotNull(dep.sourcesFile)
        val dir = globalSourcesDir.resolve(Path.of(group).resolve(sourcesFile.nameWithoutExtension))
        val lockFile = globalLockFile(dir)
        // Note: The actual index dir is managed by IndexService, but we provide it here for model completeness
        // It's technically internal to the processor and service how they get the index dir.
        return SingleDependencySourcesDir(dep.id, dir, Path.of(""), lockFile)
    }

    fun lockFile(storagePath: Path): Path {
        return environment.lockFile(storagePath, "sources")
    }

    fun globalLockFile(dir: Path): Path {
        val group = dir.parent.fileName.toString()
        val versionedArtifactName = dir.fileName.toString()
        return environment.cacheDir.resolve(".locks").resolve("extracted-sources").resolve("$group-$versionedArtifactName.lock")
    }

    fun dependencyHash(deps: Sequence<GradleDependency>): String {
        return deps
            .filter { it.hasSources }
            .map { "${it.id}:${it.sourcesFile}" }
            .sorted()
            .joinToString("\n")
            .hashCode()
            .toString()
    }

    fun checkCached(dir: MergedSourcesDir, currentHash: String, forceDownload: Boolean): Boolean {
        val hashFile = dir.storagePath.resolve(".dependencies.hash")
        if (forceDownload) {
            dir.storagePath.deleteRecursively()
            return false
        }
        return hashFile.exists() && hashFile.readText() == currentHash
    }

    fun saveCache(dir: MergedSourcesDir, currentHash: String) {
        dir.storagePath.createDirectories()
        dir.metadata.createDirectories()
        dir.storagePath.resolve(".dependencies.hash").writeText(currentHash)
        dir.lastRefreshFile.writeText(kotlin.time.Clock.System.now().toString())
    }

    fun tryGetCachedMergedSources(
        dir: MergedSourcesDir,
        fresh: Boolean,
        forceDownload: Boolean
    ): SourcesDir? {
        if (fresh || forceDownload) return null

        if (dir.sources.exists()) {
            return dir
        }
        return null
    }
}

