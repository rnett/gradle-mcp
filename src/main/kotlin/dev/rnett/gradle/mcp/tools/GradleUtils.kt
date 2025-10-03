package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.GradleResult
import dev.rnett.gradle.mcp.gradle.GradleScanTosAcceptRequest
import dev.rnett.gradle.mcp.mcp.McpContext
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Example
import io.modelcontextprotocol.kotlin.sdk.LoggingLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.ProjectIdentifier
import kotlin.io.path.absolute

fun ProjectIdentifier.matches(root: GradleProjectRoot, projectPath: GradleProjectPath): Boolean =
    buildIdentifier.rootDir.toPath().absolute() == GradlePathUtils.getRootProjectPath(root) && this.projectPath == projectPath.path

@Serializable
@Description("Additional arguments to configure the Gradle process.")
data class GradleInvocationArguments(
    @Description("Additional environment variables to set for the Gradle process. Optional.")
    val additionalEnvVars: Map<String, String> = emptyMap(),
    @Description("Additional system properties to set for the Gradle process. Optional.")
    val additionalSystemProps: Map<String, String> = emptyMap(),
    @Description("Additional JVM arguments to set for the Gradle process. Optional.")
    val additionalJvmArgs: List<String> = emptyList(),
    @Description("Additional arguments for the Gradle process. Optional.")
    val additionalArguments: List<String> = emptyList(),
    @Description("Whether to attempt to publish a Develocity Build Scan by using the '--scan' argument. Optional, defaults to false. Using Build Scans is the best way to investigate failures, especially if you have access to the Develocity MCP server. Publishing build scans to scans.gradle.com requires the MCP client to support elicitation.")
    val publishScan: Boolean = false,
) {
    operator fun plus(other: GradleInvocationArguments): GradleInvocationArguments {
        return GradleInvocationArguments(
            additionalEnvVars = additionalEnvVars + other.additionalEnvVars,
            additionalSystemProps = additionalSystemProps + other.additionalSystemProps,
            additionalJvmArgs = additionalJvmArgs + other.additionalJvmArgs,
            additionalArguments = additionalArguments + other.additionalArguments,
            publishScan = publishScan || other.publishScan
        )
    }

    @Transient
    val allAdditionalArguments = additionalArguments + (if (publishScan && "--scan" !in additionalArguments) listOf("--scan") else emptyList())

    companion object {
        val DEFAULT = GradleInvocationArguments()
    }
}

@JvmInline
@Serializable
@Description("The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.  The MCP server will do its best to convert the path to the path inside the docker container, but if you can provide the path as the MCP server would see it, that's ideal.")
value class GradleProjectRoot(val projectRoot: String)

@JvmInline
@Serializable
@Description("The Gradle project path, e.g. :project-a:subproject-b. ':' is the root project.  Defaults to ':'")
@Example(":")
@Example(":my-project")
@Example(":my-project:subproject")
value class GradleProjectPath(private val projectPath: String) {
    companion object {
        val DEFAULT = GradleProjectPath(":")
    }

    val path get() = ':' + projectPath.trim(':')

    val isRootProject get() = projectPath.isBlank() || projectPath == ":"

    fun taskPath(task: String): String = buildString {
        append(path)
        if (!isRootProject)
            append(':')
        append(task.trimStart(':'))
    }

    override fun toString(): String {
        return path
    }
}

//TODO have a "remember acceptance" option?
@PublishedApi
context(ctx: McpContext)
internal suspend fun askForScansTos(tosAcceptRequest: GradleScanTosAcceptRequest): Boolean {
    return ctx.elicitUnit(tosAcceptRequest.fullMessage, GradleScanTosAcceptRequest.TIMEOUT).isAccepted
}

context(ctx: McpContext)
suspend inline fun <reified T : Model> GradleProvider.getBuildModel(
    projectRoot: GradleProjectRoot,
    invocationArgs: GradleInvocationArguments,
    requiresGradleProject: Boolean = true
): GradleResult<T> {
    return getBuildModel(
        projectRoot,
        T::class,
        invocationArgs,
        { askForScansTos(it) },
        stdoutLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.notice, it)
            ctx.emitProgressNotification(0.0, 0.0, it)
        },
        stderrLineHandler = { ctx.emitLoggingNotification("gradle-build", LoggingLevel.error, it) },
        requiresGradleProject = requiresGradleProject
    )
}


context(ctx: McpContext)
suspend inline fun GradleProvider.doBuild(
    projectRoot: GradleProjectRoot,
    invocationArgs: GradleInvocationArguments,
): BuildResult {
    return runBuild(
        projectRoot,
        invocationArgs,
        { askForScansTos(it) },
        stdoutLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.notice, it)
            ctx.emitProgressNotification(0.0, 0.0, it)
        },
        stderrLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.error, it)
        }
    ).buildResult
}

context(ctx: McpContext)
suspend inline fun GradleProvider.doTests(
    projectRoot: GradleProjectRoot,
    testPatterns: Map<String, Set<String>>,
    invocationArgs: GradleInvocationArguments,
): BuildResult {
    return runTests(
        projectRoot,
        testPatterns,
        invocationArgs,
        { askForScansTos(it) },
        stdoutLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.notice, it)
            ctx.emitProgressNotification(0.0, 0.0, it)
        },
        stderrLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.error, it)
        }
    ).buildResult
}