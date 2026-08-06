package dev.rnett.gradle.mcp.fixtures

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

object SharedTestInfrastructure {
    val sharedWorkingDir: Path by lazy {
        Files.createTempDirectory("gradle-mcp-shared-").also { dir ->
            Runtime.getRuntime().addShutdownHook(Thread {
                dir.toFile().deleteRecursively()
            })
        }
    }

    val sharedMcpWorkingDir: Path by lazy {
        sharedWorkingDir.resolve("mcp-working-dir").createDirectories()
    }

    /**
     * A stable, build-owned directory holding Gradle wrapper distributions shared by every
     * controlled test Gradle user home ([dev.rnett.gradle.mcp.fixtures.gradle.TestGradleUserHome]).
     *
     * The path comes from the `GRADLE_MCP_TEST_SHARED_DISTS_DIR` system property set by the test
     * tasks in `build.gradle.kts` (a directory under `build/`), which is stable across forks and
     * runs (until `clean`); if the property is unset the directory falls back to a per-fork temp
     * location. Because the property value is a build-owned path, it is never the developer's real
     * user home, so controlled homes never touch the developer's `~/.gradle`.
     */
    val sharedDistsDir: Path by lazy {
        val configured = System.getProperty("GRADLE_MCP_TEST_SHARED_DISTS_DIR")
        val dir = if (!configured.isNullOrBlank()) Path.of(configured) else sharedWorkingDir.resolve("gradle-dists")
        dir.createDirectories()
        dir
    }
}
