package dev.rnett.gradle.mcp

import kotlinx.serialization.Serializable
import kotlin.io.path.Path

@Serializable
class DockerConfig(val isDocker: Boolean, val paths: PathConfig) {
    @Serializable
    data class PathConfig(val convert: Boolean, val root: String)

    val converterIfDocker by lazy {
        if (isDocker && paths.convert)
            NewRootPathConverter(Path(paths.root))
        else
            null
    }
}