package dev.rnett.gradle.mcp.mcp.fixtures

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
}
