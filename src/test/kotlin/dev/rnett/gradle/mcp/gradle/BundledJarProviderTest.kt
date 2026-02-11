package dev.rnett.gradle.mcp.gradle

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledJarProviderTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `extracting a jar creates file in working dir`() {
        // Use a resource that actually exists in the classpath for sure during tests.
        // We know init scripts are there.
        val resource = "init-scripts/repl-env.init.gradle.kts"
        val provider = DefaultBundledJarProvider(tempDir)
        val jarPath = provider.extractJar(resource)

        assertTrue(jarPath.exists(), "Extracted file $jarPath should exist")
        assertTrue(jarPath.name.startsWith("init-scripts-repl-env.init.gradle"), "Name should match")
    }

    @Test
    fun `extracting twice returns the same path and doesn't fail`() {
        val resource = "init-scripts/repl-env.init.gradle.kts"
        val provider = DefaultBundledJarProvider(tempDir)
        val jarPath1 = provider.extractJar(resource)
        val jarPath2 = provider.extractJar(resource)

        assertEquals(jarPath1, jarPath2)
    }
}
