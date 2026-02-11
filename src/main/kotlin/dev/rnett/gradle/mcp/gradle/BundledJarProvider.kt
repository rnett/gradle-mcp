package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.hash
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

interface BundledJarProvider {
    fun extractAllJars(): List<Path>
    fun extractJar(resourceName: String): Path
}

open class DefaultBundledJarProvider(
    private val workingDir: Path = Path(System.getProperty("user.home"), ".gradle-mcp").absolute()
) : BundledJarProvider {

    private val jarsDir = workingDir.resolve("jars")

    private fun listJars(): List<String> {
        return BuildConfig.BUNDLED_JARS.split(",").filter { it.isNotBlank() }
    }

    /**
     * Extracts all bundled JARs from resources to the working directory.
     * Returns a list of absolute paths to the extracted JARs.
     */
    override fun extractAllJars(): List<Path> {
        return listJars().map { extractJar(it) }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultBundledJarProvider::class.java)
    }

    /**
     * Extracts a bundled JAR from resources to the working directory.
     * Returns the absolute path to the extracted JAR.
     */
    override fun extractJar(resourceName: String): Path {
        jarsDir.createDirectories()

        val inputStream = this::class.java.classLoader.getResourceAsStream(resourceName)
            ?: throw IllegalArgumentException("Resource not found: $resourceName")

        val bytes = inputStream.readAllBytes()
        val hash = bytes.hash()
        val extension = resourceName.substringAfterLast(".", "jar")
        val baseName = resourceName.substringBeforeLast(".").replace("/", "-").replace("\\", "-")
        val targetFileName = "$baseName-$hash.$extension"
        val targetPath = jarsDir.resolve(targetFileName)

        if (!targetPath.exists()) {
            val tempPath = targetPath.resolveSibling("${targetPath.fileName}.tmp.${java.util.UUID.randomUUID()}")
            try {
                tempPath.writeBytes(bytes)
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                LOGGER.info("Extracted bundled jar $resourceName to $targetPath")
            } catch (e: Exception) {
                if (targetPath.exists()) {
                    LOGGER.debug("Bundled jar {} already exists at {} (written by another process/thread)", resourceName, targetPath)
                } else {
                    LOGGER.warn("Failed to extract bundled jar $resourceName", e)
                    throw e
                }
            } finally {
                Files.deleteIfExists(tempPath)
            }
        } else {
            LOGGER.debug("Bundled jar {} already exists at {}", resourceName, targetPath)
        }

        return targetPath.absolute()
    }
}
