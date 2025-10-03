package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildResults
import dev.rnett.gradle.mcp.gradle.FailureId
import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemId
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import kotlin.time.DurationUnit

class GradleBuildReportTools : McpServerComponent() {

    @Serializable
    data class TestDetailsLookupArgs(
        @Description("The build ID to look up.")
        val buildId: BuildId,
        @Description("A prefix of the fully-qualified test name (class or method). Matching is case-sensitive and checks startsWith on the full test name.")
        val testNamePrefix: String
    )

    @Serializable
    data class TestDetailsResult(
        val tests: List<TestDetails>
    ) {
        @Serializable
        data class TestDetails(
            val testName: String,
            val consoleOutput: String?,
            val executionDurationSeconds: Double,
            @Description("Summaries of failures for this test, if any")
            val failures: List<BuildResultSummary.FailureSummary>
        )
    }

    val lookupBuildTestDetails by tool<TestDetailsLookupArgs, TestDetailsResult>(
        name = "lookup_build_test_details",
        description = "For a given build, gets the details of test executions matching the prefix.",
    ) {
        val build = BuildResults.require(it.buildId)
        val results = build.testResults ?: return@tool TestDetailsResult(emptyList())

        val matched = results.all
            .filter { tr -> tr.testName.startsWith(it.testNamePrefix) }
            .map { tr ->
                TestDetailsResult.TestDetails(
                    testName = tr.testName,
                    consoleOutput = tr.consoleOutput,
                    executionDurationSeconds = tr.executionDuration.toDouble(DurationUnit.SECONDS),
                    failures = (tr.failures ?: emptyList()).map { f ->
                        f.toSummary()
                    }
                )
            }
            .toList()

        TestDetailsResult(matched)
    }

    @Serializable
    data class BuildIdArgs(
        @Description("The build ID to look up information for.")
        val buildId: BuildId
    )

    val lookupBuildTestsSummary by tool<BuildIdArgs, TestResultsSummary>(
        name = "lookup_build_tests_summary",
        description = "For a given build, gets the summary of all test executions.",
    ) {
        val build = BuildResults.require(it.buildId)
        val testResults = build.testResults
        testResults.toSummary()
    }

    val lookupBuildSummary by tool<BuildIdArgs, BuildResultSummary>(
        name = "lookup_build_summary",
        description = "Takes a build ID; returns a summary of tests for that build.",
    ) {
        val build = BuildResults.require(it.buildId)
        build.toSummary()
    }

    @Serializable
    data class FailuresSummaryResult(
        @Description("Summaries of all failures (including build and test failures) in the build.")
        val failures: List<BuildResultSummary.FailureSummary>
    )


    val lookupBuildFailures by tool<BuildIdArgs, FailuresSummaryResult>(
        name = "lookup_build_failures_summary",
        description = "For a given build, gets the summary of all failures (including build and test failures) in the build. Use `lookup_build_failure_details` to get the details of a specific failure.",
    ) {
        val build = BuildResults.require(it.buildId)
        val summaries = build.allFailures.values.map { f ->
            f.toSummary()
        }
        FailuresSummaryResult(summaries)
    }

    @Serializable
    data class FailureLookupArgs(
        @Description("The build ID to look up.")
        val buildId: BuildId,
        @Description("The failure ID to get details for.")
        val failureId: FailureId
    )

    @Serializable
    data class FailureDetailsResult(
        val failure: FailureDetails
    ) {

        @Serializable
        data class FailureDetails(
            val id: FailureId,
            val message: String?,
            val description: String?,
            @Description("Summaries of the direct causes of this failure")
            val causes: List<BuildResultSummary.FailureSummary>,
            @Description("Summaries for problems associated with this failure, if any")
            val problems: List<ProblemId>
        )
    }

    val lookupBuildFailureDetails by tool<FailureLookupArgs, FailureDetailsResult>(
        name = "lookup_build_failure_details",
        description = "For a given build, gets the details of a failure with the given ID. Use `lookup_build_failures_summary` to get a list of failure IDs.",
    ) {
        val build = BuildResults.require(it.buildId)
        val failure = build.allFailures[it.failureId] ?: error("No failure with ID ${it.failureId} found for build ${it.buildId}")
        FailureDetailsResult(
            FailureDetailsResult.FailureDetails(
                id = failure.id,
                message = failure.message,
                description = failure.description,
                causes = failure.causes.map { c ->
                    c.toSummary()
                },
                problems = failure.problems
            )
        )
    }


    @Serializable
    data class ProblemsSummaryResult(
        val errors: List<ProblemSummary>,
        val warnings: List<ProblemSummary>,
        val advices: List<ProblemSummary>,
        val others: List<ProblemSummary>,
    ) {
        @Serializable
        data class ProblemSummary(
            val id: ProblemId,
            val displayName: String,
            @Description("The severity of the problem. ERROR will fail a build.")
            val severity: ProblemSeverity,
            val documentationLink: String?,
            val numberOfOccurrences: Int,
        )
    }

    val lookupBuildProblemsSummary by tool<BuildIdArgs, ProblemsSummaryResult>(
        name = "lookup_build_problems_summary",
        description = "For a given build, get summaries for all problems attached to failures in the build. Use `lookup_build_problem_details` with the returned failure ID to get full details.",
    ) {
        val build = BuildResults.require(it.buildId)
        val summaries = build.problems.values.asSequence().flatten().map { p -> p.toSummary() }.toList()
        ProblemsSummaryResult(
            errors = summaries.filter { it.severity == ProblemSeverity.ERROR },
            warnings = summaries.filter { it.severity == ProblemSeverity.WARNING },
            advices = summaries.filter { it.severity == ProblemSeverity.ADVICE },
            others = summaries.filter { it.severity == ProblemSeverity.OTHER }
        )
    }

    @Serializable
    data class ProblemDetailsArgs(
        @Description("The build ID to look up.")
        val buildId: BuildId,
        @Description("The ProblemId of the problem to look up. Obtain from `lookup_build_problems_summary`.")
        val problemId: ProblemId,
    )

    // lookup_build_problem_details - takes build id and problem id, returns details about the problem
    val lookupBuildProblemDetails by tool<ProblemDetailsArgs, ProblemAggregation>(
        name = "lookup_build_problem_details",
        description = "For a given build, gets the details of all occurences of the problem with the given ID. Use `lookup_build_problems_summary` to get a list of all problem IDs for the build.",
    ) {
        val build = BuildResults.require(it.buildId)
        build.problems.values.asSequence().flatten().firstOrNull { p -> p.id == it.problemId }
            ?: error("No problem with id ${it.problemId} found for build ${it.buildId}")
    }

    private fun ProblemAggregation.toSummary(): ProblemsSummaryResult.ProblemSummary =
        ProblemsSummaryResult.ProblemSummary(
            id = id,
            displayName = displayName,
            severity = severity,
            documentationLink = documentationLink,
            numberOfOccurrences = numberOfOccurrences
        )
}
