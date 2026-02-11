package dev.rnett.gradle.mcp.gradle

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue

class InitScriptProviderTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `extracting init scripts creates files in working dir`() {
        val provider = DefaultInitScriptProvider(tempDir)
        val scripts = provider.extractInitScripts()

        assertTrue(scripts.isNotEmpty(), "Should have extracted at least one script")
        scripts.forEach { path ->
            assertTrue(path.exists(), "Extracted script $path should exist")
            assertTrue(path.fileName.toString().endsWith(".init.gradle.kts"), "Should be a kts init script")
            // Check that it contains a hash (8 hex chars before the extension)
            val name = path.fileName.toString()
            val baseName = name.removeSuffix(".init.gradle.kts")
            assertTrue(baseName.contains("-"), "Should contain a hyphen before the hash")
            val hash = baseName.substringAfterLast("-")
            assertTrue(hash.length == 8, "Hash should be 8 characters long, got $hash")
        }
    }

    @Test
    fun `extracting twice returns the same paths and doesn't fail`() {
        val provider = DefaultInitScriptProvider(tempDir)
        val scripts1 = provider.extractInitScripts()
        val scripts2 = provider.extractInitScripts()

        kotlin.test.assertEquals(scripts1, scripts2)
    }
}
