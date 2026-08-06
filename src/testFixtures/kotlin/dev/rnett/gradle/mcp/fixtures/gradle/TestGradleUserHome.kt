package dev.rnett.gradle.mcp.fixtures.gradle

import dev.rnett.gradle.mcp.fixtures.SharedTestInfrastructure
import dev.rnett.gradle.mcp.utils.OS
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * A controlled temporary Gradle user home for nested-build tests.
 *
 * The home pins both the connector channel (`GradleConnector.useGradleUserHomeDir` via the
 * home-bound provider) and the resolver-visible channels to one deterministic directory, so daemon
 * JVM settings detection never reads the developer's real `~/.gradle`. Its wrapper distributions
 * are junctioned to [SharedTestInfrastructure.sharedDistsDir], a stable build-owned cache, so
 * controlled homes do not re-download the fixture Gradle distribution on every run.
 *
 * Instances are meant to be class-scoped (one home per test class): a distribution is provisioned
 * once per class and daemons self-expire via the test idle-timeout argument. Tests that mutate the
 * home contents must reset state per test (e.g. [deleteGradleProperties]) to avoid cross-test
 * contamination.
 */
class TestGradleUserHome private constructor(val path: Path) : AutoCloseable {

    val gradleProperties: Path get() = path.resolve("gradle.properties")

    /** Writes (replacing) the home's `gradle.properties`. */
    fun writeGradleProperties(content: String) {
        path.createDirectories()
        gradleProperties.writeText(content)
    }

    /** Deletes the home's `gradle.properties` if present. */
    fun deleteGradleProperties() {
        Files.deleteIfExists(gradleProperties)
    }

    /**
     * Removes any daemon-JVM-affecting settings so a fresh test observes an empty home. Currently
     * equivalent to [deleteGradleProperties]; kept as the explicit reset hook for tests that mutate
     * home contents.
     */
    fun resetDaemonSettings() {
        deleteGradleProperties()
    }

    override fun close() {
        // Remove the dists junction/symlink FIRST as a link only; deleting through it would follow
        // into the shared distributions cache and destroy the build-wide cache.
        try {
            Files.deleteIfExists(path.resolve("wrapper").resolve("dists"))
        } catch (_: Exception) {
            // Best effort; a real directory (junction fallback) is removed with the rest below.
        }
        path.toFile().deleteRecursively()
    }

    companion object {
        fun create(): TestGradleUserHome {
            val baseDir = Files.createTempDirectory(SharedTestInfrastructure.sharedWorkingDir, "test-gradle-user-home-")
            linkDistsDir(baseDir, SharedTestInfrastructure.sharedDistsDir)
            return TestGradleUserHome(baseDir)
        }

        /**
         * Makes `<home>/wrapper/dists` resolve to [sharedDistsDir]: a directory junction on Windows
         * (`mklink /J`, no admin required) and a symbolic link elsewhere. Any failure falls back to
         * a plain directory, in which case the home provisions its own distribution copy (slower,
         * never incorrect).
         */
        private fun linkDistsDir(home: Path, sharedDistsDir: Path) {
            val wrapperDir = home.resolve("wrapper").createDirectories()
            val distsLink = wrapperDir.resolve("dists")
            try {
                if (OS.isWindows) {
                    val process = ProcessBuilder("cmd", "/c", "mklink", "/J", distsLink.toString(), sharedDistsDir.toString())
                        .redirectErrorStream(true)
                        .start()
                    process.inputStream.bufferedReader().readText()
                    val exit = process.waitFor()
                    if (exit != 0 || !Files.exists(distsLink)) {
                        Files.createDirectories(distsLink)
                    }
                } else {
                    try {
                        Files.createSymbolicLink(distsLink, sharedDistsDir)
                    } catch (_: Exception) {
                        Files.createDirectories(distsLink)
                    }
                }
            } catch (_: Exception) {
                try {
                    Files.createDirectories(distsLink)
                } catch (_: Exception) {
                    // Last resort: distribution provisioning inside the home falls back to a
                    // regular download.
                }
            }
        }
    }
}
