package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.GradleResult
import dev.rnett.gradle.mcp.mcp.McpContext
import io.modelcontextprotocol.kotlin.sdk.LoggingLevel
import org.gradle.tooling.model.Model


context(ctx: McpContext)
suspend inline fun <reified T : Model> GradleProvider.getBuildModel(
    projectRoot: GradleProjectRootInput,
    invocationArgs: GradleInvocationArguments,
    requiresGradleProject: Boolean = true
): GradleResult<T> {
    val root = projectRoot.resolve()
    return getBuildModel(
        root,
        T::class,
        invocationArgs,
        { ScansTosManager.askForScansTos(root, it) },
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
    projectRoot: GradleProjectRootInput,
    invocationArgs: GradleInvocationArguments,
): BuildResult {
    val root = projectRoot.resolve()
    return runBuild(
        root,
        invocationArgs,
        { ScansTosManager.askForScansTos(root, it) },
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
    projectRoot: GradleProjectRootInput,
    testPatterns: Map<String, Set<String>>,
    invocationArgs: GradleInvocationArguments,
): BuildResult {
    val root = projectRoot.resolve()
    return runTests(
        root,
        testPatterns,
        invocationArgs,
        { ScansTosManager.askForScansTos(root, it) },
        stdoutLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.notice, it)
            ctx.emitProgressNotification(0.0, 0.0, it)
        },
        stderrLineHandler = {
            ctx.emitLoggingNotification("gradle-build", LoggingLevel.error, it)
        }
    ).buildResult
}