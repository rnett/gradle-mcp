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
        @Description("The absolute path to the directory where the skills should be installed. This should be a directory that your calling agent is configured to search for skills (e.g., its local 'skills' or 'documentation' directory).")
        val directory: String,
        @Description("If true, authoritatively replaces any existing skills in the target directory. If false (default), existing skills are preserved to avoid accidental overrides.")
        val force: Boolean = false
    )

    val installSkills by tool<InstallSkillsArgs, String>(
        ToolNames.INSTALL_GRADLE_SKILLS,
        """
            |The authoritative tool for installing and managing Gradle MCP skills in your local environment.
            |These skills provide expert-level guidance, structured workflows, and high-signal instructions for interacting with Gradle effectively.
            |
            |### Authoritative Features
            |- **Expert Workflow Integration**: Skills provide specialized instructions for core tasks like background build monitoring, surgical test diagnostics, and deep-dive source exploration.
            |- **Managed Installation**: Automatically unpacks and configures the latest authoritative skills into your agent's searchable skills directory.
            |- **Safe and Forceful Updates**: Supports both safe (non-overwriting) and authoritative (force=true) installation modes to ensure your environment is always up to date.
            |
            |### Core Skills Included
            |- **gradle-build**: High-performance background execution and failure analysis.
            |- **gradle-test**: Surgical test selection and detailed failure isolation.
            |- **gradle-dependencies**: Authoritative dependency graph auditing and update detection.
            |- **gradle-introspection**: Deep-dive project structure and environment mapping.
            |- **gradle-docs**: High-speed search and retrieval of official Gradle documentation.
            |- **gradle-library-sources**: Navigation and indexing of dependency source code.
            |- **gradle-repl**: Interactive Kotlin prototyping with full project context.
            |
            |### Common Usage Patterns
            |- **Initial Setup**: `install_gradle_skills(directory="/path/to/my/agent/skills")`
            |- **Authoritative Update**: `install_gradle_skills(directory="/path/to/my/agent/skills", force=true)`
            |
            |Installing these skills is the STRONGLY PREFERRED first step for any agent wishing to perform high-quality, professional Gradle engineering.
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
