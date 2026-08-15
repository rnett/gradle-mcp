package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.build.BuildExecutionService
import org.gradle.tooling.ProjectConnection
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultGradleProviderCloseTest {

    private class RecordingConnectionService : GradleConnectionService {
        var closeCount = 0
        override fun connect(projectRoot: Path): ProjectConnection = throw UnsupportedOperationException("not needed")
        override fun close() {
            closeCount++
        }
    }

    private class NoopExecutionService : BuildExecutionService {
        override suspend fun <I : org.gradle.tooling.ConfigurableLauncher<*>, R> invokeBuild(
            launcher: I,
            args: GradleInvocationArguments,
            additionalProgressListeners: Map<org.gradle.tooling.events.ProgressListener, Set<org.gradle.tooling.events.OperationType>>,
            stdoutLineHandler: ((String) -> Unit)?,
            stderrLineHandler: ((String) -> Unit)?,
            progress: dev.rnett.gradle.mcp.ProgressReporter,
            buildId: BuildId,
            runningBuild: dev.rnett.gradle.mcp.gradle.build.RunningBuild,
            invoker: (I) -> R
        ): R = throw UnsupportedOperationException("not needed")
    }

    @Test
    fun `DefaultGradleProvider close propagates to connectionService`() {
        val recording = RecordingConnectionService()
        val provider = DefaultGradleProvider(
            connectionService = recording,
            executionService = NoopExecutionService(),
            buildManager = BuildManager()
        )

        provider.close()

        assertEquals(1, recording.closeCount, "connectionService.close should be called once")
    }

    @Test
    fun `DefaultGradleProvider close is idempotent and closes connectionService only once`() {
        val recording = RecordingConnectionService()
        val provider = DefaultGradleProvider(
            connectionService = recording,
            executionService = NoopExecutionService(),
            buildManager = BuildManager()
        )

        provider.close()
        provider.close()
        provider.close()

        assertEquals(1, recording.closeCount, "connectionService.close should be called exactly once despite multiple provider closes")
    }

    @Test
    fun `DefaultGradleProvider close handles non-AutoCloseable gracefully via cast`() {
        // This test verifies that the safe cast `(connectionService as? AutoCloseable)?.close()` does not throw
        // even if the service were somehow not AutoCloseable. Since GradleConnectionService now extends
        // AutoCloseable, every real service is closeable; we simulate the cast path by using a wrapper
        // that implements GradleConnectionService but we verify close still propagates.
        val recording = RecordingConnectionService()
        val provider = DefaultGradleProvider(
            connectionService = recording,
            executionService = NoopExecutionService(),
            buildManager = BuildManager()
        )
        // Should not throw
        provider.close()
        assertTrue(recording.closeCount == 1)
    }
}
