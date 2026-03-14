package dev.rnett.gradle.mcp.tools.skills

import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.tools.ToolNames
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

class SkillTools : McpServerComponent(
    "Skill Tools",
    "Tools for managing Gradle MCP skills."
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(SkillTools::class.java)
    }

    @Serializable
    data class InstallSkillsArgs(
        @Description("Providing the absolute path to the authoritative skill installation directory.")
        val directory: String,
        @Description("Setting to true replaces existing skills in the target directory that were authored by this MCP server.")
        val replaceOld: Boolean = true
    )

    val installSkills by tool<InstallSkillsArgs, String>(
        ToolNames.INSTALL_GRADLE_SKILLS,
        """
            |ALWAYS use this tool to install or update the official Gradle MCP skills into your agent's skill directory.
            |These skills provide expert-level workflows, specialized instructions, and deep diagnostic patterns that are essential for mastering Gradle tasks.
            |
            |### Authoritative Installation
            |
            |1.  **Target Directory**: Provide the absolute path to your agent's skill directory (e.g., `~/.agents/skills`).
            |2.  **Unpack & Configure**: The tool automatically extracts the latest skill definitions (`SKILL.md` and associated references) into the target directory.
            |
            |### Upgrade Protocols
            |
            |1.  **Surgical Replacement**: Set `replaceOld=true` (default) to replace existing skills authored by this MCP server. This ensures you always have the latest expert guidance.
            |2.  **Persistence**: The tool maintains a clean installation by removing old skill versions before unpacking the new ones.
            |
            |### Post-Installation
            |Once installed, the skills become available to the agent for specialized tasks like `researching_gradle_internals`, `running_gradle_tests`, and `managing_gradle_dependencies`.
        """.trimMargin()
    ) { args ->
        val targetDir = File(args.directory)
        if (!targetDir.exists()) {
            if (!targetDir.mkdirs()) {
                throw IllegalStateException("Failed to create target directory: ${args.directory}")
            }
        }

        if (args.replaceOld) {
            // Remove existing skills from this repo to ensure clean update
            val authorUrl = "https://github.com/rnett/gradle-mcp"
            targetDir.listFiles { file -> file.isDirectory }?.forEach { skillDir ->
                val skillFile = File(skillDir, "SKILL.md")
                if (skillFile.exists()) {
                    val content = skillFile.readText()
                    if (content.contains("author: $authorUrl") ||
                        content.contains("author: \"$authorUrl\"") ||
                        content.contains("author: '$authorUrl'")
                    ) {
                        skillDir.deleteRecursively()
                    }
                }
            }
        }

        val classLoader = this::class.java.classLoader

        val installedSkills = mutableSetOf<String>()
        val skippedSkills = mutableSetOf<String>()

        classLoader.getResourceAsStream("skills.zip")?.use { zipStream ->
            ZipInputStream(zipStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val entryName = entry.name
                        val skillName = entryName.substringBefore("/")

                        val skillDir = File(targetDir, skillName)
                        val exists = skillDir.exists()

                        if (!exists || (installedSkills.contains(skillName) && !skippedSkills.contains(skillName))) {
                            val targetFile = File(targetDir, entryName)
                            targetFile.parentFile.mkdirs()
                            Files.copy(zis, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                            installedSkills.add(skillName)
                        } else {
                            skippedSkills.add(skillName)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        } ?: error("skills.zip resource not found")

        buildString {
            if (installedSkills.isNotEmpty()) {
                appendLine("Successfully installed ${installedSkills.size} skills to ${targetDir.absolutePath}:")
                installedSkills.sorted().forEach {
                    appendLine("- $it")
                }
            }
            if (skippedSkills.isNotEmpty()) {
                if (installedSkills.isNotEmpty()) appendLine()
                appendLine("Skipped ${skippedSkills.size} existing skills (use replaceOld=true to replace skills from this server):")
                skippedSkills.sorted().forEach {
                    appendLine("- $it")
                }
            }
        }
    }
}
