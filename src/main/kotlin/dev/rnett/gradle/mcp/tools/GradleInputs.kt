package dev.rnett.gradle.mcp.tools

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
@Description("The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it.")
value class GradleProjectRootInput(val projectRoot: String?) {
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
        return if (roots?.size == 1) {
            val file = roots.single().fileOrNull ?: throw IllegalArgumentException("Configured root ${roots.single().nameOrUrl} could not be converted to a file")
            GradleProjectRoot(file.absolutePathString())
        } else if (roots == null || roots.isEmpty()) {
            throw IllegalArgumentException("No MCP roots configured - you must specify a Gradle project root")
        } else {
            throw IllegalArgumentException("Multiple MCP roots configured - you must specify a Gradle project root")
        }
    }

    return if (roots != null) {
        val named = roots.firstOrNull { it.name == projectRoot }
        if (named != null)
            return GradleProjectRoot(named.fileOrNull?.absolutePathString() ?: throw IllegalArgumentException("Configured root ${named.nameOrUrl} could not be converted to a file"))

        val rootFile = Path.of(projectRoot).absolute()

        val isInRoot = roots.any {
            val file = it.fileOrNull ?: return@any false
            rootFile.startsWith(file)
        }

        if (!isInRoot) {
            throw IllegalArgumentException("Gradle project root $projectRoot is not not in any of the configured MCP roots")
        }

        GradleProjectRoot(projectRoot)
    } else {
        GradleProjectRoot(projectRoot)
    }
}