package dev.rnett.gradle.mcp.skills

import dev.rnett.gradle.mcp.findProjectRoot
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Safety gates for skill artifacts: `afterEvaluate` may only appear in prohibition/avoidance context
 * or behind an explicit justification marker, per the `authoring-gradle-builds` safety constraint.
 */
class SkillArtifactSafetyTest {

    private val skillsDir = findProjectRoot().resolve("src/main/skills").toFile()

    /**
     * Markers that place an `afterEvaluate` mention in a prohibition or avoidance context.
     * Any mention without one of these must carry an explicit `afterEvaluate-justification:` block.
     */
    private val negativeContextMarkers = listOf(
        "prohibited",
        "Avoid `afterEvaluate`",
        "avoid-afterevaluate",
        "Don't Do This",
        "removes `afterEvaluate`",
    )

    @Test
    fun `afterEvaluate mentions require prohibition context or an explicit justification block`() {
        val offenders = skillsDir.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .mapNotNull { file ->
                val content = file.readText()
                if ("afterEvaluate" !in content) return@mapNotNull null
                val hasNegativeContext = negativeContextMarkers.any { it in content }
                val hasJustification = "afterEvaluate-justification:" in content
                if (hasNegativeContext || hasJustification) {
                    null
                } else {
                    "${file.relativeTo(skillsDir)} mentions `afterEvaluate` without prohibition context " +
                        "or an `afterEvaluate-justification:` block"
                }
            }
            .toList()

        assertTrue(offenders.isEmpty(), "Unjustified afterEvaluate usage in skill artifacts:\n${offenders.joinToString("\n")}")
    }
}
