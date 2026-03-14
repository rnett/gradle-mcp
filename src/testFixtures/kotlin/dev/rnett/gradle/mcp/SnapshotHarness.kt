package dev.rnett.gradle.mcp

import org.opentest4j.AssertionFailedError
import org.opentest4j.FileInfo
import java.io.File

class SnapshotHarness(
    private val srcDir: File,
    private val expectedDir: File,
    private val updateSnapshots: Boolean = System.getProperty("docs.updateSnapshots")?.toBoolean() ?: false
) {
    fun assertSnapshot(relPath: String, actual: String) {
        val expectedFile = File(expectedDir, relPath)
        if (updateSnapshots) {
            expectedFile.parentFile.mkdirs()
            expectedFile.writeText(actual)
        } else {
            if (!expectedFile.exists()) {
                throw AssertionError("Snapshot file not found: ${expectedFile.absolutePath}. Run with -Ddocs.updateSnapshots=true to generate it.")
            }
            val expected = expectedFile.readText()
            if (expected != actual) {
                throw AssertionFailedError(
                    "Snapshot mismatch for $relPath",
                    FileInfo(expectedFile.absolutePath, expected.toByteArray()),
                    FileInfo("actual://$relPath", actual.toByteArray())
                )
            }
        }
    }
}