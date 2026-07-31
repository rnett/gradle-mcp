package dev.rnett.gradle.mcp

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

/** Locates the repository root by walking up from the test working directory to `settings.gradle.kts`. */
internal fun findProjectRoot(): Path {
    var current: Path? = Path("").toAbsolutePath()
    while (current != null) {
        if (current.resolve("settings.gradle.kts").exists()) return current
        current = current.parent
    }
    error("Could not locate project root (no settings.gradle.kts found above the test working directory)")
}
