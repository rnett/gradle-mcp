package dev.rnett.gradle.mcp.dependencies.gradle.docs

import dev.rnett.gradle.mcp.DocsKind
import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.ArchiveExtractor
import dev.rnett.gradle.mcp.dependencies.gradle.DistributionDownloaderService
import dev.rnett.gradle.mcp.withMessage
import dev.rnett.gradle.mcp.withPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.io.path.Path

interface ContentExtractorService {
    fun convertedDirs(version: String, kind: DocsKind): List<Path>

    context(progress: ProgressReporter)
    suspend fun extractEntries(version: String, onFileExtracted: suspend (String, ByteArray) -> Unit)
}

class DefaultContentExtractorService(
    private val downloader: DistributionDownloaderService,
    private val htmlConverter: HtmlConverter,
    private val environment: GradleMcpEnvironment
) : ContentExtractorService {

    override fun convertedDirs(version: String, kind: DocsKind): List<Path> {
        val base = environment.cacheDir.resolve("reading_gradle_docs").resolve(version).resolve("converted")
        return when (kind) {
            DocsKind.DSL -> listOf(base.resolve("dsl"), base.resolve("kotlin-dsl"))
            DocsKind.RELEASE_NOTES -> listOf(base)
            else -> listOf(base.resolve(kind.dirName))
        }
    }

    context(progress: ProgressReporter)
    override suspend fun extractEntries(version: String, onFileExtracted: suspend (String, ByteArray) -> Unit) {
        val zipPath = downloader.downloadDocs(version)

        withContext(Dispatchers.IO) {
            val extractionProgress = progress.withPhase("PROCESSING")
            val zipFile = ZipFile(zipPath.toFile())
            val totalEntries = zipFile.size().toDouble()
            var processedEntries = 0.0

            zipFile.use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    processedEntries++
                    if (processedEntries % 50 == 0.0 || processedEntries == totalEntries) {
                        extractionProgress(processedEntries, totalEntries, "Extracting documentation")
                    }

                    if (entry.isDirectory) continue

                    val fullPath = entry.name
                    val docsIndex = fullPath.indexOf("docs/")
                    if (docsIndex == -1) continue
                    val path = fullPath.substring(docsIndex)

                    // Skip ignored files (web assets like CSS/JS) and samples/zips (handled in second pass)
                    if (isIgnored(path) || path.startsWith("docs/samples/zips/")) continue

                    val kind = detectKind(path) ?: continue
                    val relativePath = getRelativePath(path, kind)

                    val targetPathString = if (kind == DocsKind.RELEASE_NOTES) {
                        relativePath
                    } else {
                        "${kind.dirName}/$relativePath"
                    }

                    val bytes = zip.getInputStream(entry).use { it.readAllBytes() }
                    onFileExtracted(targetPathString, bytes)
                }
            }

            // Second pass: nested sample ZIPs
            val sampleZips = ZipFile(zipPath.toFile()).use { zip ->
                zip.entries().asSequence().filter { it.name.contains("docs/samples/zips/") && it.name.endsWith(".zip") }.map { it.name }.toList()
            }
            if (sampleZips.isNotEmpty()) {
                val totalSamples = sampleZips.size.toDouble()
                var processedSamples = 0.0
                ZipFile(zipPath.toFile()).use { zip ->
                    for (entryName in sampleZips) {
                        processedSamples++
                        extractionProgress(processedSamples, totalSamples, "Extracting sample source: ${entryName.substringAfterLast('/')}")
                        val entry = zip.getEntry(entryName)
                        with<ProgressReporter, Unit>(extractionProgress.withMessage { "Extracting sample source: ${entryName.substringAfterLast('/')} - $it" }) {
                            processSampleZip(zip, entry, onFileExtracted)
                        }
                    }
                }
            }
        }
    }

    private fun detectKind(path: String): DocsKind? {
        return when {
            path.startsWith("docs/userguide/") -> DocsKind.USERGUIDE
            path.startsWith("docs/dsl/") -> DocsKind.DSL
            path.startsWith("docs/kotlin-dsl/") -> DocsKind.KOTLIN_DSL
            path.startsWith("docs/javadoc/") -> DocsKind.JAVADOC
            path.startsWith("docs/samples/") -> DocsKind.SAMPLES
            path == "docs/release-notes.html" -> DocsKind.RELEASE_NOTES
            else -> null
        }
    }

    private fun getRelativePath(path: String, kind: DocsKind): String {
        return when (kind) {
            DocsKind.RELEASE_NOTES -> "release-notes.html"
            DocsKind.SAMPLES -> {
                val sub = path.removePrefix("docs/samples/")
                if (!sub.contains("/") && sub.endsWith(".html")) {
                    // docs/samples/{name}.html -> samples/{name}/README.md
                    val name = sub.removeSuffix(".html")
                    "$name/README.html"
                } else {
                    sub
                }
            }

            else -> path.removePrefix("docs/${kind.dirName}/")
        }
    }

    private fun isIgnored(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in setOf("js", "css", "map")
    }

    context(progress: ProgressReporter)
    private suspend fun processSampleZip(outerZip: ZipFile, entry: ZipEntry, onFileExtracted: suspend (String, ByteArray) -> Unit) {
        val fullFileName = entry.name.substringAfterLast('/')
        val fileName = fullFileName.removeSuffix(".zip")

        val variant = when {
            fileName.endsWith("-groovy-dsl") -> "groovy-dsl"
            fileName.endsWith("-kotlin-dsl") -> "kotlin-dsl"
            else -> "common"
        }
        val baseName = fileName.removeSuffix("-$variant")

        ZipInputStream(outerZip.getInputStream(entry).buffered()).use { zis ->
            ArchiveExtractor.extractInto(Path("dummy"), zis, skipSingleFirstDir = false, writeFiles = false) { path, bytes ->
                onFileExtracted("samples/$baseName/$variant/$path", bytes)
            }
        }
    }

}
