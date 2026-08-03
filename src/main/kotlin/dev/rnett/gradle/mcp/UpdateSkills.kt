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
 * Marker-based splicing for `docs/skills.md`.
 *
 * Regenerates the included-skills list from the skill source inventory and splices it between the
 * `SKILLS_LIST_START` / `SKILLS_LIST_END` markers, preserving all surrounding hand-authored content.
 * The curated human-facing descriptions live here; the authoritative agent-facing descriptions remain
 * in the `SKILL.md` frontmatter.
 *
 * Usage: `UpdateSkills <rootDir> [--verify]`.
 */
@OptIn(ExperimentalPathApi::class)
object UpdateSkills {

    private const val START = "[//]: # (<<SKILLS_LIST_START>>)\n"
    private const val END = "[//]: # (<<SKILLS_LIST_END>>)\n"
    private const val BLOB_BASE = "https://github.com/rnett/gradle-mcp/blob/main/src/main/skills"

    /** Curated human-facing descriptions in canonical portfolio order. */
    private val DESCRIPTIONS = linkedMapOf(
        "using-gradle" to "Operates existing Gradle builds: orient in the project, run tasks, test, diagnose failures, inspect dependencies, and research Gradle or dependency sources. It includes diagnostic/reporting task guidance (a use-case matrix of core diagnostic tasks plus discovery of plugin-provided reports), targeted `--rerun` vs `--rerun-tasks` cost guidance, and operational build/configuration-cache and isolated-projects footguns. Trivial everyday dependency edits (version-catalog entries, library declarations, and version bumps) are in scope; structural authoring of plugins, repositories, modules, toolchains, publishing, CI, compiler options, or testing frameworks belongs in authoring-gradle-builds.",
        "authoring-gradle-builds" to """Authors and modifies Gradle build definitions, project structure, build logic, and delivery wiring: build lifecycle and Kotlin DSL fundamentals (including the deprecated Kotlin `by` delegates and lazy `register`/`named` patterns), managed types and lazy Property/Provider configuration, task property annotations and file operations (Copy/Sync/Delete and lazy file APIs), extensions, convention and binary plugin development (including TestKit testing and Plugin Portal publishing), Java builds and source sets, `options.release` and Daemon JVM criterion doctrine, configurations and feature variants (including custom attributes and variant-aware artifact sharing), dependencies and catalogs (with conditional dependency verification), toolchains, Kotlin compiler options, testing-framework configuration, publishing, CI, locking, build scans, Worker API, continuous builds, and configuration-cache/build-cache/isolated-projects-safe authoring. Operation/execution (running builds, running tests, diagnosing failures, enabling/persisting the build or configuration cache, and read-only dependency inspection/update discovery) belongs to `using-gradle`; authoring/modifying build definitions belongs to `authoring-gradle-builds`. Trivial one-line everyday dependency edits (catalog entry + declaration + version bump) are a sanctioned overlap in `using-gradle`; anything structural (plugins, repositories, modules, toolchains, publishing, CI) is `authoring-gradle-builds` only. Researching internal Gradle APIs belongs to `using-gradle`'s research workflow.""",
        "interacting-with-project-runtime" to "Provides a persistent JVM/Kotlin REPL for executing and probing project logic within the full classpath context.",
        "verifying-compose-ui" to "Visually verifies Compose UI components and previews by rendering them to images from the JVM runtime.",
    )

    private val namePattern = Regex("(?m)^name:\\s*(\\S+)\\s*$")

    /** Discovers skill directories and validates that frontmatter names match directory names. */
    fun discoverSkills(skillsDir: Path): List<String> =
        skillsDir.listDirectoryEntries().filter { it.isDirectory() }.map { dir ->
            val skillFile = dir.resolve("SKILL.md")
            check(skillFile.exists()) { "Skill directory ${dir.name} is missing SKILL.md" }
            val name = namePattern.find(skillFile.readText())?.groupValues?.get(1)
                ?: error("Skill ${dir.name} has no 'name:' frontmatter entry")
            check(name == dir.name) { "Skill frontmatter name '$name' does not match directory '${dir.name}'" }
            name
        }.sorted()

    fun renderList(): String =
        DESCRIPTIONS.entries.joinToString("\n") { (name, description) ->
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

    /** Returns all violations: inventory drift or docs out of sync. */
    fun verify(root: Path): List<String> = buildList {
        val skillsDir = root.resolve("src/main/skills")
        val discovered = discoverSkills(skillsDir).toSet()
        val expected = DESCRIPTIONS.keys
        if (discovered != expected) {
            add("Skill inventory mismatch with docs manifest: extra=${discovered - expected}, missing=${expected - discovered}")
        }

        val docsFile = root.resolve("docs/skills.md")
        if (!docsFile.exists()) {
            add("docs/skills.md not found")
            return@buildList
        }
        val expectedContent = splice(docsFile.readText(), renderList())
        val actual = docsFile.readText()
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
                println("docs/skills.md skill list is in sync with the skill inventory.")
            } else {
                violations.forEach { println("  - $it") }
                error("Skill documentation verification failed with ${violations.size} violation(s).")
            }
        } else {
            update(root)
        }
    }
}
