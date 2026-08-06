package dev.rnett.gradle.mcp.gradle

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import java.nio.file.Path

interface GradleConnectionService {
    fun connect(projectRoot: Path): ProjectConnection
}

class DefaultGradleConnectionService(
    private val gradleUserHome: Path? = null
) : GradleConnectionService {
    override fun connect(projectRoot: Path): ProjectConnection {
        val connector = GradleConnector.newConnector()
            .forProjectDirectory(projectRoot.toFile())
        if (gradleUserHome != null) {
            connector.useGradleUserHomeDir(gradleUserHome.toFile())
        }
        return connector.connect()
    }
}
