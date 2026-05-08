package dev.rnett.gradle.mcp.tools.skills

import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.tools.ToolNames
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import java.io.File
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
        assertTrue(text.contains("- gradle"), "Output should list installed skills")

        // Verify files exist
        val skills = listOf(
            "gradle",
            "exploring_dependency_sources",
            "managing_gradle_dependencies",
            "interacting_with_project_runtime",
            "verifying_compose_ui"
        )

        skills.forEach { skillName ->
            val skillDir = File(targetDir, skillName)
            assertTrue(skillDir.exists(), "Skill directory $skillName should exist")
            assertTrue(File(skillDir, "SKILL.md").exists(), "SKILL.md should exist in $skillName")
        }

        // Verify some references
        assertTrue(File(targetDir, "gradle/references/background_monitoring.md").exists())
        assertTrue(File(targetDir, "gradle/references/query_build_diagnostics.md").exists())
    }

    @Test
    fun `install_gradle_skills skips existing skills by default if NOT authored by this server`() = runTest {
        val targetDir = tempDir.resolve("skip_test").toFile()
        targetDir.mkdirs()

        val skillDir = File(targetDir, "gradle")
        skillDir.mkdirs()
        val skillFile = File(skillDir, "SKILL.md")
        skillFile.writeText("EXISTING CONTENT FROM OTHER AUTHOR")

        val args = mapOf("directory" to JsonPrimitive(targetDir.absolutePath))
        val call = server.client.callTool(ToolNames.INSTALL_GRADLE_SKILLS, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(text.contains("Skipped"), "Output should mention skipped skills")
        assertTrue(text.contains("- gradle"), "Should skip gradle")

        assertTrue(skillFile.exists())
        assertTrue(skillFile.readText() == "EXISTING CONTENT FROM OTHER AUTHOR", "Content should not be overwritten")
    }

    @Test
    fun `install_gradle_skills replaces existing skills by default if authored by this server`() = runTest {
        val targetDir = tempDir.resolve("replace_test").toFile()
        targetDir.mkdirs()

        val skillDir = File(targetDir, "gradle")
        skillDir.mkdirs()
        val skillFile = File(skillDir, "SKILL.md")
        skillFile.writeText("author: https://github.com/rnett/gradle-mcp")

        val args = mapOf("directory" to JsonPrimitive(targetDir.absolutePath))
        val call = server.client.callTool(ToolNames.INSTALL_GRADLE_SKILLS, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(text.contains("Successfully installed"), "Output should mention installed skills")
        assertTrue(text.contains("- gradle"), "Should install gradle")

        assertTrue(skillFile.exists())
        assertTrue(skillFile.readText() != "author: https://github.com/rnett/gradle-mcp", "Content should be overwritten")
    }

    @Test
    fun `install_gradle_skills skips everything with replaceOld=false`() = runTest {
        val targetDir = tempDir.resolve("no_replace_test").toFile()
        targetDir.mkdirs()

        val skillDir = File(targetDir, "gradle")
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
        assertTrue(text.contains("- gradle"), "Should skip gradle")

        assertTrue(skillFile.exists())
        assertTrue(skillFile.readText() == "author: https://github.com/rnett/gradle-mcp", "Content should not be overwritten")
    }
}
