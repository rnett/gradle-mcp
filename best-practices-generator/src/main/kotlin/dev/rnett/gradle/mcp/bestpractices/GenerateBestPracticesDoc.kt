@file:JvmName("GenerateBestPracticesDoc")

package dev.rnett.gradle.mcp.bestpractices

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private const val DOCS_BASE_URL = "https://docs.gradle.org/current/"
private const val DISTRIBUTION_BASE_URL = "https://services.gradle.org/distributions/"

internal data class BestPracticesPage(
    val sourcePath: String,
    val title: String,
    val markdown: String,
)

internal data class BestPracticesSection(
    val title: String,
    val markdown: String,
    val tags: String,
    val sourcePage: String,
)

/** Returns true for HTML best-practices pages in the Gradle user guide. */
fun isBestPracticesPage(entryName: String): Boolean {
    val normalized = entryName.replace('\\', '/')
    val docsPath = normalized.substringAfter("docs/", missingDelimiterValue = "")
    return docsPath.startsWith("userguide/") &&
        docsPath.contains("best_practices") &&
        docsPath.endsWith(".html") &&
        docsPath.substringAfterLast('/') !in setOf("best_practices.html", "best_practices_index.html")
}

/** Extracts and cleans the main content area from a Gradle user-guide page. */
fun extractContent(html: String): Element? {
    val content = Jsoup.parse(html).selectFirst("main.main-content")?.clone() ?: return null
    content.select("script, style, link, meta, wbr, nav, aside, .sidebar, .navigation, .breadcrumbs, .edit-link").remove()
    return content
}

/** Converts cleaned HTML content to Markdown. */
fun convertToMarkdown(content: Element): String =
    FlexmarkHtmlConverter.builder().build().convert(content.html()).trim()

internal fun extractTitle(html: String, content: Element, sourcePath: String): String {
    val documentTitle = Jsoup.parse(html).title().trim()
    if (documentTitle.isNotEmpty()) return documentTitle

    val heading = content.selectFirst("h1")?.text()?.trim()
    if (!heading.isNullOrEmpty()) return heading

    return sourcePath.substringAfterLast('/').removeSuffix(".html").replace('_', ' ')
}

internal fun normalizeInternalLinks(markdown: String, sourcePath: String): String {
    val sourceDocPath = sourcePath.removePrefix("docs/")
    val linkPattern = Regex("(?<!\\!)\\]\\(([^)\\s]+)([^)]*)\\)")

    return linkPattern.replace(markdown) { match ->
        val rawTarget = match.groupValues[1]
        val target = normalizeDocTarget(rawTarget, sourceDocPath) ?: return@replace match.value
        val suffix = match.groupValues[2]
        "](${target.absoluteUrl})$suffix (Use `gradle_docs(path=\"${target.docPath}\")`.)"
    }
}

private data class NormalizedDocTarget(val absoluteUrl: String, val docPath: String)

private fun normalizeDocTarget(rawTarget: String, sourceDocPath: String): NormalizedDocTarget? {
    if (rawTarget.startsWith("#") || rawTarget.startsWith("mailto:") || rawTarget.startsWith("data:")) return null

    val resolved = runCatching {
        val rawUri = URI(rawTarget)
        if (rawUri.isAbsolute) rawUri else URI(DOCS_BASE_URL + sourceDocPath).resolve(rawUri)
    }.getOrNull() ?: return null

    val host = resolved.host ?: return null
    if (host != "docs.gradle.org") return null

    val path = resolved.path.removePrefix("/").removePrefix("current/")
    if (!path.endsWith(".html") && !path.contains("userguide/")) return null

    val query = resolved.rawQuery?.let { "?$it" }.orEmpty()
    val fragment = resolved.rawFragment?.let { "#$it" }.orEmpty()
    return NormalizedDocTarget(
        absoluteUrl = "https://docs.gradle.org/current/$path$query$fragment",
        docPath = "$path$query$fragment",
    )
}

