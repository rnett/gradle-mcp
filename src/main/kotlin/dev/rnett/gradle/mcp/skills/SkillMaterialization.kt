package dev.rnett.gradle.mcp.skills

import java.net.URI
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.system.exitProcess

/**
 * Deterministic materialization and drift verification for the Gradle MCP skill portfolio.
 *
 * Responsibilities:
 * - Fan out `authored-shared` sources from `src/main/skill-sources/authored-shared` into their declared skill targets.
 * - Verify that the working tree matches the authoritative sources: shared fan-out identity, generated
 *   content hashes, provenance headers, inventory, dead links, and reference reachability from `SKILL.md`.
 *
 * Usage: `SkillMaterialization materialize <rootDir>` or `SkillMaterialization verify <rootDir> [gradleDocsVersion]`.
 */
@OptIn(ExperimentalPathApi::class)
object SkillMaterialization {

    const val SKILLS_DIR = "src/main/skills"
    const val SHARED_SOURCES_DIR = "src/main/skill-sources/authored-shared"

    /** The exact four-name portfolio inventory, in canonical order. */
    val CANONICAL_SKILLS = listOf(
        "using-gradle",
        "authoring-gradle-builds",
        "interacting-with-project-runtime",
        "verifying-compose-ui",
    )


    // region Header and hash utilities

    fun sha256Hex(content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * Renders a generated file: a deterministic provenance header followed by the body.
     * The header `hash` covers the body bytes exactly as written, enabling drift detection.
     */
    fun renderGeneratedFile(skill: String?, generator: String, body: String, extraFields: Map<String, String> = emptyMap()): String {
        val header = buildString {
            appendLine("<!--")
            appendLine("class: generated")
            if (skill != null) appendLine("skill: $skill")
            appendLine("generator: $generator")
            extraFields.forEach { (key, value) -> appendLine("$key: $value") }
            appendLine("hash: ${sha256Hex(body)}")
            appendLine("-->")
        }
        return header + body
    }

    data class GeneratedFile(val fields: Map<String, String>, val body: String)

    /** Splits a file that starts with a generated header into its fields and body, or returns null. */
    fun splitGeneratedHeader(content: String): GeneratedFile? {
        if (!content.startsWith("<!--\n")) return null
        val end = content.indexOf("\n-->\n")
        if (end == -1) return null
        val fields = content.substring(4, end).lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) return@mapNotNull null
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .toMap()
        return GeneratedFile(fields, content.substring(end + "\n-->\n".length))
    }

    private val provenancePattern = Regex("(?m)^class: (?:authored-local|authored-shared|generated)$")

    /** Every markdown resource in a skill root must carry a deterministic provenance header near the top. */
    fun hasProvenanceHeader(content: String): Boolean {
        val head = content.lineSequence().take(40).joinToString("\n")
        return provenancePattern.containsMatchIn(head)
    }

