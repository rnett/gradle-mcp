package dev.rnett.gradle.mcp

import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.walk

@Serializable
data class DocsSearchResult(val title: String, val path: String, val snippet: String, val tag: String)

@Serializable
data class DocsSectionSummary(val tag: String, val displayName: String, val count: Int)

sealed class DocsPageContent {
    data class Markdown(val content: String) : DocsPageContent()
    data class Image(val base64: String, val mimeType: String) : DocsPageContent()
}

@Serializable
data class DocsSearchResponse(
    val results: List<DocsSearchResult>,
    val interpretedQuery: String? = null,
    val error: String? = null
)

interface GradleDocsService : AutoCloseable {
    suspend fun getDocsPageContent(path: String, version: String? = null): DocsPageContent
    suspend fun getReleaseNotes(version: String? = null): String
    suspend fun searchDocs(query: String, version: String? = null): DocsSearchResponse
    suspend fun summarizeSections(version: String? = null): List<DocsSectionSummary>
}

class DefaultGradleDocsService(
    private val httpClient: HttpClient,
    private val indexer: GradleDocsIndexService,
    private val environment: GradleMcpEnvironment,
    private val versionService: GradleVersionService
) : GradleDocsService {

    private suspend fun resolveVersion(version: String?): String {
        return versionService.resolveVersion(version)
    }

    private suspend fun ensurePrepared(version: String): String {
        val resolvedVersion = resolveVersion(version)

        val versionDir = environment.cacheDir.resolve("reading_gradle_docs").resolve(resolvedVersion)

        withContext(Dispatchers.IO) {
            Files.createDirectories(versionDir)
            val lockFile = versionDir.resolve(".lock")

            FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                channel.lock().use {
                    indexer.ensureIndexed(resolvedVersion)
                }
            }
        }
        return resolvedVersion
    }

    override suspend fun getDocsPageContent(path: String, version: String?): DocsPageContent {
        val resolvedVersion = ensurePrepared(version ?: "current")
        val convertedDir = environment.cacheDir.resolve("reading_gradle_docs").resolve(resolvedVersion).resolve("converted")

        // Normalize path: if it was provided with .html, change to .md (for HTML-converted files)
        // But for images, we keep the extension
        val normalizedPath = if (isHtmlPath(path)) path.replace(".html", ".md") else path
        val targetPath = convertedDir.resolve(normalizedPath)

        if (!targetPath.exists()) {
            throw RuntimeException("Docs page not found: $path (resolved to $targetPath)")
        }

        if (targetPath.isDirectory()) {
            val entries = targetPath.listDirectoryEntries().sortedBy { it.name }
            val content = buildString {
                appendLine("# Directory: ${if (path == "." || path == "") "/" else path}")
                appendLine()
                entries.forEach { entry ->
                    val name = entry.name
                    if (name.startsWith(".")) return@forEach
                    val displayName = if (entry.isDirectory()) "$name/" else name
                    appendLine("- $displayName")
                }
            }
            return DocsPageContent.Markdown(content)
        }

        return if (isImage(targetPath)) {
            val bytes = targetPath.readBytes()
            val base64 = Base64.getEncoder().encodeToString(bytes)
            DocsPageContent.Image(base64, getMimeType(targetPath))
        } else {
            DocsPageContent.Markdown(targetPath.readText())
        }
    }

    private fun isHtmlPath(path: String): Boolean {
        return path.endsWith(".html") || path.endsWith(".md")
    }

    private fun isImage(path: java.nio.file.Path): Boolean {
        val ext = path.extension.lowercase()
        return ext in setOf("png", "jpg", "jpeg", "gif", "svg", "ico")
    }

    private fun getMimeType(path: java.nio.file.Path): String {
        return when (path.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            else -> "application/octet-stream"
        }
    }

    override suspend fun getReleaseNotes(version: String?): String {
        val content = getDocsPageContent("release-notes.md", version)
        return (content as? DocsPageContent.Markdown)?.content ?: throw RuntimeException("Release notes not found as markdown")
    }

    override suspend fun searchDocs(query: String, version: String?): DocsSearchResponse {
        val resolvedVersion = ensurePrepared(version ?: "current")
        return indexer.search(query, resolvedVersion)
    }

    override suspend fun summarizeSections(version: String?): List<DocsSectionSummary> {
        val resolvedVersion = ensurePrepared(version ?: "current")
        val convertedDir = environment.cacheDir.resolve("reading_gradle_docs").resolve(resolvedVersion).resolve("converted")

        if (!convertedDir.exists()) return emptyList()

        val summaries = mutableListOf<DocsSectionSummary>()

        // Root files (release notes)
        val rootFiles = convertedDir.listDirectoryEntries("*.md")
        if (rootFiles.any { it.name == "release-notes.md" }) {
            summaries.add(DocsSectionSummary("release-notes", "Release Notes", 1))
        }

        var bestPracticesCount = 0

        // Section directories
        convertedDir.listDirectoryEntries().filter { it.isDirectory() }.forEach { dir ->
            val tag = if (dir.name == "kotlin-dsl") "dsl" else dir.name
            val files = dir.walk().filter { it.isRegularFile() && it.extension == "md" }.toList()
            val count = files.size

            bestPracticesCount += files.count { it.toString().replace("\\", "/").contains("best_practices") }

            val existing = summaries.find { it.tag == tag }
            if (existing != null) {
                summaries.remove(existing)
                summaries.add(existing.copy(count = existing.count + count))
            } else {
                val displayName = when (tag) {
                    "userguide" -> "User Guide"
                    "dsl" -> "DSL Reference"
                    "javadoc" -> "Java API Reference"
                    "samples" -> "Samples"
                    else -> tag.replaceFirstChar { it.uppercase() }
                }
                summaries.add(DocsSectionSummary(tag, displayName, count))
            }
        }

        if (bestPracticesCount > 0) {
            summaries.add(DocsSectionSummary("best-practices", "Best Practices", bestPracticesCount))
        }

        return summaries.sortedBy { it.displayName }
    }

    override fun close() {
        indexer.close()
    }
}
