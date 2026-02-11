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
        val scripts = provider.extractInitScripts()

        assert(scripts.isNotEmpty())
        scripts.forEach { path ->
            assert(path.exists())
            assert(path.fileName.toString().endsWith(".init.gradle.kts"))
            // Check that it contains a hash (8 hex chars before the extension)
            val name = path.fileName.toString()
            val baseName = name.removeSuffix(".init.gradle.kts")
            assert(baseName.contains("-"))
            val hash = baseName.substringAfterLast("-")
            assert(hash.length == 8)
        }
    }

    @Test
    fun `extracting twice returns the same paths and doesn't fail`() {
        val provider = DefaultInitScriptProvider(tempDir)
        val scripts1 = provider.extractInitScripts()
        val scripts2 = provider.extractInitScripts()

        assert(scripts1 == scripts2)
    }
}
