package dev.rnett.gradle.mcp.tools.skills

import dev.rnett.gradle.mcp.mcp.fixtures.BaseMcpServerTest
import dev.rnett.gradle.mcp.tools.ToolNames
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SkillToolsTest : BaseMcpServerTest() {

    @Test
    fun `install_gradle_skills installs skills to directory`() = runTest {
        val targetDir = tempDir.resolve("installed_skills").toFile()
        val args = mapOf("directory" to JsonPrimitive(targetDir.absolutePath))

        val call = server.client.callTool(ToolNames.INSTALL_GRADLE_SKILLS, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(call.isError != true, "Call should not be an error, but was: $text")
        assertTrue(text.contains("Successfully installed"), "Output should contain success message")
        assertTrue(text.contains("- running_gradle_builds"), "Output should list installed skills")

        // Verify files exist
        val skills = listOf(
            "running_gradle_builds",
            "managing_gradle_dependencies",
            "introspecting_gradle_projects",
            "researching_gradle_internals",
            "interacting_with_project_runtime",
            "running_gradle_tests",
            "verifying_compose_ui"
        )

        skills.forEach { skillName ->
            val skillDir = File(targetDir, skillName)
            assertTrue(skillDir.exists(), "Skill directory $skillName should exist")
            assertTrue(File(skillDir, "SKILL.md").exists(), "SKILL.md should exist in $skillName")
        }

        // Verify some references
        assertTrue(File(targetDir, "running_gradle_builds/references/background_monitoring.md").exists())
        assertTrue(File(targetDir, "running_gradle_tests/references/test_diagnostics.md").exists())
    }

    @Test
    fun `install_gradle_skills skips existing skills by default if NOT authored by this server`() = runTest {
        val targetDir = tempDir.resolve("skip_test").toFile()
        targetDir.mkdirs()

        val skillDir = File(targetDir, "running_gradle_builds")
        skillDir.mkdirs()
        val skillFile = File(skillDir, "SKILL.md")
        skillFile.writeText("EXISTING CONTENT FROM OTHER AUTHOR")

        val args = mapOf("directory" to JsonPrimitive(targetDir.absolutePath))
        val call = server.client.callTool(ToolNames.INSTALL_GRADLE_SKILLS, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(text.contains("Skipped"), "Output should mention skipped skills")
        assertTrue(text.contains("- running_gradle_builds"), "Should skip running_gradle_builds")

        assertTrue(skillFile.exists())
        assertTrue(skillFile.readText() == "EXISTING CONTENT FROM OTHER AUTHOR", "Content should not be overwritten")
    }

    @Test
    fun `install_gradle_skills replaces existing skills by default if authored by this server`() = runTest {
        val targetDir = tempDir.resolve("replace_test").toFile()
        targetDir.mkdirs()

        val skillDir = File(targetDir, "running_gradle_builds")
        skillDir.mkdirs()
        val skillFile = File(skillDir, "SKILL.md")
        skillFile.writeText("author: https://github.com/rnett/gradle-mcp")

        val args = mapOf("directory" to JsonPrimitive(targetDir.absolutePath))
        val call = server.client.callTool(ToolNames.INSTALL_GRADLE_SKILLS, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(text.contains("Successfully installed"), "Output should mention installed skills")
        assertTrue(text.contains("- running_gradle_builds"), "Should install running_gradle_builds")

        assertTrue(skillFile.exists())
        assertTrue(skillFile.readText() != "author: https://github.com/rnett/gradle-mcp", "Content should be overwritten")
    }

    @Test
    fun `install_gradle_skills skips everything with replaceOld=false`() = runTest {
        val targetDir = tempDir.resolve("no_replace_test").toFile()
        targetDir.mkdirs()

        val skillDir = File(targetDir, "running_gradle_builds")
        skillDir.mkdirs()
        val skillFile = File(skillDir, "SKILL.md")
        skillFile.writeText("author: https://github.com/rnett/gradle-mcp")

        val args = mapOf(
            "directory" to JsonPrimitive(targetDir.absolutePath),
            "replaceOld" to JsonPrimitive(false)
        )
        val call = server.client.callTool(ToolNames.INSTALL_GRADLE_SKILLS, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(text.contains("Skipped"), "Output should mention skipped skills")
        assertTrue(text.contains("- running_gradle_builds"), "Should skip running_gradle_builds")

        assertTrue(skillFile.exists())
        assertTrue(skillFile.readText() == "author: https://github.com/rnett/gradle-mcp", "Content should not be overwritten")
    }
}
