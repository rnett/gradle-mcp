package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.mcp.McpServer
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.ktor.server.application.Application
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.relativeTo

class RelatedTools() : McpServerContributor {

    context(application: Application)
    override fun contribute(server: McpServer) {
        server.addTool<GetContainingProjectArgs, ContainingProject>(
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
                return@addTool ContainingProject(root.absolutePathString(), ":")
            } else {
                val projectPath = project.relativeTo(root).joinToString(":", prefix = ":") { it.name }
                return@addTool ContainingProject(root.absolutePathString(), projectPath)
            }
        }

        server.addTool<DocsArgs, String>(
            "get_gradle_docs_link",
            "Get a link to the Gradle documentation for the passed version or the latest if no version is passed"
        ) {
            if (it.version == "current") return@addTool docsUrl("current")

            val parts = it.version.trimStart('v').split(".")
            if (parts.size < 2 || parts.size > 3) {
                throw IllegalArgumentException("Expected a Gradle version with 2 or 3 dot-seperated numbers")
            }

            if (parts.any { it.toIntOrNull() == null }) {
                throw IllegalArgumentException("Gradle version must be 2 or 3 dot-seperated numbers")
            }

            docsUrl(it.version.trimStart('v'))
        }
    }

    @Serializable
    data class ContainingProject(
        @Description("The file system path of the Gradle project's root")
        val projectRootPath: String,
        @Description("Gradle project path of the project containing the file, e.g. ':project-a'")
        val projectPath: String
    )

    @Serializable
    data class GetContainingProjectArgs(@Description(description = "The target file's path. Must be absolute.") val path: String)

    @Serializable
    data class DocsArgs(@Description("The Gradle version to get documentation for. Uses the latest by default. Should be a semver-like version with 2 or 3 numbers.") val version: String = "current")

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

    private fun docsUrl(version: String) = "https://docs.gradle.org/$version/userguide/userguide.html"
}