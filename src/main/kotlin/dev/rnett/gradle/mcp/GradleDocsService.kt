package dev.rnett.gradle.mcp

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
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

interface GradleDocsService : AutoCloseable {
    suspend fun getDocsPageContent(path: String, version: String? = null): DocsPageContent
    suspend fun getReleaseNotes(version: String? = null): String
    suspend fun searchDocs(query: String, version: String? = null): List<DocsSearchResult>
    suspend fun summarizeSections(version: String? = null): List<DocsSectionSummary>
}

class DefaultGradleDocsService(
    private val httpClient: HttpClient,
    private val indexer: GradleDocsIndexService,
    private val environment: GradleMcpEnvironment
) : GradleDocsService {

    private suspend fun resolveVersion(version: String?): String {
        if (version != null && version != "current") return version

        val rootUrl = "https://docs.gradle.org/current/userguide/userguide.html"
        val response = httpClient.get(rootUrl) {
            timeout {
                requestTimeoutMillis = 5000
            }
        }
        val html = response.bodyAsText()
        val doc = Jsoup.parse(html)

        val canonical = doc.selectFirst("link[rel=canonical]")?.attr("href")
        if (canonical != null) {
            val match = Regex("docs\\.gradle\\.org/([^/]+)/").find(canonical)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        val versionText = doc.select(".footer").text()
        val versionMatch = Regex("Version (\\d+\\.\\d+(\\.\\d+)?)").find(versionText)
        if (versionMatch != null) {
            return versionMatch.groupValues[1]
        }

        return "current"
    }

    private suspend fun ensurePrepared(version: String): String {
        val resolvedVersion = resolveVersion(version)
        if (resolvedVersion == "current") return "current"

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

    override suspend fun searchDocs(query: String, version: String?): List<DocsSearchResult> {
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

        // Section directories
        convertedDir.listDirectoryEntries().filter { it.isDirectory() }.forEach { dir ->
            val tag = if (dir.name == "kotlin-dsl") "dsl" else dir.name
            val count = dir.walk().count { it.isRegularFile() && it.extension == "md" }

            val existing = summaries.find { it.tag == tag }
            if (existing != null) {
                summaries.remove(existing)
                summaries.add(existing.copy(count = existing.count + count.toInt()))
            } else {
                val displayName = when (tag) {
                    "userguide" -> "User Guide"
                    "dsl" -> "DSL Reference"
                    "javadoc" -> "Java API Reference"
                    "samples" -> "Samples"
                    else -> tag.replaceFirstChar { it.uppercase() }
                }
                summaries.add(DocsSectionSummary(tag, displayName, count.toInt()))
            }
        }

        return summaries.sortedBy { it.displayName }
    }

    override fun close() {
        indexer.close()
    }
}
