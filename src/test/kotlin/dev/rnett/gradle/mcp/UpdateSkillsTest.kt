package dev.rnett.gradle.mcp

import org.junit.jupiter.api.Test
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UpdateSkillsTest {

    private val projectRoot = findProjectRoot()

    @Test
    fun `docs skills list is in sync with the skill inventory`() {
        val violations = UpdateSkills.verify(projectRoot)
        assertEquals(emptyList(), violations, "Expected docs and metadata to be in sync, but found:\n${violations.joinToString("\n")}")
    }

    @Test
    fun `description accepts 400 characters and rejects 401`() {
        assertEquals(emptyList(), UpdateSkills.validateDescription("boundary-skill", "a".repeat(400)))
        val violations = UpdateSkills.validateDescription("boundary-skill", "a".repeat(401))
        assertEquals(1, violations.size)
        assertContains(violations.single(), "boundary-skill")
        assertContains(violations.single(), "401")
        assertContains(violations.single(), "400")
    }

    @Test
    fun `combined metadata accepts 1536 characters and rejects 1537`() {
        assertEquals(
            emptyList(),
            UpdateSkills.validateDescription("boundary-skill", "a".repeat(400), "b".repeat(1136)),
        )
        val violations = UpdateSkills.validateDescription("boundary-skill", "a".repeat(400), "b".repeat(1137))
        assertEquals(1, violations.size)
        assertContains(violations.single(), "boundary-skill")
        assertContains(violations.single(), "1537")
        assertContains(violations.single(), "1536")
    }

    @Test
    fun `absent when to use contributes zero characters`() {
        assertEquals(emptyList(), UpdateSkills.validateDescription("no-when-to-use", "a".repeat(400), null))
    }

    @Test
    fun `malformed and missing frontmatter produce actionable diagnostics`() {
        val malformed = UpdateSkills.validateFrontmatter("broken-skill", "name: broken-skill\ndescription: text")
        assertEquals(1, malformed.size)
        assertContains(malformed.single(), "broken-skill")
        assertContains(malformed.single(), "missing opening '---'")

        val missingDescription = UpdateSkills.validateFrontmatter(
            "missing-description",
            "---\nname: missing-description\nlicense: Apache-2.0\n---\n# Body",
        )
        assertEquals(1, missingDescription.size)
        assertContains(missingDescription.single(), "missing-description")
        assertContains(missingDescription.single(), "measured 0")
        assertContains(missingDescription.single(), "limit is 400")
    }

    @Test
    fun `all packaged skills have valid descriptions matching the docs manifest`() {
        val skillsDir = projectRoot.resolve("src/main/skills")
        val shippedSkills = UpdateSkills.discoverSkills(skillsDir)
        assertEquals(UpdateSkills.DESCRIPTIONS.keys.sorted(), shippedSkills)

        shippedSkills.forEach { name ->
            val content = skillsDir.resolve(name).resolve("SKILL.md").readText()
            val frontmatter = UpdateSkills.parseFrontmatter(name, content)
            assertEquals(UpdateSkills.DESCRIPTIONS.getValue(name), frontmatter.description, "$name description drifted")
            assertTrue(UpdateSkills.validateDescription(name, frontmatter.description, frontmatter.whenToUse).isEmpty())
        }
    }

    @Test
    fun `splice replaces only the marker section and preserves surrounding content`() {
        val original = """
            # Agent Skills

            Hand-authored intro.

            [//]: # (<<SKILLS_LIST_START>>)

            * stale entry

            [//]: # (<<SKILLS_LIST_END>>)

            Hand-authored outro.
        """.trimIndent()
        val spliced = UpdateSkills.splice(original, "* fresh entry")
        assertEquals(
            """
            # Agent Skills

            Hand-authored intro.

            [//]: # (<<SKILLS_LIST_START>>)

            * fresh entry

            [//]: # (<<SKILLS_LIST_END>>)

            Hand-authored outro.
            """.trimIndent(),
            spliced,
        )
    }

    @Test
    fun `splice fails when markers are missing`() {
        assertFailsWith<IllegalArgumentException> {
            UpdateSkills.splice("# No markers here", "* entry")
        }
    }
}
