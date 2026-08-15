package dev.rnett.gradle.mcp.gradle

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import java.nio.file.Paths
import kotlin.test.assertTrue

class GradleConnectionServiceTest {

    private class RecordingConnector(
        private val shouldThrowOnDisconnect: Boolean = false
    ) : GradleConnector() {
        var disconnectCalled = false

        override fun useInstallation(gradleInstallation: File): GradleConnector = this
        override fun useGradleVersion(gradleVersion: String): GradleConnector = this
        override fun useDistribution(gradleDistribution: URI): GradleConnector = this
        override fun useBuildDistribution(): GradleConnector = this
        override fun forProjectDirectory(projectDir: File): GradleConnector = this
        override fun useGradleUserHomeDir(gradleUserHomeDir: File): GradleConnector = this
        override fun connect(): ProjectConnection = throw UnsupportedOperationException("not needed in unit test")
        override fun disconnect() {
            disconnectCalled = true
            if (shouldThrowOnDisconnect) throw RuntimeException("disconnect failure")
        }
    }

    @Test
    @Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK")
    fun `GradleConnectionService extends AutoCloseable`() {
        val service: GradleConnectionService = DefaultGradleConnectionService()
        assertTrue(service is AutoCloseable)
        service.close()
    }

    @Test
    fun `close calls disconnect on all managed connectors`() {
        val service = DefaultGradleConnectionService()
        val c1 = RecordingConnector()
        val c2 = RecordingConnector()
        val c3 = RecordingConnector()

        service.connectors[Paths.get("/tmp/projectA")] = c1
        service.connectors[Paths.get("/tmp/projectB")] = c2
        service.connectors[Paths.get("/tmp/projectC")] = c3

        service.close()

        assertTrue(c1.disconnectCalled, "c1 disconnect should be called")
        assertTrue(c2.disconnectCalled, "c2 disconnect should be called")
        assertTrue(c3.disconnectCalled, "c3 disconnect should be called")
        assertTrue(service.connectors.isEmpty(), "connectors map should be cleared after close")
    }

    @Test
    fun `close handles disconnect exceptions and still clears map`() {
        val service = DefaultGradleConnectionService()
        val c1 = RecordingConnector(shouldThrowOnDisconnect = true)
        val c2 = RecordingConnector()
        val c3 = RecordingConnector(shouldThrowOnDisconnect = true)

        service.connectors[Paths.get("/tmp/p1")] = c1
        service.connectors[Paths.get("/tmp/p2")] = c2
        service.connectors[Paths.get("/tmp/p3")] = c3

        // should not throw despite failures
        service.close()

        assertTrue(c1.disconnectCalled)
        assertTrue(c2.disconnectCalled)
        assertTrue(c3.disconnectCalled)
        assertTrue(service.connectors.isEmpty())
    }

    @Test
    fun `close is safe when no connectors`() {
        val service = DefaultGradleConnectionService()
        // should not throw
        service.close()
        assertTrue(service.connectors.isEmpty())
    }

    @Test
    fun `close can be called multiple times safely`() {
        val service = DefaultGradleConnectionService()
        val c1 = RecordingConnector()
        service.connectors[Paths.get("/tmp/projectA")] = c1

        service.close()
        assertTrue(c1.disconnectCalled)
        assertTrue(service.connectors.isEmpty())

        // second close should be no-op and not throw
        service.close()
        assertTrue(service.connectors.isEmpty())
    }
}
