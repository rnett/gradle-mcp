package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.tools.GradleInvocationArguments
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
@Description("The result of a Gradle build.")
sealed interface BuildResult {

    val output: String
    val publishedScans: List<GradleBuildScan>


    @Description("The result of a successful Gradle build.")
    @SerialName("success")
    @Serializable
    data class Success(
        @Description("The combined stdout and stderr output of the build.")
        override val output: String,
        override val publishedScans: List<GradleBuildScan>
    ) : BuildResult

    @SerialName("failure")
    @Description("The result of a failed Gradle build.")
    @Serializable
    data class BuildFailure(
        @Description("The combined stdout and stderr output of the build.")
        override val output: String,
        @Description("The failures that failed the build. Optional, may be empty if failure information was not requested, even if there were failures.")
        val failures: List<Failure>,
        override val publishedScans: List<GradleBuildScan>
    ) : BuildResult

    @Description("A failure encountered during a Gradle build.")
    @Serializable
    data class Failure(
        @Description("A short message describing the failure")
        val message: String?,
        @Description("A longer description of the details of the failure")
        val description: String?,
        @Description("Other failures that caused this failure")
        val causes: List<Failure>,
        @Description("Problems in the build that caused this failure")
        val problems: List<Problem>,
    )
}

fun org.gradle.tooling.Failure.toModel(): BuildResult.Failure {
    return BuildResult.Failure(
        message,
        description,
        causes.map { it.toModel() },
        problems.map { it.toModel() }
    )
}

suspend fun GradleProvider.runBuildAndGetOutput(
    projectRoot: String,
    invocationArgs: GradleInvocationArguments,
    includeFailures: Boolean,
    tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
    stdoutLineHandler: ((String) -> Unit)? = null,
    stderrLineHandler: ((String) -> Unit)? = null,
): BuildResult {
    val result = StringBuilder()
    val r = runBuild(
        projectRoot,
        invocationArgs,
        tosAccepter,
        stdoutLineHandler = {
            result.appendLine(it)
            stdoutLineHandler?.invoke(it)
        },
        stderrLineHandler = {
            result.appendLine("ERR:  $it")
            stderrLineHandler?.invoke(it)
        }
    )

    return when (r) {
        is GradleResult.Failure -> BuildResult.BuildFailure(result.toString(), if (includeFailures) r.error.failures.map { it.toModel() } else emptyList(), r.publishedScans)
        is GradleResult.Success -> BuildResult.Success(result.toString(), r.publishedScans)
    }
}