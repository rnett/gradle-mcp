package dev.rnett.gradle.mcp.skills

import dev.rnett.gradle.mcp.findProjectRoot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkillMaterializationTest {

    private val projectRoot = findProjectRoot()

    @Test
    fun `repository skill tree verifies clean`() {
        val violations = SkillMaterialization.verify(projectRoot)
        assertEquals(emptyList(), violations, "Expected no materialization violations, but found:\n${violations.joinToString("\n")}")
    }

    @Test
    fun `no generated root reference index exists`() {
        val skillsDir = projectRoot.resolve("src/main/skills")
        SkillMaterialization.CANONICAL_SKILLS.forEach { skill ->
            assertTrue(
                !skillsDir.resolve("$skill/references/_index.md").exists(),
                "Root reference index must not exist for $skill",
            )
        }
    }

    @Test
    fun `materialize is idempotent against the repository tree`(@TempDir tempDir: Path) {
        val tempRoot = copySkillSources(tempDir)

        SkillMaterialization.materialize(tempRoot)
        assertTreesEqual(projectRoot.resolve("src/main/skills"), tempRoot.resolve("src/main/skills"))
        assertEquals(emptyList(), SkillMaterialization.verify(tempRoot))

        SkillMaterialization.materialize(tempRoot)
        assertTreesEqual(projectRoot.resolve("src/main/skills"), tempRoot.resolve("src/main/skills"))
    }

    @Test
    fun `detects drift in a shared fan-out target`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("src/main/skill-sources/authored-shared/setup.md")
        source.parent.createDirectories()
        source.writeText(
            """
            <!--
            class: authored-shared
            targets: using-gradle/references/setup.md
            -->
            # Canonical Content
            """.trimIndent()
        )
        val target = tempDir.resolve("src/main/skills/using-gradle/references/setup.md")
        target.parent.createDirectories()
        target.writeText("# Manually Edited Content")

        val violations = SkillMaterialization.checkSharedFanOut(tempDir)
        assertEquals(1, violations.size)
        assertContains(violations.single(), "drifted from authoritative source")
    }

    @Test
    fun `detects a missing shared fan-out target`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("src/main/skill-sources/authored-shared/setup.md")
        source.parent.createDirectories()
        source.writeText(
            """
            <!--
            class: authored-shared
            targets: verifying-compose-ui/references/setup.md
            -->
            # Canonical Content
            """.trimIndent()
        )

        val violations = SkillMaterialization.checkSharedFanOut(tempDir)
        assertEquals(1, violations.size)
        assertContains(violations.single(), "has not been materialized")
    }

    @Test
    fun `detects manual edits to generated content`(@TempDir tempDir: Path) {
        val skillsDir = tempDir.resolve("src/main/skills")
        val generated = skillsDir.resolve("authoring-gradle-builds/references/best-practices/topic.md")
        generated.parent.createDirectories()
        generated.writeText(
            SkillMaterialization.renderGeneratedFile(
                skill = null,
                generator = "best-practices",
                body = "# Original Body\n",
                extraFields = mapOf("gradle-version" to "9.6.1"),
            )
        )
        assertEquals(emptyList(), SkillMaterialization.checkGeneratedContent(skillsDir, "9.6.1"))

        generated.writeText(generated.readText().replace("Original Body", "Tampered Body"))
        val violations = SkillMaterialization.checkGeneratedContent(skillsDir, "9.6.1")
        assertEquals(1, violations.size)
        assertContains(violations.single(), "drifted from its recorded content hash")
    }

    @Test
    fun `detects gradle docs version drift in best-practices artifacts`(@TempDir tempDir: Path) {
        val skillsDir = tempDir.resolve("src/main/skills")
        val generated = skillsDir.resolve("authoring-gradle-builds/references/best-practices/topic.md")
        generated.parent.createDirectories()
        generated.writeText(
            SkillMaterialization.renderGeneratedFile(
                skill = null,
                generator = "best-practices",
                body = "# Body\n",
                extraFields = mapOf("gradle-version" to "9.5.0"),
            )
        )

        val violations = SkillMaterialization.checkGeneratedContent(skillsDir, "9.6.1")
        assertEquals(1, violations.size)
        assertContains(violations.single(), "run generateBestPracticesDoc")
    }


    @Test
    fun `detects dead links and orphaned references`(@TempDir tempDir: Path) {
        val skillDir = tempDir.resolve("using-gradle")
        skillDir.resolve("references").createDirectories()
        skillDir.resolve("SKILL.md").writeText(
            """
            # Skill
            See [Missing](references/missing.md) and [Linked](references/linked.md).
            """.trimIndent()
        )
        skillDir.resolve("references/linked.md").writeText("# Linked")
        skillDir.resolve("references/orphan.md").writeText("# Orphan")

        val violations = SkillMaterialization.checkReferenceReachability(tempDir)
        assertTrue(violations.any { "Dead link" in it && "missing.md" in it }, "Expected dead link violation, got: $violations")
        assertTrue(violations.any { "Orphaned reference" in it && "orphan.md" in it }, "Expected orphan violation, got: $violations")
    }

    @Test
    fun `detects missing provenance headers`(@TempDir tempDir: Path) {
        val skillsDir = tempDir.resolve("src/main/skills")
        val noHeader = skillsDir.resolve("using-gradle/references/bare.md")
        noHeader.parent.createDirectories()
        noHeader.writeText("# No Provenance Header\n")

        val violations = SkillMaterialization.checkProvenanceHeaders(skillsDir)
        assertEquals(1, violations.size)
        assertContains(violations.single(), "Missing provenance header")
    }

    @Test
    fun `generated file headers round-trip through the parser`() {
        val body = "# Body Content\n\nWith details.\n"
        val rendered = SkillMaterialization.renderGeneratedFile(
            skill = "using-gradle",
            generator = "best-practices",
            body = body,
        )

        val parsed = assertNotNull(SkillMaterialization.splitGeneratedHeader(rendered))
        assertEquals("generated", parsed.fields["class"])
        assertEquals("using-gradle", parsed.fields["skill"])
        assertEquals("best-practices", parsed.fields["generator"])
        assertEquals(SkillMaterialization.sha256Hex(body), parsed.fields["hash"])
        assertEquals(body, parsed.body)
    }

    private fun copySkillSources(tempRoot: Path): Path {
        listOf("src/main/skills", "src/main/skill-sources").forEach { relative ->
            val source = projectRoot.resolve(relative)
            val target = tempRoot.resolve(relative)
            source.toFile().copyRecursively(target.toFile(), overwrite = true)
        }
        return tempRoot
    }

    private fun assertTreesEqual(expected: Path, actual: Path) {
        assertTrue(expected.isDirectory(), "Expected tree missing: $expected")
        assertTrue(actual.isDirectory(), "Actual tree missing: $actual")
        fun treeFiles(root: Path): Map<String, ByteArray> =
            root.toFile().walkTopDown().filter { it.isFile }.associate { file ->
                file.toPath().relativeTo(root).toString() to file.toPath().readBytes()
            }
        val expectedFiles = treeFiles(expected)
        val actualFiles = treeFiles(actual)
        assertEquals(expectedFiles.keys.sorted(), actualFiles.keys.sorted(), "File sets differ")
        expectedFiles.forEach { (path, bytes) ->
            assertTrue(bytes.contentEquals(actualFiles[path]), "Content differs for $path")
        }
    }
}
