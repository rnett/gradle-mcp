package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.BuildConfig
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

class InitScriptProvider(
    private val workingDir: Path = Path(System.getProperty("user.home"), ".gradle-mcp").absolute()
) {


    private val initScriptsDir = workingDir.resolve("init-scripts")

    companion object {
        private val LOGGER = LoggerFactory.getLogger(InitScriptProvider::class.java)
        private const val RESOURCE_PATH = "init-scripts"
    }

    private fun listResources(): List<String> {
        return BuildConfig.INIT_SCRIPTS.split(",").filter { it.isNotBlank() }
    }

    /**
     * Extracts all .init.gradle.kts files from resources to the working directory.
     * Returns a list of absolute paths to the extracted scripts.
     */
    fun extractInitScripts(): List<Path> {
        val scripts = mutableListOf<Path>()
        initScriptsDir.createDirectories()

        val resourceNames = listResources()

        return buildList {

            resourceNames.forEach { resourceName ->
                val resourcePath = "$RESOURCE_PATH/$resourceName"
                val inputStream = this::class.java.classLoader.getResourceAsStream(resourcePath)
                if (inputStream == null) {
                    LOGGER.warn("Resource not found: $resourcePath")
                    return@forEach
                }

                val bytes = inputStream.readAllBytes()
                val hash = bytes.hash()
                val baseName = resourceName.removeSuffix(".init.gradle.kts")
                val targetFileName = "$baseName-$hash.init.gradle.kts"
                val targetPath = initScriptsDir.resolve(targetFileName)

                if (!targetPath.exists()) {
                    val tempPath = targetPath.resolveSibling("${targetPath.fileName}.tmp.${java.util.UUID.randomUUID()}")
                    try {
                        tempPath.writeBytes(bytes)
                        Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                        LOGGER.info("Extracted init script $resourceName to $targetPath")
                    } catch (e: Exception) {
                        if (targetPath.exists()) {
                            LOGGER.debug("Init script {} already exists at {} (written by another process/thread)", resourceName, targetPath)
                        } else {
                            LOGGER.warn("Failed to extract init script $resourceName", e)
                        }
                    } finally {
                        Files.deleteIfExists(tempPath)
                    }
                } else {
                    LOGGER.debug("Init script {} already exists at {}", resourceName, targetPath)
                }

                add(targetPath.absolute())
            }
        }
    }

    private fun ByteArray.hash(): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(this)
        return digest.fold("") { str, it -> str + "%02x".format(it) }.take(8)
    }
}
