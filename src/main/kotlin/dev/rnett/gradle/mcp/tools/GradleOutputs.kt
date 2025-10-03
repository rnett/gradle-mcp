package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.FailureId
import dev.rnett.gradle.mcp.gradle.GradleBuildScan
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import dev.rnett.gradle.mcp.mapToSet
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
@Description("A summary of the results of a Gradle build. More details can be obtained by using `lookup_build_*` tools or a Develocity Build Scan. Prefer build scans when possible.")
data class BuildResultSummary(
    val id: BuildId,
    val consoltOutput: String,
    val publishedScans: List<GradleBuildScan>,
    val wasSuccessful: Boolean?,
    val testsRan: Int,
    val testsFailed: Int,
    @Description("Summaries of all failures encountered during the build. Does not include test failures. Details can be looked up using the `lookup_build_failure_details` tool.")
    val failureSummaries: List<FailureSummary>,
    @Description("A summary of all problems encountered during the build. More information can be looked up with the `lookup_build_problems_summary` tool.")
    val problemsSummary: ProblemsSummary,
) {

    @Serializable
    @Description("A summary of a single failure. Details can be looked up using the `lookup_build_failure_details` tool.")
    data class FailureSummary(
        val id: FailureId,
        @Description("A short description of the failure.")
        val message: String?,
        @Description("A description of the failure, with more details.")
        val description: String?,
        @Description("A set of IDs of the causes of this failure.")
        val causes: Set<FailureId>
    )
}

fun BuildResult.Failure.toSummary(): BuildResultSummary.FailureSummary = BuildResultSummary.FailureSummary(
    id = id,
    message = message,
    description = description,
    causes = causes.mapToSet { it.id }
)

fun BuildResult.toSummary() = BuildResultSummary(
    id,
    consoleOutput,
    publishedScans,
    isSuccessful,
    testResults?.totalCount ?: 0,
    testResults?.failed?.size ?: 0,
    allBuildFailures.values.map { it.toSummary() },
    ProblemsSummary(
        errorsCount = problems[ProblemSeverity.ERROR]?.size ?: 0,
        warningsCount = problems[ProblemSeverity.WARNING]?.size ?: 0,
        advicesCount = problems[ProblemSeverity.ADVICE]?.size ?: 0,
        othersCount = problems[ProblemSeverity.OTHER]?.size ?: 0,
    )
)

fun BuildResult.TestResults.toSummary() = TestResultsSummary(
    passed.map { it.testName },
    failed.map { it.testName },
    skipped.map { it.testName }
)

@Serializable
data class TestResultsSummary(
    val passed: List<String>,
    val failed: List<String>,
    val skipped: List<String>
) {

    val totalPassed: Int = passed.size
    val totalFailed: Int = failed.size
    val totalSkipped: Int = skipped.size
    val total: Int = totalPassed + totalFailed + totalSkipped
}

@Serializable
data class ProblemsSummary(
    val errorsCount: Int,
    val warningsCount: Int,
    val advicesCount: Int,
    val othersCount: Int,
)