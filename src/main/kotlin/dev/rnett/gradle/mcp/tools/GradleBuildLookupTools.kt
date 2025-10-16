package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildResults
import dev.rnett.gradle.mcp.gradle.FailureId
import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemId
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable
import java.time.ZoneId
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

class GradleBuildLookupTools : McpServerComponent("Lookup Tools", "Tools for looking up detailed information about past Gradle builds ran by this MCP server.") {

    companion object {
        const val BUILD_ID_DESCRIPTION = "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
    }

    @Serializable
    data class LatestBuildsResults(
        @Description("The latest builds ran by this MCP server, starting with the latest.")
        val latestBuilds: List<LatestBuild>
    ) {
        @Serializable
        data class LatestBuild(
            val buildId: BuildId,
            val occuredAt: String,
        )
    }

    @Serializable
    data class LatestBuildArgs(
        @Description("The maximum number of builds to return. Defaults to 5.")
        val maxBuilds: Int = 5
    )

    @OptIn(ExperimentalTime::class)
    val lookupLatestBuilds by tool<LatestBuildArgs, LatestBuildsResults>(
        "lookup_latest_builds",
        "Gets the latest builds ran by this MCP server."
    ) {
        LatestBuildsResults(
            BuildResults.latest(it.maxBuilds).map {
                LatestBuildsResults.LatestBuild(
                    it.id,
                    it.id.timestamp.toJavaInstant().atZone(ZoneId.systemDefault())
                        .toString()
                )
            }
        )
    }

    @Serializable
    data class LatestBuildsSummariesResults(
        @Description("The latest builds ran by this MCP server, starting with the latest.")
        val latestBuilds: List<BuildResultSummary>
    ) {
        @Serializable
        data class LatestBuild(
            val buildId: BuildId,
            val occuredAt: String,
        )
    }

    @OptIn(ExperimentalTime::class)
    val lookupLatestBuildsSummaries by tool<LatestBuildArgs, LatestBuildsSummariesResults>(
        "lookup_latest_builds_summaries",
        "Gets the summaries the latest builds ran by this MCP server."
    ) {
        LatestBuildsSummariesResults(
            BuildResults.latest(it.maxBuilds).map {
                it.toSummary()
            }
        )
    }

    @Serializable
    data class TestDetailsLookupArgs(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
        @Description("A prefix of the fully-qualified test name (class or method). Matching is case-sensitive and checks startsWith on the full test name.")
        val testNamePrefix: String,
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
        val results = build.testResults

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
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
    )

    val lookupBuildTestsSummary by tool<BuildIdArgs, TestResultsSummary>(
        name = "lookup_build_tests_summary",
        description = "For a given build, gets the summary of all test executions.",
    ) {
        val build = BuildResults.require(it.buildId)
        val testResults = build.testResults
        testResults.toSummary(null)
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
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
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
            val displayName: String?,
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
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
        @Description("The ProblemId of the problem to look up. Obtain from `lookup_build_problems_summary`.")
        val problemId: ProblemId,
    )

    // lookup_build_problem_details - takes build id and problem id, returns details about the problem
    val lookupBuildProblemDetails by tool<ProblemDetailsArgs, ProblemAggregation>(
        name = "lookup_build_problem_details",
        description = "For a given build, gets the details of all occurences of the problem with the given ID. Use `lookup_build_problems_summary` to get a list of all problem IDs for the build.",
    ) {
        val build = BuildResults.require(it.buildId)
        build.problems.values.asSequence().flatten().firstOrNull { p -> p.definition.id == it.problemId }
            ?: error("No problem with id ${it.problemId} found for build ${it.buildId}")
    }

    private fun ProblemAggregation.toSummary(): ProblemsSummaryResult.ProblemSummary =
        ProblemsSummaryResult.ProblemSummary(
            id = definition.id,
            displayName = definition.displayName,
            severity = definition.severity,
            documentationLink = definition.documentationLink,
            numberOfOccurrences = numberOfOccurrences
        )

    @Serializable
    data class ConsoleOutputArgs(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
        @Description("The offset to start returning output from, in lines. Required.")
        val offsetLines: Int,
        @Description("The maximum lines of output to return. Defaults to 100. Null means no limit.")
        val limitLines: Int? = 100,
        @Description("If true, starts returning output from the end instead of the beginning (and offsetLines is from the end). Defaults to false.")
        val tail: Boolean = false
    )

    @Serializable
    data class ConsoleOutputResult(
        @Description("The offset to use for the next lookup_build_console_output call. Null if there is no more output to get.")
        val nextOffset: Int?
    )

    val lookupBuildConsoleOutput by tool<ConsoleOutputArgs, ConsoleOutputResult>(
        "lookup_build_console_output",
        "Gets up to `limitLines` of the console output for a given build, starting at a given offset `offsetLines`. Can read from the tail instead of the head. Repeatedly call this tool using the `nextOffset` in the response to get all console output."
    ) {
        require(it.offsetLines >= 0) { "Offset must be non-negative" }
        require(it.limitLines == null || it.limitLines > 0) { "Limit must be null or > 0" }

        val build = BuildResults.require(it.buildId)
        val (lines, nextOffset) = if (it.tail) {
            val end = build.consoleOutputLines.size - it.offsetLines
            val start = if (it.limitLines == null) 0 else end - it.limitLines
            build.consoleOutputLines.subList(start.coerceAtLeast(0), end.coerceIn(0, build.consoleOutputLines.size)) to start.takeIf { it > 0 }
        } else {
            val end = (it.offsetLines + (it.limitLines ?: build.consoleOutputLines.size))
            build.consoleOutputLines.subList(it.offsetLines, end.coerceAtMost(build.consoleOutputLines.size)) to end.takeIf { it < build.consoleOutputLines.size }
        }
        addAdditionalContent(TextContent(lines.joinToString("\n")))
        ConsoleOutputResult(nextOffset)
    }

}
