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
        "verifying-compose-ui",
        "advanced-gradle-dependencies"
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

        // Verify files exist — exact five-name inventory
        val skills = listOf(
            "using-gradle",
            "authoring-gradle-builds",
            "interacting-with-project-runtime",
            "verifying-compose-ui",
            "advanced-gradle-dependencies"
        )

        skills.forEach { skillName ->
            val skillDir = File(targetDir, skillName)
            assertTrue(skillDir.exists(), "Skill directory $skillName should exist")
            assertTrue(File(skillDir, "SKILL.md").exists(), "SKILL.md should exist in $skillName")
        }

        // Installed directory set must exactly equal the five-name inventory
        val installedDirs = targetDir.listFiles { file -> file.isDirectory }!!.map { it.name }.toSet()
        assertEquals(expectedInventory, installedDirs, "Installed skills must exactly equal the five-name inventory")

        // Verify some references
        assertTrue(File(targetDir, "using-gradle/references/running-builds.md").exists())
        assertTrue(File(targetDir, "using-gradle/references/troubleshooting.md").exists())
        assertTrue(File(targetDir, "authoring-gradle-builds/references/dependencies-and-catalogs.md").exists())
        assertTrue(File(targetDir, "using-gradle/references/included-builds.md").exists())
        assertTrue(File(targetDir, "authoring-gradle-builds/references/composite-builds.md").exists())
        // Phase-1 references of the advanced-gradle-dependencies skill
        assertTrue(File(targetDir, "advanced-gradle-dependencies/references/variant-resolution-diagnostics.md").exists())
        assertTrue(File(targetDir, "advanced-gradle-dependencies/references/dependency-verification.md").exists())
        assertTrue(File(targetDir, "advanced-gradle-dependencies/references/component-metadata-rules.md").exists())
        assertTrue(File(targetDir, "advanced-gradle-dependencies/references/substitution-and-composites.md").exists())
        // Phase-2 references of the advanced-gradle-dependencies skill
        assertTrue(File(targetDir, "advanced-gradle-dependencies/references/feature-variants-and-capabilities.md").exists())
        assertTrue(File(targetDir, "advanced-gradle-dependencies/references/dependency-locking-deep-dive.md").exists())
        assertTrue(File(targetDir, "advanced-gradle-dependencies/references/advanced-version-catalogs.md").exists())
        assertTrue(File(targetDir, "advanced-gradle-dependencies/references/repository-governance.md").exists())
        assertTrue(File(targetDir, "advanced-gradle-dependencies/references/resolution-mechanics.md").exists())
    }

    @Test
    fun `skills zip contains exactly the five-name inventory`() {
        val zipEntries = mutableSetOf<String>()
        var hasBestPracticesIndex = false
        var hasBestPracticesCorpus = false
        var hasIncludedBuildsRef = false
        var hasCompositeBuildsRef = false
        javaClass.classLoader.getResourceAsStream("skills.zip")!!.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        zipEntries.add(entry.name.substringBefore("/"))
                        if (entry.name == "authoring-gradle-builds/references/best-practices/_index.md") {
                            hasBestPracticesIndex = true
                        }
                        if (entry.name.startsWith("authoring-gradle-builds/references/best-practices/") &&
                            entry.name.endsWith(".md") &&
                            entry.name != "authoring-gradle-builds/references/best-practices/_index.md"
                        ) {
                            hasBestPracticesCorpus = true
                        }
                        if (entry.name == "using-gradle/references/included-builds.md") {
                            hasIncludedBuildsRef = true
                        }
                        if (entry.name == "authoring-gradle-builds/references/composite-builds.md") {
                            hasCompositeBuildsRef = true
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        }

        assertEquals(expectedInventory, zipEntries, "skills.zip entries must exactly equal the five-name inventory")
        assertTrue(
            hasBestPracticesIndex,
            "skills.zip must contain the generated best-practices index (authoring-gradle-builds/references/best-practices/_index.md)"
        )
        assertTrue(
            hasBestPracticesCorpus,
            "skills.zip must contain generated best-practices corpus files under authoring-gradle-builds/references/best-practices/"
        )
        assertTrue(
            hasIncludedBuildsRef,
            "skills.zip must contain using-gradle/references/included-builds.md"
        )
        assertTrue(
            hasCompositeBuildsRef,
            "skills.zip must contain authoring-gradle-builds/references/composite-builds.md"
        )
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
