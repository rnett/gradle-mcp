package dev.rnett.gradle.mcp.utils

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class FileUtilsTest {

    @Test
    fun `createSymbolicLink handles junctions on Windows`(@TempDir tempDir: Path) {
        val target = tempDir.resolve("target")
        target.createDirectories()
        val link = tempDir.resolve("link")

        val result = FileUtils.createSymbolicLink(link, target)

        assertTrue(result, "Should have created a symlink or junction")
        assertTrue(link.exists(), "Link should exist")
        assertTrue(link.isDirectory(), "Link should be a directory")
    }
}
