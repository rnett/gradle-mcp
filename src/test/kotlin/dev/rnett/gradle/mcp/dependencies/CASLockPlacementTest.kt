package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CASLockPlacementTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `test lock files are siblings of CAS directory`() {
        val hash = "abcdef1234567890"
        val casBaseDir = tempDir.resolve("cas").resolve(hash.take(2)).resolve(hash)
        val casDir = CASDependencySourcesDir(hash, casBaseDir)

        val baseLock = casDir.baseLockFile
        val fullTextLock = casDir.indexLockFile("FullTextSearch")

        // Should be siblings of casBaseDir
        assertEquals(casBaseDir.parent.resolve("$hash.base.lock"), baseLock)
        assertEquals(casBaseDir.parent.resolve("$hash.index.FullTextSearch.lock"), fullTextLock)

        // Verify they are NOT inside casBaseDir
        assertFalse(baseLock.startsWith(casBaseDir))
        assertFalse(fullTextLock.startsWith(casBaseDir))

        // Verify they are in the same parent directory as casBaseDir
        assertEquals(casBaseDir.parent, baseLock.parent)
        assertEquals(casBaseDir.parent, fullTextLock.parent)
    }
}
