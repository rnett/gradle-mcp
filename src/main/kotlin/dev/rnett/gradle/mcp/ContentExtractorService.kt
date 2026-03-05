package dev.rnett.gradle.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

interface ContentExtractorService {
    fun convertedDirs(version: String, kind: DocsKind): List<Path>
    suspend fun ensureProcessed(version: String)
}

class DefaultContentExtractorService(
    private val downloader: DistributionDownloaderService,
    private val htmlConverter: HtmlConverter,
    private val environment: GradleMcpEnvironment
) : ContentExtractorService {

    override fun convertedDirs(version: String, kind: DocsKind): List<Path> {
        val base = environment.cacheDir.resolve("gradle-docs").resolve(version).resolve("converted")
        return when (kind) {
            DocsKind.DSL -> listOf(base.resolve("dsl"), base.resolve("kotlin-dsl"))
            DocsKind.RELEASE_NOTES -> listOf(base)
            else -> listOf(base.resolve(kind.dirName))
        }
    }

    override suspend fun ensureProcessed(version: String) {
        val versionDir = environment.cacheDir.resolve("gradle-docs").resolve(version)
        val convertedDir = versionDir.resolve("converted")
        val doneMarker = convertedDir.resolve(".done")

        if (doneMarker.exists()) {
            return
        }

        val zipPath = downloader.downloadDocs(version)

        withContext(Dispatchers.IO) {
            Files.createDirectories(convertedDir)

            ZipFile(zipPath.toFile()).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue

                    val path = entry.name
                    if (!path.startsWith("docs/")) continue

                    // Skip ignored files (web assets like CSS/JS) and samples/zips (handled in second pass)
                    if (isIgnored(path) || path.startsWith("docs/samples/zips/")) continue

                    val kind = detectKind(path) ?: continue
                    val relativePath = getRelativePath(path, kind)

                    // Zip-slip guard
                    val targetPath = if (kind == DocsKind.RELEASE_NOTES) {
                        convertedDir.resolve(relativePath)
                    } else {
                        convertedDir.resolve(kind.dirName).resolve(relativePath)
                    }
                    if (!targetPath.normalize().startsWith(convertedDir.normalize())) {
                        throw RuntimeException("Zip slip detected in entry: ${entry.name}")
                    }

                    Files.createDirectories(targetPath.parent)

                    if (path.endsWith(".html")) {
                        val html = zip.getInputStream(entry).use { it.bufferedReader().readText() }
                        val markdown = htmlConverter.convert(html, kind)
                        val mdPath = targetPath.parent.resolve(targetPath.fileName.toString().replace(".html", ".md"))
                        mdPath.writeText(markdown)
                    } else {
                        zip.getInputStream(entry).use { input ->
                            targetPath.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }

            // Second pass: nested sample ZIPs
            ZipFile(zipPath.toFile()).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("docs/samples/zips/") && entry.name.endsWith(".zip")) {
                        processSampleZip(zip, entry, convertedDir.resolve("samples"))
                    }
                }
            }

            doneMarker.writeText(System.currentTimeMillis().toString())
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

    private fun processSampleZip(outerZip: ZipFile, entry: java.util.zip.ZipEntry, samplesDest: Path) {
        val fullFileName = entry.name.substringAfterLast('/')
        val fileName = fullFileName.removeSuffix(".zip")

        val variant = when {
            fileName.endsWith("-groovy-dsl") -> "groovy-dsl"
            fileName.endsWith("-kotlin-dsl") -> "kotlin-dsl"
            else -> "common"
        }
        val baseName = fileName.removeSuffix("-$variant")

        val destDir = samplesDest.resolve(baseName).resolve(variant)
        Files.createDirectories(destDir)

        ZipInputStream(outerZip.getInputStream(entry).buffered()).use { zis ->
            var nestedEntry = zis.getNextEntry()
            while (nestedEntry != null) {
                if (!nestedEntry.isDirectory) {
                    val target = destDir.resolve(nestedEntry.name)

                    // Zip-slip guard
                    if (!target.normalize().startsWith(destDir.normalize())) {
                        throw RuntimeException("Zip slip detected in nested entry: ${nestedEntry.name}")
                    }

                    Files.createDirectories(target.parent)
                    target.outputStream().buffered().use { output ->
                        zis.copyTo(output)
                    }
                }
                nestedEntry = zis.getNextEntry()
            }
        }
    }
}