private val setextHeadingPattern = Regex("(?m)^([^\r\n]+?)\\s*(?:\\{#[^}\\r\\n]+})?\\s*\\r?\\n(={4,}|-{4,})\\s*$")
private val headingAnchorPattern = Regex("(?m)^(#{1,6}[^\r\n]*?)\\s*\\{#[^}\\r\\n]+}\\s*$")
private val standaloneAnchorPattern = Regex("(?m)^[ \\t]*\\{#[^}\\r\\n]+}[ \\t]*$\\r?\\n?")
private val navigationBlockPattern = Regex(
    "(?ms)^[ \\t]*version[ \\t]+\\d+(?:\\.\\d+)*(?:[-+][^\\s]+)?[ \\t]*$\\r?\\n.*?(?=^#{1,6}[ \\t]+)",
)
private val versionBannerPattern = Regex("(?m)^[ \\t]*version[ \\t]+\\d+(?:\\.\\d+)*(?:[-+][^\\s]+)?[ \\t]*$\\r?\\n?")
private val headingUnderlinePattern = Regex("(?m)^[ \\t]*(?:={4,}|-{4,})[ \\t]*$\\r?\\n?")
private val internalAnchorLinkPattern = Regex("(?<!!)\\[([^]\\r\\n]+)]\\(#[^)\\r\\n]+\\)")
private val downloadBadgePattern = Regex(
    "(?m)^[ \\t]*\\[!\\[Download]\\(https://img\\.shields\\.io/[^)\\r\\n]*\\)](?:\\([^)\\r\\n]+\\))?[ \\t]*$\\r?\\n?",
)

internal fun cleanContent(markdown: String): String = markdown
    .replace(setextHeadingPattern) { match ->
        val marker = if (match.groupValues[2].startsWith('=')) "#" else "##"
        "$marker ${match.groupValues[1].trim()}"
    }
    .replace(headingAnchorPattern, "$1")
    .replace(standaloneAnchorPattern, "")
    .replace(navigationBlockPattern, "\n")
    .replace(versionBannerPattern, "")
    .replace(headingUnderlinePattern, "")
    .replace(downloadBadgePattern, "")
    .replace(internalAnchorLinkPattern, "$1")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

private const val SECTION_MARKER = "[[GRADLE_MCP_SECTION]]"
private val subsectionHeadingPattern = Regex("(?m)^## ")
private val anchoredSetextSubsectionPattern =
    Regex("(?m)^([^\\r\\n]+?)\\s*(\\{#[^}\\r\\n]+})\\s*\\r?\\n(-{4,})\\s*$")
private val markedSubsectionHeadingPattern = Regex("(?m)^## \\Q$SECTION_MARKER\\E ")
private val tagsSectionPattern = Regex("(?ms)^### Tags[ \\t]*\\r?\\n(.*?)(?=^#{1,6} |\\z)")
private val linkedTagPattern = Regex("\\[(`?#[A-Za-z0-9-]+`?)]\\([^)]+\\)")
private val headingPromotionPattern = Regex("(?m)^(#{2,4}) ")

private fun extractTags(tagsContent: String): String {
    val linkedTags = linkedTagPattern.findAll(tagsContent)
        .map { it.groupValues[1].trim('`') }
        .toList()
    return if (linkedTags.isNotEmpty()) linkedTags.joinToString(", ") else tagsContent
}

