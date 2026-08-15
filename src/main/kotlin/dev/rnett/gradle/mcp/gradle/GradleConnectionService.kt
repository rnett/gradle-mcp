package dev.rnett.gradle.mcp.gradle

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

interface GradleConnectionService : AutoCloseable {
    fun connect(projectRoot: Path): ProjectConnection
}

class DefaultGradleConnectionService(
    private val gradleUserHome: Path? = null
) : GradleConnectionService {
    internal val connectors = ConcurrentHashMap<Path, GradleConnector>()

    override fun connect(projectRoot: Path): ProjectConnection {
        val connector = connectors.computeIfAbsent(projectRoot) {
            GradleConnector.newConnector()
                .forProjectDirectory(it.toFile())
                .also { c ->
                    if (gradleUserHome != null) {
                        c.useGradleUserHomeDir(gradleUserHome.toFile())
                    }
                }
        }
        return connector.connect()
    }

    override fun close() {
        for (connector in connectors.values) {
            runCatching { connector.disconnect() }
        }
        connectors.clear()
    }
}
