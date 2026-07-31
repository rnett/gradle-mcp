package dev.rnett.gradle.mcp.tools.skills

import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.tools.ToolNames
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillToolsTest : BaseMcpServerTest() {

    private val expectedInventory = setOf(
        "using-gradle",
        "authoring-gradle-builds",
        "interacting-with-project-runtime",
        "verifying-compose-ui"
    )

    @Test
    fun `install_gradle_skills installs skills to directory`() = runTest {
        val targetDir = tempDir.resolve("installed_skills").toFile()
        val args = mapOf("directory" to JsonPrimitive(targetDir.absolutePath))

        val call = server.client.callTool(ToolNames.INSTALL_GRADLE_SKILLS, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(call.isError != true, "Call should not be an error, but was: $text")
        assertTrue(text.contains("Successfully installed"), "Output should contain success message")
        assertTrue(text.contains("- using-gradle"), "Output should list installed skills")

        // Verify files exist — exact four-name inventory
        val skills = listOf(
            "using-gradle",
            "authoring-gradle-builds",
            "interacting-with-project-runtime",
            "verifying-compose-ui"
        )

        skills.forEach { skillName ->
            val skillDir = File(targetDir, skillName)
            assertTrue(skillDir.exists(), "Skill directory $skillName should exist")
            assertTrue(File(skillDir, "SKILL.md").exists(), "SKILL.md should exist in $skillName")
        }

        // Installed directory set must exactly equal the four-name inventory
        val installedDirs = targetDir.listFiles { file -> file.isDirectory }!!.map { it.name }.toSet()
        assertEquals(expectedInventory, installedDirs, "Installed skills must exactly equal the four-name inventory")

        // Verify some references
        assertTrue(File(targetDir, "using-gradle/references/running-builds.md").exists())
        assertTrue(File(targetDir, "using-gradle/references/build-diagnostics.md").exists())
        assertTrue(File(targetDir, "authoring-gradle-builds/references/dependency-declaration.md").exists())
    }

    @Test
    fun `skills zip contains exactly the four-name inventory`() {
        val zipSkills = mutableSetOf<String>()
        javaClass.classLoader.getResourceAsStream("skills.zip")!!.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    zipSkills.add(entry.name.substringBefore("/"))
                    entry = zis.nextEntry
                }
            }
        }

        assertEquals(expectedInventory, zipSkills, "skills.zip entries must exactly equal the four-name inventory")
    }

    @Test
    fun `install_gradle_skills skips existing skills by default if NOT authored by this server`() = runTest {
        val targetDir = tempDir.resolve("skip_test").toFile()
        targetDir.mkdirs()

        val skillDir = File(targetDir, "using-gradle")
        skillDir.mkdirs()
        val skillFile = File(skillDir, "SKILL.md")
        skillFile.writeText("EXISTING CONTENT FROM OTHER AUTHOR")

        val args = mapOf("directory" to JsonPrimitive(targetDir.absolutePath))
        val call = server.client.callTool(ToolNames.INSTALL_GRADLE_SKILLS, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(text.contains("Skipped"), "Output should mention skipped skills")
        assertTrue(text.contains("- using-gradle"), "Should skip using-gradle")

        assertTrue(skillFile.exists())
        assertTrue(skillFile.readText() == "EXISTING CONTENT FROM OTHER AUTHOR", "Content should not be overwritten")
    }

    @Test
    fun `install_gradle_skills replaces existing skills by default if authored by this server`() = runTest {
        val targetDir = tempDir.resolve("replace_test").toFile()
        targetDir.mkdirs()

        val skillDir = File(targetDir, "using-gradle")
        skillDir.mkdirs()
        val skillFile = File(skillDir, "SKILL.md")
        skillFile.writeText("author: https://github.com/rnett/gradle-mcp")

        val args = mapOf("directory" to JsonPrimitive(targetDir.absolutePath))
        val call = server.client.callTool(ToolNames.INSTALL_GRADLE_SKILLS, args)

        val text = call!!.content.filterIsInstance<TextContent>().joinToString { it.text ?: "" }
        assertTrue(text.contains("Successfully installed"), "Output should mention installed skills")
        assertTrue(text.contains("- using-gradle"), "Should install using-gradle")

        assertTrue(skillFile.exists())
        assertTrue(skillFile.readText() != "author: https://github.com/rnett/gradle-mcp", "Content should be overwritten")
    }

    @Test
    fun `install_gradle_skills skips everything with replaceOld=false`() = runTest {
        val targetDir = tempDir.resolve("no_replace_test").toFile()
        targetDir.mkdirs()

        val skillDir = File(targetDir, "using-gradle")
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
        assertTrue(text.contains("- using-gradle"), "Should skip using-gradle")

        assertTrue(skillFile.exists())
        assertTrue(skillFile.readText() == "author: https://github.com/rnett/gradle-mcp", "Content should not be overwritten")
    }
}
