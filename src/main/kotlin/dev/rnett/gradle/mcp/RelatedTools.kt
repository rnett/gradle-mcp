package dev.rnett.gradle.mcp

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import dev.rnett.gradle.mcp.mcp.McpFactory
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.relativeTo

@Service
class RelatedTools(val toolFactory: McpFactory) {
    @Serializable
    data class ContainingProject(
        @JsonPropertyDescription("The file system path of the Gradle project's root")
        val projectRootPath: String,
        @JsonPropertyDescription("Gradle project path of the project containing the file, e.g. ':project-a'")
        val projectPath: String
    )

    @Serializable
    data class GetContainingProjectArgs(@Description(description = "The target file's path. Must be absolute.") val path: String)

    @Bean
    fun getContainingProjectTool() = toolFactory.tool<GetContainingProjectArgs, ContainingProject>(
        "get_gradle_project_containing_file",
        "Gets the nearest Gradle project containing the given file if there is one."
    ) {
        var current = Path(it.path)
        if (current.notExists())
            throw IllegalArgumentException("The target file does not exist")

        var project: Path? = null
        var root: Path? = null

        while (root == null) {
            if (isGradleProjectDir(current) && project == null)
                project = current
            if (isGradleRootProjectDir(current) && root == null)
                root = current
            current = current.parent ?: throw IllegalArgumentException("The target file is not in a Gradle project")
        }

        if (project == null) {
            return@tool ContainingProject(root.absolutePathString(), ":")
        } else {
            val projectPath = project.relativeTo(root).joinToString(":", prefix = ":") { it.name }
            return@tool ContainingProject(root.absolutePathString(), projectPath)
        }
    }

    private fun isGradleProjectDir(path: Path): Boolean {
        if (path.notExists() || !path.isDirectory())
            return false

        val dirName = path.name

        return path.listDirectoryEntries("*.gradle*").any {
            it.isRegularFile() && (it.name.startsWith("build.gradle") || it.name.startsWith("$dirName.gradle"))
        }
    }

    private fun isGradleRootProjectDir(path: Path): Boolean {
        if (path.notExists() || !path.isDirectory())
            return false

        return path.listDirectoryEntries("gradlew*").any {
            it.isRegularFile() && (it.name == "gradlew" || it.name == "gradlew.bat")
        }
    }
}