internal fun splitPageIntoSections(page: BestPracticesPage): List<BestPracticesSection> {
    val setextSubsectionCount = anchoredSetextSubsectionPattern.findAll(page.markdown).count()
    val atxSubsectionCount = subsectionHeadingPattern.findAll(page.markdown).count()
    val subsectionCount = setextSubsectionCount + atxSubsectionCount
    val markedSetextMarkdown = anchoredSetextSubsectionPattern.replace(page.markdown) { match ->
        "$SECTION_MARKER ${match.groupValues[1].trim()} ${match.groupValues[2]}\n${match.groupValues[3]}"
    }
    val markedMarkdown = subsectionHeadingPattern.replace(markedSetextMarkdown, "## $SECTION_MARKER ")
    val cleanedMarkdown = cleanContent(markedMarkdown)
    if (subsectionCount <= 3) {
        return listOf(
            BestPracticesSection(
                title = page.title,
                markdown = cleanedMarkdown.replace("## $SECTION_MARKER ", "## "),
                tags = "",
                sourcePage = page.title,
            ),
        )
    }

    val subsectionStarts = markedSubsectionHeadingPattern.findAll(cleanedMarkdown).map { it.range.first }.toList()
    check(subsectionStarts.size == subsectionCount) {
        "Expected $subsectionCount marked subsections after cleaning ${page.sourcePath}, but found ${subsectionStarts.size}."
    }
    return subsectionStarts.mapIndexed { index, start ->
        val end = subsectionStarts.getOrNull(index + 1) ?: cleanedMarkdown.length
        val markedChunk = cleanedMarkdown.substring(start, end).trim()
        val chunk = markedChunk.replaceFirst("## $SECTION_MARKER ", "## ")
        val tagsMatch = tagsSectionPattern.find(chunk)
        val tags = tagsMatch?.groupValues?.get(1)
            ?.lineSequence()
            ?.joinToString(" ") { it.trim() }
            ?.trim()
            ?.let(::extractTags)
            .orEmpty()
        val markdownWithoutTags = tagsSectionPattern.replace(chunk, "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        val title = chunk.lineSequence().first().removePrefix("## ").trim()
        val promotedMarkdown = headingPromotionPattern.replace(markdownWithoutTags) { match ->
            "#".repeat(match.groupValues[1].length - 1) + " "
        }

        BestPracticesSection(
            title = title,
            markdown = promotedMarkdown,
            tags = tags,
            sourcePage = page.title,
        )
    }
}

/** Extracts a one-line summary from section markdown: first non-heading paragraph, cleaned and truncated. */
internal fun extractSummary(markdown: String): String {
    val lines = markdown.lines()
    val titleIndex = lines.indexOfFirst { it.startsWith("# ") }
    if (titleIndex == -1) return ""

    val summary = lines.drop(titleIndex + 1)
        .takeWhile { !it.startsWith("## ") && !it.startsWith("---") }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .joinToString(" ")
        .replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("\\(Use `gradle_docs\\([^)]*\\)`\\.?\\)\\)?"), "")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s+([,.;:!?])"), "$1")
        .trim()

    if (summary.length <= 160) return summary
    val wordBoundary = summary.lastIndexOf(' ', startIndex = 159).takeIf { it > 0 } ?: 160
    return summary.substring(0, wordBoundary).trimEnd() + "…"
}

internal fun assignSectionFiles(sections: List<BestPracticesSection>): List<Pair<BestPracticesSection, String>> {
    val usedSlugs = mutableMapOf<String, Int>()
    return sections.mapIndexed { index, section ->
        val baseSlug = sectionAnchor(section.title, index)
        val occurrence = (usedSlugs[baseSlug] ?: 0) + 1
        usedSlugs[baseSlug] = occurrence
        val slug = if (occurrence == 1) baseSlug else "$baseSlug-$occurrence"
        section to "$slug.md"
    }
}

private val tagPattern = Regex("#[A-Za-z0-9-]+")

private fun BestPracticesSection.tagList(): List<String> =
    tagPattern.findAll(tags).map { it.value }.distinct().toList()

internal fun generateIndex(sections: List<BestPracticesSection>, version: String): String = buildString {
    val sectionFiles = assignSectionFiles(sections)
    val sectionsBySourcePage = sectionFiles.groupBy { (section, _) -> section.sourcePage }
    val filesByTag = mutableMapOf<String, MutableList<String>>()

    appendLine("# Gradle Best Practices Index")
    appendLine()
    appendLine("Generated from Gradle $version documentation.")
    appendLine("Read this first to find the relevant practice, then open the linked file for detail.")

    sectionsBySourcePage.forEach { (sourcePage, entries) ->
        appendLine()
        appendLine("## $sourcePage")
        entries.forEach { (section, fileName) ->
            val summary = extractSummary(section.markdown)
            val tags = section.tagList()
            append("- [${section.title}]($fileName)")
            if (summary.isNotEmpty()) append(" — $summary")
            if (tags.isNotEmpty()) append(tags.joinToString(separator = " ", prefix = " ") { "`$it`" })
            appendLine()

            tags.forEach { tag ->
                filesByTag.getOrPut(tag) { mutableListOf() }.add(fileName.removeSuffix(".md"))
            }
        }
    }

    if (filesByTag.isNotEmpty()) {
        appendLine()
        appendLine("## Browse by Tag")
        filesByTag.toSortedMap().forEach { (tag, slugs) ->
            appendLine("- `$tag` — ${slugs.joinToString(", ")}")
        }
    }
}.trimEnd()

