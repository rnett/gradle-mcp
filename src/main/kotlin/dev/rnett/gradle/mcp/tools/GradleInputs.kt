package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.expandPath
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.mcp.McpContext
import dev.rnett.gradle.mcp.mcp.McpServer
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.modelcontextprotocol.kotlin.sdk.Root
import kotlinx.serialization.Serializable
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.toPath


@JvmInline
@Serializable
@Description("The file system path of the Gradle project's root directory (containing gradlew script and settings.gradle). Providing this ensures the tool executes in the correct project context and avoids ambiguities in multi-root or environment-dependent workspaces. If omitted, the tool will attempt to auto-detect the root from the current MCP roots or the GRADLE_MCP_PROJECT_ROOT environment variable. **It MUST be an absolute path.**")
value class GradleProjectRootInput(val projectRoot: String? = null) {
    companion object {
        val DEFAULT = GradleProjectRootInput(null)
    }
}

val Root.fileOrNull: Path?
    get() {
        return try {
            URI.create(uri).toPath().absolute()
        } catch (e: IllegalArgumentException) {
            return null
        }
    }

val Root.nameOrUrl get() = name ?: uri

context(ctx: McpContext)
fun GradleProjectRootInput.resolve(): GradleProjectRoot = with(ctx.server) { resolveRoot() }

context(server: McpServer)
fun GradleProjectRootInput.resolveRoot(): GradleProjectRoot {
    val roots = server.roots.value
    if (projectRoot == null) {
        if (roots?.size == 1) {
            val file = roots.single().fileOrNull
                ?: throw IllegalArgumentException("Configured root ${roots.single().nameOrUrl} could not be converted to a file")
            return GradleProjectRoot(file.absolutePathString())
        }

        val envRoot = System.getenv("GRADLE_MCP_PROJECT_ROOT")
        if (envRoot != null) {
            return GradleProjectRoot(envRoot.expandPath())
        }

        if (roots == null || roots.isEmpty()) {
            throw IllegalArgumentException("No MCP roots configured - you must specify a Gradle project root")
        } else {
            throw IllegalArgumentException("Multiple MCP roots configured - you must specify a Gradle project root")
        }
    }

    val expandedProjectRoot = projectRoot.expandPath()

    return if (roots != null) {
        val named = roots.firstOrNull { it.name == projectRoot }
        if (named != null)
            return GradleProjectRoot(
                named.fileOrNull?.absolutePathString()
                    ?: throw IllegalArgumentException("Configured root ${named.nameOrUrl} could not be converted to a file")
            )

        val rootFile = Path.of(expandedProjectRoot).absolute()

        val isInRoot = roots.any {
            val file = it.fileOrNull ?: return@any false
            rootFile.startsWith(file)
        }

        if (!isInRoot) {
            throw IllegalArgumentException("Gradle project root $expandedProjectRoot is not in any of the configured MCP roots")
        }

        GradleProjectRoot(expandedProjectRoot)
    } else {
        GradleProjectRoot(expandedProjectRoot)
    }
}