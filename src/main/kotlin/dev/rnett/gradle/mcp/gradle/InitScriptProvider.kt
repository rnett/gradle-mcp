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

interface InitScriptProvider {
    fun extractInitScripts(scriptNames: List<String>): List<Path>
}

class DefaultInitScriptProvider(
    private val workingDir: Path = Path(System.getProperty("user.home"), ".gradle-mcp").absolute()
) : InitScriptProvider {


    private val initScriptsDir = workingDir.resolve("init-scripts")

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultInitScriptProvider::class.java)
        private const val RESOURCE_PATH = "init-scripts"
    }

    private fun listResources(): List<String> {
        return BuildConfig.INIT_SCRIPTS.split(",").filter { it.isNotBlank() }
    }

    /**
     * Extracts requested .init.gradle.kts files from resources to the working directory.
     * Returns a list of absolute paths to the extracted scripts.
     */
    override fun extractInitScripts(scriptNames: List<String>): List<Path> {
        val scripts = mutableListOf<Path>()
        initScriptsDir.createDirectories()

        val resourceNames = listResources()

        return buildList {

            scriptNames.forEach { requestedName ->
                val resourceName = resourceNames.find { it == "$requestedName.init.gradle.kts" }
                if (resourceName == null) {
                    LOGGER.warn("Init script $requestedName not found in resources")
                    return@forEach
                }

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
}