/** Writes standalone files per topic for large pages, whole files for small pages, and removes stale Markdown output. */
internal fun writePages(
    outputDir: Path,
    version: String,
    pages: List<BestPracticesPage>,
): Path {
    require(pages.isNotEmpty()) { "Cannot generate Gradle best-practices documentation for version $version: no pages were extracted." }

    Files.deleteIfExists(outputDir.resolve("best_practices.md"))
    val bestPracticesDirectory = outputDir.resolve("best-practices")
    bestPracticesDirectory.createDirectories()

    val sections = pages.flatMap(::splitPageIntoSections)
    val sectionFiles = assignSectionFiles(sections)
    val expectedFiles = sectionFiles.mapTo(mutableSetOf("_index.md")) { it.second }

    Files.list(bestPracticesDirectory).use { paths ->
        paths.filter { path ->
            Files.isRegularFile(path) &&
                path.fileName.toString().endsWith(".md") &&
                path.fileName.toString() !in expectedFiles
        }.forEach(Files::delete)
    }

    bestPracticesDirectory.resolve("_index.md")
        .writeText(generatedFile(version, generateIndex(sections, version)), StandardCharsets.UTF_8)
    sectionFiles.forEach { (section, fileName) ->
        val document = buildString {
            appendLine(section.markdown)
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.")
        }
        bestPracticesDirectory.resolve(fileName).writeText(generatedFile(version, document), StandardCharsets.UTF_8)
    }
    return bestPracticesDirectory
}

/**
 * Prepends the deterministic `generated` provenance header to a best-practices artifact.
 * The `hash` field covers the body bytes exactly as written, enabling drift detection
 * by the skill materialization verifier.
 */
internal fun generatedFile(version: String, body: String): String = buildString {
    appendLine("<!--")
    appendLine("class: generated")
    appendLine("generator: best-practices")
    appendLine("gradle-version: $version")
    appendLine("hash: ${sha256Hex(body)}")
    appendLine("-->")
    append(body)
}

internal fun sha256Hex(content: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun sectionAnchor(title: String, index: Int): String {
    val normalized = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    return normalized.ifEmpty { "topic-$index" }
}

private fun downloadDocsDistribution(version: String): Path {
    val temporaryFile = Files.createTempFile("gradle-$version-docs-", ".zip")
    val url = URL("${DISTRIBUTION_BASE_URL}gradle-$version-docs.zip")
    url.openStream().use { input ->
        Files.newOutputStream(temporaryFile).use { output ->
            input.copyTo(output)
        }
    }
    return temporaryFile
}

private fun extractPages(version: String): List<BestPracticesPage> {
    val distribution = downloadDocsDistribution(version)
    return try {
        ZipFile(distribution.toFile()).use { zipFile ->
            val examinedEntries = mutableListOf<String>()
            val pages = zipFile.entries().asSequence()
                .filter { entry ->
                    examinedEntries += entry.name
                    isBestPracticesPage(entry.name)
                }
                .mapNotNull { entry ->
                    val html = zipFile.getInputStream(entry).use { it.readBytes().toString(StandardCharsets.UTF_8) }
                    val content = extractContent(html) ?: return@mapNotNull null
                    val sourcePath = "docs/${entry.name.substringAfter("docs/")}"
                    val title = extractTitle(html, content, sourcePath)
                    val markdown = normalizeInternalLinks(convertToMarkdown(content), sourcePath)
                    BestPracticesPage(sourcePath, title, markdown)
                }
                .toList()

            if (pages.isEmpty()) {
                val examined = examinedEntries.takeLast(20).joinToString(", ").ifEmpty { "<none>" }
                error("No Gradle best-practices pages were extracted for version $version. Examined entries include: $examined")
            }
            pages
        }
    } finally {
        Files.deleteIfExists(distribution)
    }
}

fun main(args: Array<String>) {
    require(args.size == 2) {
        "Usage: GenerateBestPracticesDoc <output-dir> <gradle-version>"
    }

    val outputDirectory = Path.of(args[0]).toAbsolutePath()
    val version = args[1]
    val pages = extractPages(version)
    val bestPracticesDirectory = writePages(outputDirectory, version, pages)

    println("Generated ${bestPracticesDirectory.absolutePathString()} from ${pages.size} Gradle $version pages")
}
