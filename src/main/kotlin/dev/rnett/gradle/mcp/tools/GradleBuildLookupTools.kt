package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildResults
import dev.rnett.gradle.mcp.gradle.FailureId
import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemId
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import dev.rnett.gradle.mcp.gradle.TestOutcome
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

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

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    val lookupLatestBuilds by tool<LatestBuildArgs, String>(
        "lookup_latest_builds",
        "Gets the latest builds ran by this MCP server."
    ) {
        buildString {
            appendLine("BuildId | Command line | Seconds ago | Status | Build failures | Test failures")
            BuildResults.latest(it.maxBuilds)
                .forEach {
                    append(it.id).append(" | ")
                    append(it.args.renderCommandLine()).append(" | ")
                    append(it.id.timestamp.minus(Clock.System.now()).inWholeSeconds).append("s ago | ")
                    append(if (it.isSuccessful) "SUCCESS" else "FAILURE").append(" | ")
                    append(it.buildFailures?.size ?: 0).append(" | ")
                    appendLine(it.testResults.failed.size)
                }
        }
    }

    @Serializable
    data class TestsLookupArgs(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
        @Description("A prefix of the fully-qualified test name (class or method). Matching is case-sensitive and checks startsWith on the full test name. Defaults to empty (aka all tests).")
        val testNamePrefix: String = "",
        val offset: Int = 0,
        val limit: Int? = 20,
        val outcome: TestOutcome? = null
    )


    val lookupBuildTests by tool<TestsLookupArgs, String>(
        name = "lookup_build_tests",
        description = "For a given build, gets an overview of test executions matching the prefix.  Control results using `offset` (defaults to 0), `limit` (defaults to 20, pass null to return all), and `outcome` (defaults to null, which includes all)  Use `lookup_build_test_details` to get more details for a specific execution.",
    ) {
        require(it.offset >= 0) { "`offset` must be non-negative" }
        require(it.limit == null || it.limit > 0) { "`limit` must be null or > 0" }
        val build = BuildResults.require(it.buildId)

        val matched = build.testResults.all
            .filter { tr -> tr.testName.startsWith(it.testNamePrefix) }
        val results = matched
            .drop(it.offset)
            .take(it.limit ?: Int.MAX_VALUE)
            .toList()

        buildString {
            appendLine("Total matching results: ${matched.count()}")
            appendLine("Test | Outcome")
            results.forEach {
                appendLine("${it.testName} | ${it.status}")
            }
        }
    }

    @Serializable
    data class TestDetailsLookupArgs(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
        @Description("The test to show the details of.")
        val testName: String,
        @Description("The index of the test to show, if there are multiple tests with the same name")
        val testIndex: Int = 0
    )

    val lookupBuildTestDetails by tool<TestDetailsLookupArgs, String>(
        name = "lookup_build_test_details",
        description = "Gets the details of test execution of the given test.",
    ) { args ->
        val build = BuildResults.require(args.buildId)
        val tests = build.testResults.all.filter { it.testName == args.testName }.toList()

        if (tests.isEmpty())
            error("Test not found")

        if (tests.size > 1) {
            return@tool "${tests.size} test executions with this name found.  Pass the `testIndex` parameter to select one."
        }

        val test = tests.single()

        buildString {
            append(test.testName).append(" - ").appendLine(test.status)
            appendLine("Duration: ${test.executionDuration}")

            if (!test.failures.isNullOrEmpty()) {
                appendLine("Failures: ${test.failures}")
                test.failures.forEach {
                    it.writeFailureTree(this, "  ")
                }
            }

            appendLine("Console output:")
            appendLine(test.consoleOutput)
        }
    }

    @Serializable
    data class BuildIdArgs(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
    )

    val lookupBuild by tool<BuildIdArgs, String>(
        name = "lookup_build",
        description = "Takes a build ID; returns a summary of that build.",
    ) {
        val build = BuildResults.require(it.buildId)
        build.toOutputString(true)
    }


    val lookupBuildFailures by tool<BuildIdArgs, String>(
        name = "lookup_build_failures_summary",
        description = "For a given build, gets the summary of all build (not test) failures in the build. Use `lookup_build_failure_details` to get the details of a specific failure.",
    ) {
        val build = BuildResults.require(it.buildId)
        buildString {
            appendLine("Id | Message")
            build.allFailures.forEach { (id, failure) ->
                appendLine("${id.id} : ${failure.message}")
            }
        }
    }

    @Serializable
    data class FailureLookupArgs(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
        @Description("The failure ID to get details for.")
        val failureId: FailureId
    )


    val lookupBuildFailureDetails by tool<FailureLookupArgs, String>(
        name = "lookup_build_failure_details",
        description = "For a given build, gets the details of a failure with the given ID. Use `lookup_build_failures_summary` to get a list of failure IDs.",
    ) {
        val build = BuildResults.require(it.buildId)
        val failure = build.allFailures[it.failureId] ?: error("No failure with ID ${it.failureId} found for build ${it.buildId}")

        buildString {
            failure.writeFailureTree(this)
        }
    }

    val lookupBuildProblemsSummary by tool<BuildIdArgs, String>(
        name = "lookup_build_problems_summary",
        description = "For a given build, get summaries for all problems attached to failures in the build. Use `lookup_build_problem_details` with the returned failure ID to get full details.",
    ) {
        val build = BuildResults.require(it.buildId)
        buildString {
            fun writeProblems(list: List<ProblemAggregation>) {
                appendLine("Id | Name | Occurrences")
                list.forEach { p ->
                    appendLine("${p.definition.id.id} | ${p.definition.displayName ?: "N/A"} | ${p.numberOfOccurrences}")
                }
                appendLine()
            }

            if (!build.problems[ProblemSeverity.ERROR].isNullOrEmpty()) {
                appendLine("Errors:")
                writeProblems(build.problems[ProblemSeverity.ERROR]!!)
            }

            if (!build.problems[ProblemSeverity.WARNING].isNullOrEmpty()) {
                appendLine("Warnings:")
                writeProblems(build.problems[ProblemSeverity.WARNING]!!)
            }

            if (!build.problems[ProblemSeverity.ADVICE].isNullOrEmpty()) {
                appendLine("Advices:")
                writeProblems(build.problems[ProblemSeverity.ADVICE]!!)
            }

            if (!build.problems[ProblemSeverity.OTHER].isNullOrEmpty()) {
                appendLine("Other:")
                writeProblems(build.problems[ProblemSeverity.OTHER]!!)
            }

        }
    }

    @Serializable
    data class ProblemDetailsArgs(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
        @Description("The ProblemId of the problem to look up. Obtain from `lookup_build_problems_summary`.")
        val problemId: ProblemId,
    )

    val lookupBuildProblemDetails by tool<ProblemDetailsArgs, String>(
        name = "lookup_build_problem_details",
        description = "For a given build, gets the details of all occurrences of the problem with the given ID. Use `lookup_build_problems_summary` to get a list of all problem IDs for the build.",
    ) {
        val build = BuildResults.require(it.buildId)
        val problem = build.problems.values.asSequence().flatten().firstOrNull { p -> p.definition.id == it.problemId }
            ?: error("No problem with id ${it.problemId} found for build ${it.buildId}")
        buildString {
            append(problem.definition.id)
            if (problem.definition.displayName != null) {
                appendLine(": ${problem.definition.displayName}")
            } else {
                appendLine()
            }

            if (problem.definition.documentationLink != null) {
                appendLine("Documentation: ${problem.definition.documentationLink}")
            }

            appendLine("Occurrences: ${problem.numberOfOccurrences}")
            problem.occurences.forEach {
                append("  Locations: ")
                appendLine(it.originLocations)
                if (it.details != null) {
                    if (it.details.contains("\n")) {
                        appendLine("  Details: \n${it.details.prependIndent("    ")}")
                    } else {
                        appendLine("  Details: ${it.details}")
                    }
                }

                if (it.contextualLocations.isNotEmpty()) {
                    append("  Context (possibly related) locations: ")
                    appendLine(it.contextualLocations)
                }

                if (it.potentialSolutions.isNotEmpty()) {
                    appendLine("  Potential Solutions.")
                    it.potentialSolutions.forEach {
                        if (it.contains("\n")) {
                            appendLine(
                                "  - " + it.prependIndent("    ").trimStart()
                            )
                        } else {
                            appendLine("  - $it")
                        }
                    }
                }

            }

        }
    }

    @Serializable
    data class ConsoleOutputArgs(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
        val offsetLines: Int,
        val limitLines: Int? = 100,
        val tail: Boolean = false
    )

    val lookupBuildConsoleOutput by tool<ConsoleOutputArgs, String>(
        "lookup_build_console_output",
        "Gets up to `limitLines` (default 100, null means no limit) of the console output for a given build, starting at a given offset `offsetLines` (default 0). Can read from the tail instead of the head. Repeatedly call this tool using the `nextOffset` in the response to get all console output."
    ) {
        require(it.offsetLines >= 0) { "`offsetLines` must be non-negative" }
        require(it.limitLines == null || it.limitLines > 0) { "`Description` must be null or > 0" }

        val build = BuildResults.require(it.buildId)
        val start: Int
        val end: Int
        val lines: List<String>
        val nextOffset: Int?
        when {
            it.tail -> {
                end = build.consoleOutputLines.size - it.offsetLines
                start = if (it.limitLines == null) 0 else end - it.limitLines
                nextOffset = start.takeIf { it > 0 }
                lines = build.consoleOutputLines.subList(start.coerceAtLeast(0), end.coerceIn(0, build.consoleOutputLines.size))
            }

            else -> {
                end = (it.offsetLines + (it.limitLines ?: build.consoleOutputLines.size))
                start = it.offsetLines
                nextOffset = end.takeIf { it < build.consoleOutputLines.size }
                lines = build.consoleOutputLines.subList(it.offsetLines, end.coerceAtMost(build.consoleOutputLines.size))
            }
        }

        buildString {
            appendLine("Lines $start to $end of ${build.consoleOutputLines.size} lines, ${if (nextOffset != null) "next offset: $nextOffset" else "reached end of stream"}")
            appendLine()
            append(lines.joinToString("\n"))
        }
    }

}
