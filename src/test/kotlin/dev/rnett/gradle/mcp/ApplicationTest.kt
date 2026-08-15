package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.gradle.GradleConnectionService
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationTest {

    /**
     * Builds an [Application] with a [Transport.Stdio] that is never started. Because
     * [Transport.Stdio.start] is never invoked, `stop()` skips the server-resolution path
     * (which would otherwise hit the network for the latest stable Gradle version), keeping
     * this a fast, hermetic unit test of the teardown lifecycle.
     */
    private fun stdioApp(): Application {
        val input = ByteArrayInputStream(ByteArray(0)).asSource().buffered()
        val output = ByteArrayOutputStream().asSink().buffered()
        return Application(emptyArray(), Transport.Stdio(input, output))
    }

    @Test
    fun `stop cancels the application coroutine scope`() = runBlocking {
        val app = stdioApp()
        try {
            assertTrue(app.scope.isActive, "scope should be active before stop")
            app.stop()
            assertFalse(app.scope.isActive, "scope should be cancelled after stop")
        } finally {
            app.stop()
        }
    }

    @Test
    fun `stop closes the Koin container`() = runBlocking {
        val app = stdioApp()
        try {
            // Sanity: the bean resolves before teardown, so a later failure proves the container closed.
            assertTrue(runCatching { app.koinContext.get<GradleConnectionService>() }.isSuccess)
            app.stop()
            val resolutionAfterClose = runCatching { app.koinContext.get<GradleConnectionService>() }.isFailure
            assertTrue(resolutionAfterClose, "resolving a bean after stop should fail because Koin is closed")
        } finally {
            app.stop()
        }
    }

    @Test
    fun `stop is idempotent and safe to call multiple times`() = runBlocking {
        val app = stdioApp()
        runCatching { app.stop() }.getOrThrow()
        runCatching { app.stop() }.getOrThrow()
        runCatching { app.stop() }.getOrThrow()

        assertFalse(app.scope.isActive, "scope should remain cancelled across repeated stop calls")
    }

    @Test
    fun `stop disposes AutoCloseable singletons without throwing`() = runBlocking {
        // Exercises the stop() path that resolves and closes the closeable singletons and then
        // closes the Koin container, asserting the whole teardown runs cleanly end-to-end.
        val app = stdioApp()
        runCatching { app.stop() }.getOrThrow()
        assertFalse(app.scope.isActive)
    }
}
