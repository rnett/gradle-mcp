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
        assertTrue(text.contains("- gradle-build"), "Output should list installed skills")

        // Verify files exist
        val skills = listOf(
            "gradle-build",
            "gradle-dependencies",
            "gradle-docs",
            "gradle-introspection",
            "gradle-library-sources",
            "gradle-repl",
            "gradle-test"
        )

        skills.forEach { skillName ->
            val skillDir = File(targetDir, skillName)
            assertTrue(skillDir.exists(), "Skill directory $skillName should exist")
            assertTrue(File(skillDir, "SKILL.md").exists(), "SKILL.md should exist in $skillName")
        }

        // Verify some references
        assertTrue(File(targetDir, "gradle-build/references/background-monitoring.md").exists())
        assertTrue(File(targetDir, "gradle-test/references/test-diagnostics.md").exists())
    }

    @Test
    fun `install_gradle_skills skips existing skills by default`() = runTest {
        val targetDir = tempDir.resolve("skip_test").toFile()
        targetDir.mkdirs()

        val skillDir = File(targetDir, "gradle-build")
        skillDir.mkdirs()
        val skillFile = File(skillDir, "SKILL.md")
        skillFile.writeText("EXISTING CONTENT")

        val args = mapOf("directory" to JsonPrimitive(targetDir.absolutePath))
        val call = server.client.callTool(ToolNames.INSTALL_GRADLE_SKILLS, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(text.contains("Skipped"), "Output should mention skipped skills")
        assertTrue(text.contains("- gradle-build"), "Should skip gradle-build")

        assertTrue(skillFile.exists())
        assertTrue(skillFile.readText() == "EXISTING CONTENT", "Content should not be overwritten")
    }

    @Test
    fun `install_gradle_skills replaces existing skills with force`() = runTest {
        val targetDir = tempDir.resolve("force_test").toFile()
        targetDir.mkdirs()

        val skillDir = File(targetDir, "gradle-build")
        skillDir.mkdirs()
        val skillFile = File(skillDir, "SKILL.md")
        skillFile.writeText("EXISTING CONTENT")

        val args = mapOf(
            "directory" to JsonPrimitive(targetDir.absolutePath),
            "force" to JsonPrimitive(true)
        )
        val call = server.client.callTool(ToolNames.INSTALL_GRADLE_SKILLS, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(text.contains("Successfully installed"), "Output should mention installed skills")
        assertTrue(text.contains("- gradle-build"), "Should install gradle-build")

        assertTrue(skillFile.exists())
        assertTrue(skillFile.readText() != "EXISTING CONTENT", "Content should be overwritten")
    }
}
