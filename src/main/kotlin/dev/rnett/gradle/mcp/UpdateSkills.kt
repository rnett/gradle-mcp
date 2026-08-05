package dev.rnett.gradle.mcp

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Marker-based splicing for `docs/skills.md` and validation of shipped skill metadata.
 *
 * Usage: `UpdateSkills <rootDir> [--verify]`.
 */
@OptIn(ExperimentalPathApi::class)
object UpdateSkills {

    data class Frontmatter(
        val name: String,
        val description: String?,
        val whenToUse: String?,
    )

    private const val START = "[//]: # (<<SKILLS_LIST_START>>)\n"
    private const val END = "[//]: # (<<SKILLS_LIST_END>>)\n"
    private const val BLOB_BASE = "https://github.com/rnett/gradle-mcp/blob/main/src/main/skills"
    private const val DESCRIPTION_LIMIT = 400
    private const val COMBINED_LIMIT = 1536

    /** Agent-facing and human-facing descriptions in canonical portfolio order. */
    val DESCRIPTIONS = linkedMapOf(
        "using-gradle" to "Using Gradle MCP tools to inspect and run existing builds, including projects, tasks, properties, dependencies, and build results. Activate for Gradle build operation and diagnosis; use authoring-gradle-builds when the build definition itself must change.",
        "authoring-gradle-builds" to "Authoring and modifying Gradle build logic, including settings, plugins, dependencies, tasks, conventions, and upgrades. Activate when build files or plugins must change; use using-gradle for inspection or execution without build-definition edits.",
        "advanced-gradle-dependencies" to "Analyzing advanced Gradle dependency behavior, including resolution, variants, capabilities, conflicts, constraints, and publication metadata. Activate for dependency-resolution design or diagnosis; use authoring-gradle-builds for routine dependency declarations.",
        "interacting-with-project-runtime" to "Interacting with a project's JVM runtime through the Kotlin REPL to execute focused probes against project classes and dependencies. Activate when behavior must be observed by running code; use using-gradle for build inspection or task execution.",
        "verifying-compose-ui" to "Verifying Compose UI by rendering components or previews and inspecting the resulting images and state transitions. Activate for visual behavior that requires runtime rendering; use ordinary tests for nonvisual logic.",
    )

    private val fieldPattern = Regex("^([A-Za-z_][A-Za-z0-9_-]*):(?:\\s*(.*))?$")
    private val blockIndicatorPattern = Regex("[|>][+-]?")

