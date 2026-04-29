package dev.rnett.gradle.mcp.dependencies.search

import com.github.luben.zstd.ZstdInputStream
import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.utils.FileLockManager
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.outputStream

@Serializable
data class ParserManifest(
    val version: String,
    val platforms: Map<String, PlatformBundle>,
    val languages: Map<String, LanguageInfo>,
    val groups: Map<String, List<String>>
)

@Serializable
data class PlatformBundle(
    val url: String,
    val sha256: String
)

@Serializable
data class LanguageInfo(
    val group: String,
    val sha256: String? = null
)

class ParserDownloader(
    private val httpClient: HttpClient,
    private val version: String = BuildConfig.TREE_SITTER_LANGUAGE_PACK_VERSION,
    private val cacheDirOverride: Path? = null
) {
    init {
        require(version.matches(Regex("""^v?\d+\.\d+\.\d+.*$"""))) { "Invalid version format." }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val logger = LoggerFactory.getLogger(ParserDownloader::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manifestDeferred: Deferred<ParserManifest> = scope.async {
        fetchManifest()
    }

    private val cacheDir: Path by lazy {
        cacheDirOverride ?: Path.of(System.getProperty("user.home"), ".gradle-mcp", "cache", "tree-sitter-language-pack", "v$version", "libs")
    }

    suspend fun ensureLanguage(name: String): Path = withContext(Dispatchers.IO) {
        require(name.matches(Regex("""^[a-zA-Z0-9_+\-]+$"""))) { "Invalid language name format." }
        val manifest = manifestDeferred.await()
        val languageInfo = manifest.languages[name] ?: error("Language '$name' not available for download.")
        val expectedLibPath = libPath(name)

        if (expectedLibPath.exists()) {
            val hash = languageInfo.sha256
            if (hash.isNullOrBlank()) {
                logger.warn("No SHA-256 hash available in manifest for language '$name'. Returning unverified library (SECURITY RISK).")
                return@withContext expectedLibPath
            } else if (verifyHash(expectedLibPath, hash)) {
                return@withContext expectedLibPath
            }
        }

        cacheDir.createDirectories()
        val lockFile = cacheDir.resolve("$name.lock")
        FileLockManager.withLock(lockFile) {
            if (expectedLibPath.exists()) {
                val hash = languageInfo.sha256
                if (hash.isNullOrBlank()) {
                    logger.warn("No SHA-256 hash available in manifest for language '$name'. Returning unverified library (SECURITY RISK).")
                    return@withLock expectedLibPath
                } else if (verifyHash(expectedLibPath, hash)) {
                    return@withLock expectedLibPath
                }
            }

            val platformKey = TreeSitterUtils.platformKey()
            val bundleLockFile = cacheDir.resolve("$platformKey.bundle.lock")
            FileLockManager.withLock(bundleLockFile) {
                // Check again inside bundle lock
                if (expectedLibPath.exists()) {
                    val hash = languageInfo.sha256
                    if (hash.isNullOrBlank()) {
                        logger.warn("No SHA-256 hash available in manifest for language '$name'. Returning unverified library (SECURITY RISK).")
                        return@withLock expectedLibPath
                    } else if (verifyHash(expectedLibPath, hash)) {
                        return@withLock expectedLibPath
                    }
                }

                val bundle = manifest.platforms[platformKey]
                    ?: error("No pre-built parsers available for platform '$platformKey'.")

                logger.info("Downloading tree-sitter parser bundle for $platformKey...")
                val tempFile = downloadToTemp(bundle.url, bundle.sha256)
                try {
                    val group = languageInfo.group
                    val languagesInGroup = manifest.languages.filter { it.value.group == group }.keys
                    logger.info("Extracting languages in group '$group' from bundle...")
                    extractLanguages(tempFile, languagesInGroup)
                } finally {
                    tempFile.deleteIfExists()
                }
            }

            if (!expectedLibPath.exists()) {
                logger.debug("Extraction succeeded but expected library $expectedLibPath was not found.")
                error("Extraction succeeded but expected library for '$name' was not found.")
            }

            val hash = languageInfo.sha256
            if (!hash.isNullOrBlank() && !verifyHash(expectedLibPath, hash)) {
                logger.debug("Extraction succeeded but library $expectedLibPath has incorrect hash.")
                error("Extraction succeeded but library for '$name' has incorrect hash.")
            }

            expectedLibPath
        }
    }

    private suspend fun fetchManifest(): ParserManifest {
        val url = "https://github.com/kreuzberg-dev/tree-sitter-language-pack/releases/download/v$version/parsers.json"
        val response = httpClient.get(url)
        if (response.status.value !in 200..299) {
            error("Failed to fetch manifest from $url: ${response.status}")
        }
        return json.decodeFromString<ParserManifest>(response.bodyAsText())
    }

    private suspend fun downloadToTemp(url: String, expectedHash: String): Path = withContext(Dispatchers.IO) {
        val tempFile = createTempFile(cacheDir, "ts-bundle", ".tar.zst")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            httpClient.prepareGet(url).execute { response ->
                if (response.status.value !in 200..299) {
                    error("Failed to download bundle from $url: ${response.status}")
                }
                val channel = response.bodyAsChannel()
                java.security.DigestOutputStream(tempFile.outputStream(), digest).use { out ->
                    channel.toInputStream().copyTo(out)
                }
            }

            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (actualHash != expectedHash) {
                error("Checksum mismatch for $url. Expected $expectedHash, got $actualHash")
            }

            tempFile
        } catch (e: Exception) {
            tempFile.deleteIfExists()
            throw e
        }
    }

    private fun extractLanguages(archiveFile: Path, requestedNames: Set<String>) {
        cacheDir.createDirectories()

        val expectedFiles = requestedNames.associateBy { libPath(it).name }
        val extractedFiles = mutableSetOf<String>()

        archiveFile.inputStream().use { fileIn ->
            ZstdInputStream(fileIn).use { zstdIn ->
                TarArchiveInputStream(zstdIn).use { tarIn ->
                    while (true) {
                        val entry = tarIn.nextEntry ?: break
                        if (entry.isSymbolicLink || entry.isLink) continue
                        val entryName = Path.of(entry.name).name

                        if (entryName in expectedFiles) {
                            val dest = cacheDir.resolve(entryName)
                            val tempDest = createTempFile(cacheDir, "extract", entryName)
                            tempDest.outputStream().use { out ->
                                tarIn.copyTo(out)
                            }
                            tempDest.moveTo(dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                            extractedFiles.add(expectedFiles[entryName]!!)
                        }
                    }
                }
            }
        }

        val missing = requestedNames - extractedFiles
        if (missing.isNotEmpty()) {
            error("Failed to extract requested languages: ${missing.joinToString(", ")}")
        }
    }

    private fun libPath(name: String): Path {
        return TreeSitterUtils.libPath(cacheDir, name)
    }

    private fun verifyHash(path: Path, expectedHash: String): Boolean {
        if (!path.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        path.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        return actualHash.equals(expectedHash, ignoreCase = true)
    }
}