    /** Parses the first HTML comment header block of a file into simple `key: value` pairs. */
    fun parseHeaderBlock(content: String): Map<String, String>? {
        if (!content.startsWith("<!--\n")) return null
        val end = content.indexOf("\n-->")
        if (end == -1) return null
        return content.substring(4, end).lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) return@mapNotNull null
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .toMap()
    }

    // endregion


    // region Shared source fan-out

    data class SharedSource(val path: Path, val targets: List<String>)

    fun sharedSources(root: Path): List<SharedSource> {
        val dir = root.resolve(SHARED_SOURCES_DIR)
        if (!dir.exists()) return emptyList()
        return dir.listDirectoryEntries("*.md").sortedBy { it.name }.map { source ->
            val header = parseHeaderBlock(source.readText())
                ?: throw IllegalStateException("Shared source $source is missing a header block")
            val targets = header["targets"]
                ?.split(',')
                ?.map { it.trim().replace('\\', '/') }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            if (header["class"] != "authored-shared") {
                throw IllegalStateException("Shared source $source must declare 'class: authored-shared'")
            }
            if (targets.isEmpty()) {
                throw IllegalStateException("Shared source $source must declare at least one 'targets:' entry")
            }
            SharedSource(source, targets)
        }
    }

    // endregion

    // region Materialization

    fun materialize(root: Path) {
        val skillsDir = root.resolve(SKILLS_DIR)
        require(skillsDir.exists()) { "Skills directory not found: $skillsDir" }

        val inventory = skillsDir.listDirectoryEntries().filter { it.isDirectory() }.map { it.name }.toSet()
        require(inventory == CANONICAL_SKILLS.toSet()) {
            "Skill source inventory $inventory does not equal the canonical portfolio ${CANONICAL_SKILLS.toSet()}"
        }

        var written = 0

        sharedSources(root).forEach { source ->
            val bytes = source.path.readBytes()
            source.targets.forEach { target ->
                val targetPath = skillsDir.resolve(target)
                targetPath.parent.createDirectories()
                targetPath.writeBytes(bytes)
                written++
            }
        }


        println("Materialized $written skill resources into $skillsDir")
    }

    // endregion

    // region Verification

    fun verify(root: Path, expectedBestPracticesVersion: String? = null): List<String> = buildList {
        val skillsDir = root.resolve(SKILLS_DIR)
        if (!skillsDir.exists()) {
            add("Skills directory not found: $skillsDir")
            return@buildList
        }

        checkInventory(skillsDir).let(::addAll)
        checkProvenanceHeaders(skillsDir).let(::addAll)
        checkSharedFanOut(root).let(::addAll)
        checkGeneratedContent(skillsDir, expectedBestPracticesVersion).let(::addAll)
        checkReferenceReachability(skillsDir).let(::addAll)
        checkNoBlockedDocUrls(skillsDir).let(::addAll)
    }

    fun checkInventory(skillsDir: Path): List<String> {
        val actual = skillsDir.listDirectoryEntries().filter { it.isDirectory() }.map { it.name }.toSortedSet()
        val expected = CANONICAL_SKILLS.toSortedSet()
        return if (actual == expected) {
            emptyList()
        } else {
            listOf(
                "Skill inventory mismatch: extra=${actual - expected}, missing=${expected - actual}"
            )
        }
    }

    fun checkProvenanceHeaders(skillsDir: Path): List<String> =
        allMarkdownFiles(skillsDir).mapNotNull { file ->
            if (hasProvenanceHeader(file.readText())) null
            else "Missing provenance header (class: authored-local|authored-shared|generated): ${file.relativeTo(skillsDir)}"
        }

    fun checkSharedFanOut(root: Path): List<String> = buildList {
        val skillsDir = root.resolve(SKILLS_DIR)
        sharedSources(root).forEach { source ->
            val sourceBytes = source.path.readBytes()
            source.targets.forEach { target ->
                val targetPath = skillsDir.resolve(target)
                when {
                    !CANONICAL_SKILLS.contains(target.substringBefore('/')) ->
                        add("Shared target $target is not inside a canonical skill root")

                    !targetPath.exists() ->
                        add("Shared target ${targetPath.relativeTo(root)} has not been materialized from ${source.path.relativeTo(root)}")

                    !targetPath.readBytes().contentEquals(sourceBytes) ->
                        add("Shared target ${targetPath.relativeTo(root)} has drifted from authoritative source ${source.path.relativeTo(root)}")
                }
            }
        }
    }

    fun checkGeneratedContent(skillsDir: Path, expectedBestPracticesVersion: String?): List<String> =
        allMarkdownFiles(skillsDir).mapNotNull { file ->
            val content = file.readText()
            val generated = splitGeneratedHeader(content) ?: return@mapNotNull null
            if (generated.fields["class"] != "generated") return@mapNotNull null
            val relative = file.relativeTo(skillsDir).toString()

            val hash = generated.fields["hash"]
                ?: return@mapNotNull "Generated file $relative is missing a 'hash' header field"
            if (hash != sha256Hex(generated.body)) {
                return@mapNotNull "Generated file $relative has drifted from its recorded content hash (manual edit detected)"
            }


            if (generated.fields["generator"] == "best-practices" && expectedBestPracticesVersion != null) {
                val version = generated.fields["gradle-version"]
                if (version != expectedBestPracticesVersion) {
                    return@mapNotNull "Generated file $relative was produced from Gradle docs '$version' but the build expects '$expectedBestPracticesVersion'; run generateBestPracticesDoc"
                }
            }

            null
        }

    fun checkNoBlockedDocUrls(skillsDir: Path): List<String> =
        allMarkdownFiles(skillsDir).flatMap { file ->
            val relative = file.relativeTo(skillsDir)
            Regex("https?://[^\\s)\\]>]+")
                .findAll(file.readText())
                .mapNotNull { match ->
                    val host = runCatching { URI(match.value).host }.getOrNull()
                    if (host == "docs.gradle.org" || host == "gradle-mcp.rnett.dev") {
                        "Blocked documentation URL in $relative: ${match.value}"
                    } else null
                }
                .toList()
        }

    fun checkReferenceReachability(skillsDir: Path): List<String> = buildList {
        CANONICAL_SKILLS.forEach { skill ->
            val skillDir = skillsDir.resolve(skill)
            if (!skillDir.isDirectory()) return@forEach
            val relativeSkill = skillDir.relativeTo(skillsDir)

            // Dead links: every relative markdown link must resolve.
            allMarkdownFiles(skillDir).forEach { file ->
                markdownLinkPattern.findAll(file.readText()).forEach { match ->
                    val target = match.groupValues[1]
                    if (isExternalLink(target)) return@forEach
                    val resolved = file.parent.resolve(target.substringBefore('#')).normalize()
                    if (!resolved.exists()) {
                        add("Dead link in ${file.relativeTo(skillsDir)}: $target")
                    }
                }
            }

            // Every reference must be reachable from SKILL.md through markdown links.
            val entryPoint = skillDir.resolve("SKILL.md")
            if (!entryPoint.exists()) {
                add("Skill $skill is missing SKILL.md")
                return@forEach
            }
            val reachable = mutableSetOf<Path>()
            val queue = ArrayDeque<Path>()
            queue.add(entryPoint.normalize())
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (!reachable.add(current) || !current.isRegularFile()) continue
                markdownLinkPattern.findAll(current.readText()).forEach { match ->
                    val target = match.groupValues[1]
                    if (isExternalLink(target)) return@forEach
                    queue.add(current.parent.resolve(target.substringBefore('#')).normalize())
                }
            }
            val referencesDir = skillDir.resolve("references")
            if (referencesDir.isDirectory()) {
                allMarkdownFiles(referencesDir).forEach { file ->
                    if (file.normalize() !in reachable) {
                        add("Orphaned reference in $relativeSkill: ${file.relativeTo(skillDir)} is not linked from SKILL.md or any reachable reference")
                    }
                }
            }
        }
    }

    // endregion

    // region Helpers

    private val markdownLinkPattern = Regex("""\[[^\]]*]\(([^)\s]+)\)""")

    private fun isExternalLink(target: String): Boolean =
        target.startsWith("#") ||
            target.startsWith("mailto:") ||
            target.startsWith("data:") ||
            target.contains("://")

    private fun allMarkdownFiles(dir: Path): List<Path> = buildList {
        if (!dir.isDirectory()) return@buildList
        val queue = ArrayDeque<Path>()
        queue.add(dir)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            current.listDirectoryEntries().sortedBy { it.name }.forEach { entry ->
                when {
                    entry.isDirectory() -> queue.add(entry)
                    entry.name.endsWith(".md") -> add(entry)
                }
            }
        }
        sortBy { it.toString() }
    }

    // endregion

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0)
        val root = args.getOrNull(1)?.let { Path(it) }?.toAbsolutePath() ?: Path("").toAbsolutePath()

        when (mode) {
            "materialize" -> materialize(root)

            "verify" -> {
                val expectedVersion = args.getOrNull(2)
                val violations = verify(root, expectedVersion)
                if (violations.isEmpty()) {
                    println("Skill materialization verified: shared fan-out, generated hashes, and reference reachability from SKILL.md are consistent.")
                } else {
                    violations.forEach { println("  - $it") }
                    error("Skill materialization verification failed with ${violations.size} violation(s). Run 'materializeSkills' and regenerate best-practices to repair.")
                }
            }

            else -> {
                System.err.println("Usage: SkillMaterialization <materialize|verify> <rootDir> [gradleDocsVersion]")
                exitProcess(2)
            }
        }
    }
}
