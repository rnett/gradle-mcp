package dev.rnett.gradle.mcp.gradle

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test

class InitScriptProviderTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `extracting init scripts creates files in working dir`() {
        val provider = DefaultInitScriptProvider(tempDir)
        val scripts = provider.extractInitScripts(listOf("task-out"))

        assert(scripts.size == 1)
        val path = scripts.first()
        assert(path.exists())
        assert(path.fileName.toString().startsWith("task-out"))
        assert(path.fileName.toString().endsWith(".init.gradle.kts"))
        // Check that it contains a hash (8 hex chars before the extension)
        val name = path.fileName.toString()
        val baseName = name.removeSuffix(".init.gradle.kts")
        assert(baseName.contains("-"))
        val hash = baseName.substringAfterLast("-")
        assert(hash.length == 8)
    }

    @Test
    fun `extracting twice returns the same paths and doesn't fail`() {
        val provider = DefaultInitScriptProvider(tempDir)
        val scripts1 = provider.extractInitScripts(listOf("task-out"))
        val scripts2 = provider.extractInitScripts(listOf("task-out"))

        assert(scripts1 == scripts2)
    }

    @Test
    fun `extracting non-existent script returns empty list`() {
        val provider = DefaultInitScriptProvider(tempDir)
        val scripts = provider.extractInitScripts(listOf("non-existent"))
        assert(scripts.isEmpty())
    }
}