    /** Parses the top-level YAML scalars used by skill frontmatter. */
    fun parseFrontmatter(skillName: String, content: String): Frontmatter {
        val lines = content.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        require(lines.firstOrNull() == "---") { "Skill $skillName has malformed frontmatter: missing opening '---'" }
        val end = lines.indexOfFirst { index, line -> index > 0 && line == "---" }
        require(end > 0) { "Skill $skillName has malformed frontmatter: missing closing '---'" }

        val values = linkedMapOf<String, String>()
        var index = 1
        while (index < end) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#") || line.first().isWhitespace()) {
                index++
                continue
            }
            val match = fieldPattern.matchEntire(line)
                ?: throw IllegalArgumentException("Skill $skillName has malformed frontmatter at line ${index + 1}: expected 'key: value'")
            val key = match.groupValues[1]
            require(key !in values) { "Skill $skillName has malformed frontmatter: duplicate '$key' entry" }
            val rawValue = match.groupValues[2]
            val parsed = if (blockIndicatorPattern.matches(rawValue)) {
                val blockStart = index + 1
                var blockEnd = blockStart
                while (blockEnd < end && (lines[blockEnd].isBlank() || lines[blockEnd].first().isWhitespace())) blockEnd++
                index = blockEnd - 1
                parseBlockScalar(lines.subList(blockStart, blockEnd), rawValue.first(), rawValue.drop(1))
            } else {
                parseInlineScalar(skillName, key, rawValue)
            }
            values[key] = parsed
            index++
        }

        val name = values["name"]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Skill $skillName has malformed frontmatter: missing or blank 'name'")
        return Frontmatter(name, values["description"], values["when_to_use"])
    }

    private fun List<String>.indexOfFirst(predicate: (Int, String) -> Boolean): Int {
        forEachIndexed { index, value -> if (predicate(index, value)) return index }
        return -1
    }

    private fun parseInlineScalar(skillName: String, key: String, rawValue: String): String {
        return when {
            rawValue.startsWith("'") -> {
                require(rawValue.length >= 2 && rawValue.endsWith("'")) {
                    "Skill $skillName has malformed frontmatter: unterminated quoted '$key' value"
                }
                rawValue.substring(1, rawValue.length - 1).replace("''", "'")
            }

            rawValue.startsWith("\"") -> {
                require(rawValue.length >= 2 && rawValue.endsWith("\"")) {
                    "Skill $skillName has malformed frontmatter: unterminated quoted '$key' value"
                }
                rawValue.substring(1, rawValue.length - 1)
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }

            else -> rawValue.substringBefore(" #").trimEnd()
        }
    }

    private fun parseBlockScalar(lines: List<String>, style: Char, modifiers: String): String {
        if (lines.isEmpty()) return ""
        val indent = lines.filter { it.isNotBlank() }.minOfOrNull { it.indexOfFirst { char -> !char.isWhitespace() } } ?: 0
        val normalized = lines.map { if (it.isBlank()) "" else it.drop(indent) }
        val body = if (style == '|') {
            normalized.joinToString("\n")
        } else {
            buildString {
                normalized.forEachIndexed { index, line ->
                    if (index > 0) append(if (line.isBlank() || normalized[index - 1].isBlank()) '\n' else ' ')
                    append(line)
                }
            }
        }
        return when {
            '-' in modifiers -> body.trimEnd('\n')
            '+' in modifiers -> body + "\n"
            else -> body.trimEnd('\n') + "\n"
        }
    }

    /** Returns description budget violations for a parsed skill. */
    fun validateDescription(skillName: String, description: String?, whenToUse: String? = null): List<String> = buildList {
        val descriptionLength = description?.length ?: 0
        if (description.isNullOrBlank()) {
            add("Skill $skillName description is missing or blank: measured $descriptionLength characters; limit is $DESCRIPTION_LIMIT")
        } else if (descriptionLength > DESCRIPTION_LIMIT) {
            add("Skill $skillName description measured $descriptionLength characters; limit is $DESCRIPTION_LIMIT")
        }
        val combinedLength = descriptionLength + (whenToUse?.length ?: 0)
        if (whenToUse != null && combinedLength > COMBINED_LIMIT) {
            add("Skill $skillName description + when_to_use measured $combinedLength characters; limit is $COMBINED_LIMIT")
        }
    }

    /** Returns parse and description violations for one skill file. */
    fun validateFrontmatter(skillName: String, content: String): List<String> = try {
        val frontmatter = parseFrontmatter(skillName, content)
        buildList {
            if (frontmatter.name != skillName) {
                add("Skill frontmatter name '${frontmatter.name}' does not match directory '$skillName'")
            }
            addAll(validateDescription(skillName, frontmatter.description, frontmatter.whenToUse))
        }
    } catch (error: IllegalArgumentException) {
        listOf(error.message ?: "Skill $skillName has malformed frontmatter")
    }

    private fun skillDirectories(skillsDir: Path): List<Path> =
        skillsDir.listDirectoryEntries().filter { it.isDirectory() }.sortedBy { it.name }

    /** Discovers skill directories and validates their frontmatter. */
    fun discoverSkills(skillsDir: Path): List<String> = skillDirectories(skillsDir).map { dir ->
        val skillFile = dir.resolve("SKILL.md")
        check(skillFile.exists()) { "Skill directory ${dir.name} is missing SKILL.md" }
        val frontmatter = parseFrontmatter(dir.name, skillFile.readText())
        check(frontmatter.name == dir.name) { "Skill frontmatter name '${frontmatter.name}' does not match directory '${dir.name}'" }
        dir.name
    }

    fun renderList(): String = DESCRIPTIONS.entries.joinToString("\n") { (name, description) ->
        "* **[$name]($BLOB_BASE/$name/SKILL.md)**: $description"
    }

    fun splice(docsContent: String, list: String): String {
        val startCount = Regex.fromLiteral(START).findAll(docsContent).count()
        val endCount = Regex.fromLiteral(END).findAll(docsContent).count()
        require(startCount == 1 && endCount == 1) {
            "docs/skills.md must contain exactly one SKILLS_LIST_START and one SKILLS_LIST_END marker (found $startCount start, $endCount end)"
        }
        val before = docsContent.substringBefore(START) + START
        val after = END + docsContent.substringAfter(END)
        return before + "\n" + list + "\n\n" + after
    }

    /** Returns all violations: invalid metadata, inventory drift, or docs out of sync. */
    fun verify(root: Path): List<String> = buildList {
        val skillsDir = root.resolve("src/main/skills")
        val directories = skillDirectories(skillsDir)
        val discovered = directories.map { it.name }.toSet()
        val expected = DESCRIPTIONS.keys
        if (discovered != expected) {
            add("Skill inventory mismatch with docs manifest: extra=${discovered - expected}, missing=${expected - discovered}")
        }
        directories.forEach { dir ->
            val skillFile = dir.resolve("SKILL.md")
            if (!skillFile.exists()) {
                add("Skill directory ${dir.name} is missing SKILL.md")
            } else {
                addAll(validateFrontmatter(dir.name, skillFile.readText()))
            }
        }

        val docsFile = root.resolve("docs/skills.md")
        if (!docsFile.exists()) {
            add("docs/skills.md not found")
            return@buildList
        }
        val actual = docsFile.readText()
        val expectedContent = splice(actual, renderList())
        if (actual != expectedContent) {
            add("docs/skills.md skill list is out of sync; run 'updateSkillsList'")
        }
    }

    fun update(root: Path) {
        val skillsDir = root.resolve("src/main/skills")
        val discovered = discoverSkills(skillsDir).toSet()
        require(discovered == DESCRIPTIONS.keys) {
            "Skill inventory mismatch with docs manifest: extra=${discovered - DESCRIPTIONS.keys}, missing=${DESCRIPTIONS.keys - discovered}"
        }
        val violations = skillDirectories(skillsDir).flatMap { dir ->
            validateFrontmatter(dir.name, dir.resolve("SKILL.md").readText())
        }
        require(violations.isEmpty()) { violations.joinToString("\n") }

        val docsFile = root.resolve("docs/skills.md")
        require(docsFile.exists()) { "docs/skills.md not found" }
        docsFile.writeText(splice(docsFile.readText(), renderList()))
        println("Updated skill list in $docsFile")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val root = args.firstOrNull()?.let { Path(it) }?.toAbsolutePath() ?: Path("").toAbsolutePath()
        val verify = args.contains("--verify")

        if (verify) {
            val violations = verify(root)
            if (violations.isEmpty()) {
                println("docs/skills.md and shipped skill metadata are in sync.")
            } else {
                violations.forEach { println("  - $it") }
                error("Skill documentation verification failed with ${violations.size} violation(s).")
            }
        } else {
            update(root)
        }
    }
}
