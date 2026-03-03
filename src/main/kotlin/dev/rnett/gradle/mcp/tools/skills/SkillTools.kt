package dev.rnett.gradle.mcp.tools.skills

import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.tools.ToolNames
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

class SkillTools : McpServerComponent(
    "Skill Tools",
    "Tools for managing Gradle MCP skills."
) {

    @Serializable
    data class InstallSkillsArgs(
        @Description("The directory to install the skills to. This should be a directory where your calling agent can find and use skills (e.g. its skills directory).")
        val directory: String,
        @Description("If true, replaces existing skills in the target directory. If false (default), skips skills that already exist.")
        val force: Boolean = false
    )

    val installSkills by tool<InstallSkillsArgs, String>(
        ToolNames.INSTALL_GRADLE_SKILLS,
        """
            |Installs a set of skills for working with Gradle into the specified directory.
            |
            |These skills provide comprehensive guidance and best practices for various Gradle-related tasks, including:
            |- Running and troubleshooting builds
            |- Build authoring and optimization
            |- Dependency management and troubleshooting
            |- Project introspection and structure analysis
            |- Effective use of Gradle documentation
            |- Running and debugging tests
            |- Using the Gradle REPL for interactive development, debugging, and testing
            |
            |Installing these skills allows your agent to access structured knowledge and specialized instructions to perform Gradle tasks more effectively and follow best practices.
            |You should pass the directory where you want the skills to be installed, typically your own skills or documentation directory.
            |
            |Use the `force` option to replace existing skills with the ones from this tool.
        """.trimMargin()
    ) { args ->
        val targetDir = File(args.directory)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
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

                        if (args.force || !exists || (installedSkills.contains(skillName) && !skippedSkills.contains(skillName))) {
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
                appendLine("Skipped ${skippedSkills.size} existing skills (use force=true to replace):")
                skippedSkills.sorted().forEach {
                    appendLine("- $it")
                }
            }
        }
    }
}
