package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.FailureId
import dev.rnett.gradle.mcp.gradle.GradleBuildScan
import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemId
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import dev.rnett.gradle.mcp.mapToSet
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
@Description("A summary of the results of a Gradle build. More details can be obtained by using `lookup_build_*` tools or a Develocity Build Scan. Prefer build scans when possible.")
data class BuildResultSummary(
    val id: BuildId,
    @Description("The console output, if it was small enough. If it was too large, this field will be null and the output will be available via `lookup_build_console_output`.")
    val consoleOutput: String?,
    val publishedScans: List<GradleBuildScan>,
    val wasSuccessful: Boolean?,
    val testsRan: Int,
    val testsFailed: Int,
    @Description("Summaries of all failures encountered during the build. Does not include test failures. Details can be looked up using the `lookup_build_failure_details` tool.")
    val failureSummaries: List<FailureSummary>,
    @Description("A summary of all problems encountered during the build. The keys of the maps/objects are the problem IDs. More information can be looked up with the `lookup_build_problem_details` tool. Note that not all failures have coresponding problems.")
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
    consoleOutput.takeIf { it.length < 10_000 },
    publishedScans,
    isSuccessful,
    testResults.totalCount,
    testResults.failed.size,
    allBuildFailures.values.map { it.toSummary() },
    problems.toSummary()

)

fun Map<ProblemSeverity, List<ProblemAggregation>>.toSummary(): ProblemsSummary {
    fun counts(severity: ProblemSeverity) = this[severity].orEmpty().associate { it.definition.id to ProblemsSummary.ProblemSummary(it.definition.displayName, it.numberOfOccurrences) }
    return ProblemsSummary(
        errorCounts = counts(ProblemSeverity.ERROR),
        warningCounts = counts(ProblemSeverity.WARNING),
        adviceCounts = counts(ProblemSeverity.ADVICE),
        otherCounts = counts(ProblemSeverity.OTHER),
    )
}

fun BuildResult.TestResults.toSummary(maxResults: Int?) = TestResultsSummary(
    failed = failed.map { it.testName },
    skipped = skipped.map { it.testName },
    totalPassed = passed.size,
    totalFailed = failed.size,
    totalSkipped = skipped.size,
).truncate(maxResults)

@Serializable
data class TestResultsSummary(
    val totalPassed: Int,
    val totalFailed: Int,
    val totalSkipped: Int,
    val failed: List<String>?,
    val skipped: List<String>?,
) {

    fun truncate(maxResults: Int?): TestResultsSummary {
        if (maxResults == null) return this
        var taken = 0

        val failed = this.failed?.take(maxResults - taken)?.also { taken += it.size }?.takeIf { it.size == this.failed.size }
            ?: return TestResultsSummary(totalPassed = totalPassed, totalFailed = totalFailed, totalSkipped = totalSkipped, failed = null, skipped = null)

        val skipped = this.skipped?.take(maxResults - taken)?.also { taken += it.size }?.takeIf { it.size == this.skipped.size }
            ?: return TestResultsSummary(totalPassed = totalPassed, totalFailed = totalFailed, totalSkipped = totalSkipped, failed = failed, skipped = null)

        return TestResultsSummary(totalPassed = totalPassed, totalFailed = totalFailed, totalSkipped = totalSkipped, failed = failed, skipped = skipped)
    }

    @Description("Whether the results were truncated. If true, use a lookup tool to get more detailed results.")
    val wasTruncated: Boolean = failed == null || skipped == null

    val total: Int = totalPassed + totalFailed + totalSkipped
}

@Serializable
data class ProblemsSummary(
    val errorCounts: Map<ProblemId, ProblemSummary>,
    val warningCounts: Map<ProblemId, ProblemSummary>,
    val adviceCounts: Map<ProblemId, ProblemSummary>,
    val otherCounts: Map<ProblemId, ProblemSummary>,
) {
    @Serializable
    data class ProblemSummary(
        val displayName: String?,
        val occurences: Int
    )
}