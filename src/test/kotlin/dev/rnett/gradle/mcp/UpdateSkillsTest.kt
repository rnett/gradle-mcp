package dev.rnett.gradle.mcp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdateSkillsTest {

    private val projectRoot = findProjectRoot()

    @Test
    fun `docs skills list is in sync with the skill inventory`() {
        val violations = UpdateSkills.verify(projectRoot)
        assertEquals(emptyList(), violations, "Expected docs to be in sync, but found:\n${violations.joinToString("\n")}")
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
