package dev.rnett.gradle.mcp.gradle

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import java.nio.file.Path

interface GradleConnectionService {
    fun connect(projectRoot: Path): ProjectConnection
}

class DefaultGradleConnectionService : GradleConnectionService {
    override fun connect(projectRoot: Path): ProjectConnection {
        return GradleConnector.newConnector()
            .forProjectDirectory(projectRoot.toFile())
            .connect()
    }
}
