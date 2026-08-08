package dev.rnett.gradle.mcp.dependencies.gradle.docs

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.ProgressReporter
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
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

internal fun pinDocVersionLinks(content: String, version: String): String =
    content.replace("https://docs.gradle.org/current/", "https://docs.gradle.org/$version/")

interface GradleDocsService : AutoCloseable {
    context(progress: ProgressReporter)
    suspend fun getDocsPageContent(path: String, version: String? = null): DocsPageContent

    context(progress: ProgressReporter)
    suspend fun getReleaseNotes(version: String? = null): String

    context(progress: ProgressReporter)
    suspend fun searchDocs(query: String, version: String? = null): DocsSearchResponse

    context(progress: ProgressReporter)
    suspend fun summarizeSections(version: String? = null): List<DocsSectionSummary>
}

private const val MAX_LISTED_FRAGMENTS = 50

class DefaultGradleDocsService(
    private val httpClient: HttpClient,
    private val indexer: GradleDocsIndexService,
    private val environment: GradleMcpEnvironment,
    private val versionService: GradleVersionService
) : GradleDocsService {

    private suspend fun resolveVersion(version: String?): String {
        return versionService.resolveVersion(version)
    }

    context(progress: ProgressReporter)
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

    context(progress: ProgressReporter)
    override suspend fun getDocsPageContent(path: String, version: String?): DocsPageContent {
        val resolvedVersion = ensurePrepared(version ?: "current")
        val convertedDir = environment.cacheDir.resolve("reading_gradle_docs").resolve(resolvedVersion).resolve("converted")
        val parsedPath = parsePagePath(path)

        // Normalize the page path after removing URI-only query and fragment components.
        val normalizedPath = if (isHtmlPath(parsedPath.basePath)) {
            parsedPath.basePath.replace(".html", ".md")
        } else {
            parsedPath.basePath
        }
        val targetPath = convertedDir.resolve(normalizedPath)

        if (!targetPath.exists()) {
            throw RuntimeException("Docs page not found: $path (resolved to $targetPath)")
        }

        if (targetPath.isDirectory()) {
            val entries = targetPath.listDirectoryEntries().sortedBy { it.name }
            val content = buildString {
                appendLine("# Directory: ${if (parsedPath.basePath == "." || parsedPath.basePath == "") "/" else parsedPath.basePath}")
                appendLine()
                entries.forEach { entry ->
                    val name = entry.name
                    if (name.startsWith(".")) return@forEach
                    val displayName = if (entry.isDirectory()) "$name/" else name
                    appendLine("- $displayName")
                }
            }
            return DocsPageContent.Markdown(pinDocVersionLinks(content + parsedPath.queryNote(), resolvedVersion))
        }

        if (isImage(targetPath)) {
            val bytes = targetPath.readBytes()
            val base64 = Base64.getEncoder().encodeToString(bytes)
            return DocsPageContent.Image(base64, getMimeType(targetPath))
        }

        val page = targetPath.readText()
        val content = parsedPath.fragment?.let { fragment ->
            extractSection(page, fragment, path)
        } ?: page
        return DocsPageContent.Markdown(pinDocVersionLinks(content + parsedPath.queryNote(), resolvedVersion))
    }

    private data class ParsedPagePath(
        val basePath: String,
        val fragment: String?,
        val query: String?,
    ) {
        fun queryNote(): String = query?.let {
            "\n\n(query string \"$it\"; ignored for documentation page reads)\n"
        }.orEmpty()
    }

    private fun parsePagePath(path: String): ParsedPagePath {
        val fragmentIndex = path.indexOf('#')
        val queryIndex = path.indexOf('?')
        val baseEnd = listOf(fragmentIndex, queryIndex).filter { it >= 0 }.minOrNull() ?: path.length
        val fragment = if (fragmentIndex >= 0) {
            val end = if (queryIndex > fragmentIndex) queryIndex else path.length
            path.substring(fragmentIndex + 1, end)
        } else null
        val query = if (queryIndex >= 0) path.substring(queryIndex + 1) else null
        return ParsedPagePath(path.substring(0, baseEnd), fragment, query)
    }

    private fun extractSection(page: String, fragment: String, requestedPath: String): String {
        val headings = Regex("(?m)^(#{1,6})\\s+(.+?)(?:\\s+\\{#([^}\\r\\n]+)})?\\s*$")
            .findAll(page)
            .toList()
        val requestedSlug = slugify(fragment)
        val match = headings.firstOrNull { it.groupValues[3] == fragment }
            ?: headings.firstOrNull { slugify(it.groupValues[2]) == requestedSlug }
            ?: throw RuntimeException(buildFragmentListing(headings, fragment, requestedPath))
        val level = match.groupValues[1].length
        val end = headings.firstOrNull {
            it.range.first > match.range.first && it.groupValues[1].length <= level
        }?.range?.first ?: page.length
        val title = match.groupValues[2].trim()
        val section = page.substring(match.range.first, end).trim()
        return buildString {
            appendLine("# Section: $fragment ($title)")
            appendLine()
            appendLine(section)
        }
    }

    private fun buildFragmentListing(headings: List<MatchResult>, fragment: String, requestedPath: String): String {
        if (headings.isEmpty()) {
            return """
                Fragment "#$fragment" could not be resolved in page "$requestedPath".

                This page has no recognized heading fragments — only Markdown ATX headings ('#' through '######', optionally with an explicit '{#id}') are resolvable as fragments.

                Read the entire page with path="$requestedPath" (no fragment).
            """.trimIndent()
        }

        data class FragmentEntry(val level: Int, val fragment: String, val title: String)

        val entries = headings.map { heading ->
            val level = heading.groupValues[1].length
            val title = heading.groupValues[2].trim()
            val fragment = heading.groupValues[3].ifEmpty { slugify(title) }
            FragmentEntry(level, fragment, title)
        }.distinctBy { it.fragment }
        val minLevel = entries.minOf { it.level }
        val listed = entries.take(MAX_LISTED_FRAGMENTS)
        val lines = listed.joinToString("\n") { entry ->
            "${"  ".repeat(entry.level - minLevel)}- `#${entry.fragment}` — ${entry.title}"
        }
        val truncation = if (headings.size > MAX_LISTED_FRAGMENTS) {
            "\n... and ${headings.size - MAX_LISTED_FRAGMENTS} more fragments not shown (${headings.size} total on this page)."
        } else {
            ""
        }

        return buildString {
            appendLine("Fragment \"#$fragment\" could not be resolved in page \"$requestedPath\".")
            appendLine()
            appendLine("Available fragments on this page:")
            appendLine(lines)
            if (truncation.isNotEmpty()) appendLine(truncation.trimStart())
            appendLine()
            append("Retry with a fragment from the list above (e.g. path=\"$requestedPath#<fragment>\"), or read the entire page with path=\"$requestedPath\" (no fragment).")
        }
    }

    private fun slugify(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun isHtmlPath(path: String): Boolean {
        return path.endsWith(".html") || path.endsWith(".md")
    }


    private fun isImage(path: Path): Boolean {
        val ext = path.extension.lowercase()
        return ext in setOf("png", "jpg", "jpeg", "gif", "svg", "ico")
    }

    private fun getMimeType(path: Path): String {
        return when (path.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            else -> "application/octet-stream"
        }
    }

    context(progress: ProgressReporter)
    override suspend fun getReleaseNotes(version: String?): String {
        val content = getDocsPageContent("release-notes.md", version)
        return (content as? DocsPageContent.Markdown)?.content ?: throw RuntimeException("Release notes not found as markdown")
    }

    context(progress: ProgressReporter)
    override suspend fun searchDocs(query: String, version: String?): DocsSearchResponse {
        val resolvedVersion = ensurePrepared(version ?: "current")
        val response = indexer.search(query, resolvedVersion)
        return response.copy(
            results = response.results.map { it.copy(snippet = pinDocVersionLinks(it.snippet, resolvedVersion)) }
        )
    }

    context(progress: ProgressReporter)
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
        var upgradingCount = 0

        // Section directories
        convertedDir.listDirectoryEntries().filter { it.isDirectory() }.forEach { dir ->
            val tag = if (dir.name == "kotlin-dsl") "dsl" else dir.name
            val files = dir.walk().filter { it.isRegularFile() && it.extension == "md" }.toList()
            val count = files.size

            bestPracticesCount += files.count { it.toString().replace("\\", "/").contains("best_practices") }
            upgradingCount += files.count {
                val name = it.fileName.toString()
                name.contains("upgrading_version_") || name.contains("upgrading_major_version_")
            }

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

        if (upgradingCount > 0) {
            summaries.add(DocsSectionSummary("upgrading", "Upgrading Gradle", upgradingCount))
        }

        return summaries.sortedBy { it.displayName }
    }

    override fun close() {
        indexer.close()
    }
}
