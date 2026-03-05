package dev.rnett.gradle.mcp

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

interface DistributionDownloaderService {
    suspend fun downloadDocs(version: String): Path
}

class DefaultDistributionDownloaderService(
    private val httpClient: HttpClient,
    private val environment: GradleMcpEnvironment,
    private val baseUrl: String = "https://services.gradle.org/distributions/"
) : DistributionDownloaderService {

    private val mutex = Mutex()

    override suspend fun downloadDocs(version: String): Path {
        val fileName = "gradle-$version-docs.zip"
        val destination = environment.cacheDir.resolve("gradle-docs").resolve(version).resolve(fileName)

        if (destination.exists()) {
            return destination
        }

        mutex.withLock {
            if (destination.exists()) {
                return destination
            }

            withContext(Dispatchers.IO) {
                Files.createDirectories(destination.parent)
                val partFile = destination.parent.resolve("$fileName.part")
                val url = "$baseUrl$fileName"

                try {
                    val response = httpClient.get(url)
                    if (response.status.value !in 200..299) {
                        throw RuntimeException("Failed to download docs from $url: ${response.status}")
                    }

                    val channel = response.bodyAsChannel()
                    partFile.toFile().outputStream().buffered().use { output ->
                        channel.toInputStream().copyTo(output)
                    }

                    Files.move(partFile, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (e: Exception) {
                    Files.deleteIfExists(partFile)
                    throw e
                }
            }
        }

        return destination
    }
}
