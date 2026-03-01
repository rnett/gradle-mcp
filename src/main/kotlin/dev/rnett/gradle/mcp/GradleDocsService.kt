package dev.rnett.gradle.mcp

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class DocsPage(val title: String, val path: String)

@Serializable
data class DocsSearchResult(val title: String, val path: String, val snippet: String)

interface GradleDocsService {
    suspend fun getAllDocsPages(version: String? = null): List<DocsPage>
    suspend fun getDocsPageAsMarkdown(path: String, version: String? = null): String
    suspend fun getReleaseNotes(version: String? = null): String
    suspend fun searchDocs(query: String, isRegex: Boolean, version: String? = null): List<DocsSearchResult>
}

class DefaultGradleDocsService(
    private val httpClient: HttpClient,
    private val markdownService: MarkdownService,
    private val environment: GradleMcpEnvironment
) : GradleDocsService {

    private val json = Json { ignoreUnknownKeys = true }

    private fun baseUrl(version: String?) = "https://docs.gradle.org/${version ?: "current"}/userguide"

    private suspend fun resolveVersion(version: String?): String {
        if (version != null && version != "current") return version

        val rootUrl = "https://docs.gradle.org/current/userguide/userguide.html"
        val response = httpClient.get(rootUrl)
        val html = response.bodyAsText()
        val doc = Jsoup.parse(html)

        // Usually there is a version string in the page, e.g. in the title or a specific meta tag/element
        // Let's try to find it. In Gradle docs, it's often in a script or a specific element.
        // A common way is to look at the link to the current version or some text.
        // For now, let's look for "Gradle User Home" or similar that might have version, 
        // but actually, a better way is to look at the canonical link if present.
        val canonical = doc.selectFirst("link[rel=canonical]")?.attr("href")
        if (canonical != null) {
            val match = Regex("docs\\.gradle\\.org/([^/]+)/").find(canonical)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        // Fallback: look for version in the footer or some text
        val versionText = doc.select(".footer").text()
        val versionMatch = Regex("Version (\\d+\\.\\d+(\\.\\d+)?)").find(versionText)
        if (versionMatch != null) {
            return versionMatch.groupValues[1]
        }

        return "current" // If we can't resolve it, we can't cache 'current' properly as a specific version
    }

    private fun getCachePath(version: String, fileName: String): Path {
        val dir = environment.cacheDir.resolve("gradle-docs").resolve(version)
        dir.toFile().mkdirs()
        return dir.resolve(fileName)
    }

    override suspend fun getReleaseNotes(version: String?): String {
        val resolvedVersion = resolveVersion(version)
        val cacheFile = getCachePath(resolvedVersion, "release-notes.md")

        if (resolvedVersion != "current" && cacheFile.exists()) {
            return cacheFile.readText()
        }

        val url = "https://docs.gradle.org/${version ?: "current"}/release-notes.html"
        val markdown = markdownService.downloadAsMarkdown(url)

        if (resolvedVersion != "current") {
            cacheFile.writeText(markdown)
        }

        return markdown
    }

    override suspend fun getAllDocsPages(version: String?): List<DocsPage> {
        val resolvedVersion = resolveVersion(version)
        val cacheFile = getCachePath(resolvedVersion, "pages.json")

        if (resolvedVersion != "current" && cacheFile.exists()) {
            return json.decodeFromString(cacheFile.readText())
        }

        val url = "${baseUrl(version)}/userguide.html"
        val response = httpClient.get(url)
        val html = response.bodyAsText()
        val doc = Jsoup.parse(html)

        val nav = doc.selectFirst("nav.docs-navigation")
        val results = mutableListOf<DocsPage>()

        if (nav != null) {
            val children = nav.children()
            var currentHeader: String? = null
            for (child in children) {
                if (child.tagName() == "h3") {
                    currentHeader = child.text().trim()
                } else if (child.tagName() == "ul") {
                    val parents = if (currentHeader != null) listOf(currentHeader) else emptyList()
                    processUl(child, parents, results)
                }
            }
        } else {
            // Fallback to all links in the main content area if nav is not found
            val mainLinks = doc.select("main a[href]")
            mainLinks.forEach {
                val href = it.attr("href")
                val title = it.text().trim()
                if (isRelativeDocLink(href) && title.isNotEmpty()) {
                    results.add(DocsPage(title, href))
                }
            }
        }

        val distinctResults = results.distinctBy { it.path }
        if (resolvedVersion != "current") {
            cacheFile.writeText(json.encodeToString(distinctResults))
        }

        return distinctResults
    }

    private fun processUl(ul: org.jsoup.nodes.Element, parents: List<String>, results: MutableList<DocsPage>) {
        for (li in ul.select(":root > li")) {
            val link = li.selectFirst(":root > a")
            val nestedUl = li.selectFirst(":root > ul")

            val title = link?.text()?.trim()?.replace(Regex("^‣\\s*"), "")
                ?: li.selectFirst(":root > span")?.text()?.trim()
            val href = link?.attr("href")

            if (link != null && title != null && href != null && isRelativeDocLink(href)) {
                val cleanHref = href.removePrefix("../userguide/")
                if (!cleanHref.contains("..")) {
                    val fullTitle = (parents + title).joinToString(" > ")
                    results.add(DocsPage(fullTitle, cleanHref))
                }
            }

            if (nestedUl != null) {
                val nextParents = if (title != null) parents + title else parents
                processUl(nestedUl, nextParents, results)
            }
        }
    }

    private fun isRelativeDocLink(href: String): Boolean {
        return href.isNotEmpty() &&
                !href.startsWith("http://") &&
                !href.startsWith("https://") &&
                !href.startsWith("#") &&
                (href.endsWith(".html") || !href.contains("://"))
    }

    override suspend fun getDocsPageAsMarkdown(path: String, version: String?): String {
        require(!path.contains("..")) { "Path cannot contain '..'" }
        val resolvedVersion = resolveVersion(version)
        val safePath = path.replace("/", "_").replace("\\", "_").replace(":", "_")
        val cacheFile = getCachePath(resolvedVersion, "$safePath.md")

        if (resolvedVersion != "current" && cacheFile.exists()) {
            return cacheFile.readText()
        }

        val url = if (path.startsWith("http")) path else "${baseUrl(version)}/$path"
        val markdown = markdownService.downloadAsMarkdown(url)

        if (resolvedVersion != "current" && !path.startsWith("http")) {
            cacheFile.writeText(markdown)
        }

        return markdown
    }

    override suspend fun searchDocs(query: String, isRegex: Boolean, version: String?): List<DocsSearchResult> {
        val pages = getAllDocsPages(version)
        val regex = if (isRegex) {
            Regex(query, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        } else null

        val results = mutableListOf<DocsSearchResult>()
        for (page in pages) {
            val markdown = getDocsPageAsMarkdown(page.path, version)
            val lines = markdown.lines()

            for ((index, line) in lines.withIndex()) {
                val isMatch = if (regex != null) {
                    regex.containsMatchIn(line)
                } else {
                    line.contains(query, ignoreCase = true)
                }

                if (isMatch) {
                    val snippet = getSnippetFromLines(lines, index)
                    results.add(DocsSearchResult(page.title, page.path, snippet))
                }
            }
        }
        return results
    }

    private fun getSnippetFromLines(lines: List<String>, matchLineIndex: Int, linesAround: Int = 2): String {
        val startLine = (matchLineIndex - linesAround).coerceAtLeast(0)
        val endLine = (matchLineIndex + linesAround).coerceAtMost(lines.size - 1)

        return lines.subList(startLine, endLine + 1).joinToString("\n")
    }
}
