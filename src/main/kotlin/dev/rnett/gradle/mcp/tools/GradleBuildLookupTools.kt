package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.BuildResults
import dev.rnett.gradle.mcp.gradle.FailureId
import dev.rnett.gradle.mcp.gradle.ProblemId
import dev.rnett.gradle.mcp.gradle.RunningBuild
import dev.rnett.gradle.mcp.gradle.TaskOutcome
import dev.rnett.gradle.mcp.gradle.TestOutcome
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

class GradleBuildLookupTools(val buildResults: BuildResults) : McpServerComponent("Lookup Tools", "Tools for looking up detailed information about past Gradle builds ran by this MCP server.") {

    companion object {
        const val BUILD_ID_DESCRIPTION = "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
    }


    @Serializable
    data class LatestBuildArgs(
        @Description("The maximum number of builds to return. Defaults to 5.")
        val maxBuilds: Int = 5,
        @Description("Whether to only show completed builds. Defaults to false.")
        val onlyCompleted: Boolean = false
    )

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    val lookupLatestBuilds by tool<LatestBuildArgs, String>(
        "lookup_latest_builds",
        "Gets the latest builds (both background and completed) ran by this MCP server."
    ) {
        val completed = buildResults.latest(it.maxBuilds)
        val active = if (it.onlyCompleted) emptyList() else buildResults.backgroundBuildManager.listBuilds()

        val all = (completed.map { it to false } + active.map { it to true })
            .sortedByDescending { (b, _) -> b.id.timestamp }
            .take(it.maxBuilds)

        buildString {
            appendLine("BuildId | Command line | Seconds ago | Status | Build failures | Test failures")
            all.forEach { (build, isActive) ->
                val id = build.id
                val commandLine = build.args.renderCommandLine()
                val secondsAgo = id.timestamp.minus(Clock.System.now()).inWholeSeconds

                append(id).append(" | ")
                append(commandLine).append(" | ")
                append(secondsAgo).append("s ago | ")

                if (isActive) {
                    val rb = build as RunningBuild<*>
                    append(rb.status).append(" | ")
                    append("-").append(" | ")
                    appendLine("-")
                } else {
                    val br = build as BuildResult
                    append(if (br.isSuccessful == true) "SUCCESS" else "FAILURE").append(" | ")
                    append(br.buildFailures?.size ?: 0).append(" | ")
                    appendLine(br.testResults.failed.size)
                }
            }
        }
    }

    @Serializable
    data class TestSummaryArgs(
        @Description("A prefix of the fully-qualified test name (class or method). Matching is case-sensitive and checks startsWith on the full test name. Defaults to empty (aka all tests).")
        val testNamePrefix: String = "",
        @Description("The offset to start from in the results.")
        val offset: Int = 0,
        @Description("The maximum number of results to return.")
        val limit: Int? = 20,
        @Description("Filter results by outcome.")
        val outcome: TestOutcome? = null,
    )

    @Serializable
    data class TestDetailArgs(
        @Description("The full name of the test to show details for. If multiple tests have this name, use `testIndex` to select one.")
        val testName: String,
        @Description("The index of the test to show if multiple tests have the same name.")
        val testIndex: Int = 0
    )

    @Serializable
    data class TestsLookupArgs(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
        @Description("Arguments for test summary mode. Only one of `summary` or `details` may be specified.")
        val summary: TestSummaryArgs? = null,
        @Description("Arguments for test detail mode. Only one of `summary` or `details` may be specified.")
        val details: TestDetailArgs? = null
    )


    val lookupBuildTests by tool<TestsLookupArgs, String>(
        name = "lookup_build_tests",
        description = "For a given build, provides either a summary of test executions or detailed information for a specific test. If `details` is provided, detailed execution info (duration, failure details, and console output) for that test is returned. If `summary` is provided (or neither), returns a list of tests matching the provided filters. Only one of `summary` or `details` may be specified. Works for in-progress builds.",
    ) {
        require(it.summary == null || it.details == null) { "Only one of `summary` or `details` may be specified." }

        val build = buildResults.require(it.buildId)

        if (it.details != null) {
            val tests = build.testResults.all.filter { t -> t.testName == it.details.testName }.toList()

            if (tests.isEmpty()) {
                val isRunning = build is RunningBuild<*> || build.isRunning
                if (isRunning) {
                    return@tool "Test not found. The build is still running, so it may not have been executed yet."
                } else {
                    error("Test not found")
                }
            }

            if (tests.size > 1 && it.details.testIndex >= tests.size) {
                return@tool "${tests.size} test executions with this name found. Pass a valid `testIndex` (0 to ${tests.size - 1}) to select one."
            }

            val test = if (tests.size > 1) tests[it.details.testIndex] else tests.single()

            return@tool buildString {
                append(test.testName).append(" - ").appendLine(test.status)
                appendLine("Duration: ${test.executionDuration}")

                if (!test.failures.isNullOrEmpty()) {
                    appendLine("Failures: ")
                    test.failures.forEach { f ->
                        f.writeFailureTree(this, "  ")
                    }
                }

                appendLine("Console output:")
                appendLine(test.consoleOutput)
            }
        }

        val summary = it.summary ?: TestSummaryArgs()

        require(summary.offset >= 0) { "`offset` must be non-negative" }
        require(summary.limit == null || summary.limit > 0) { "`limit` must be null or > 0" }

        val matched = build.testResults.all
            .filter { tr -> tr.testName.startsWith(summary.testNamePrefix) }
            .filter { tr -> summary.outcome == null || tr.status == summary.outcome }

        val results = matched
            .drop(summary.offset)
            .take(summary.limit ?: Int.MAX_VALUE)
            .toList()

        buildString {
            appendLine("Total matching results: ${matched.count()}")
            appendLine("Test | Outcome")
            results.forEach { tr ->
                appendLine("${tr.testName} | ${tr.status}")
            }
        }
    }

    @Serializable
    data class TaskSummaryArgs(
        @Description("A prefix of the task path (e.g. ':app:'). Matching is case-sensitive and checks startsWith on the task path. Defaults to empty (aka all tasks).")
        val taskPathPrefix: String = "",
        @Description("The offset to start from in the results.")
        val offset: Int = 0,
        @Description("The maximum number of results to return.")
        val limit: Int? = 50,
        @Description("Filter results by outcome.")
        val outcome: TaskOutcome? = null,
    )

    @Serializable
    data class TaskDetailArgs(
        @Description("The full path of the task to show details for.")
        val taskPath: String,
    )

    @Serializable
    data class TaskLookupArgs(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
        @Description("Arguments for task summary mode. Only one of `summary` or `details` may be specified.")
        val summary: TaskSummaryArgs? = null,
        @Description("Arguments for task detail mode. Only one of `summary` or `details` may be specified.")
        val details: TaskDetailArgs? = null
    )

    val lookupBuildTasks by tool<TaskLookupArgs, String>(
        "lookup_build_tasks",
        "For a given build, provides either a summary of task executions or detailed information for a specific task. If `details` is provided, detailed execution info (duration, outcome, and console output) for that task is returned. If `summary` is provided (or neither), returns a list of tasks matching the provided filters. Only one of `summary` or `details` may be specified. Works for in-progress builds."
    ) {
        val result = buildResults.require(it.buildId)
        if (it.summary != null && it.details != null)
            throw IllegalArgumentException("Only one of summary or details may be specified")

        if (it.details != null) {
            val taskResult = result.taskResults[it.details.taskPath]
                ?: throw IllegalArgumentException("Task not found in build ${result.id}: ${it.details.taskPath}")

            buildString {
                appendLine("Task: ${taskResult.path}")
                appendLine("Outcome: ${taskResult.outcome}")
                appendLine("Duration: ${taskResult.duration}")
                appendLine()
                if (result.taskOutputCapturingFailed) {
                    appendLine("WARNING: Task output capturing failed for this build. Showing guessed output from console if available - may be incomplete or interleaved with other tasks.")
                    val guessed = result.getTaskOutput(taskResult.path, true)
                    if (guessed != null) {
                        appendLine("Guessed Output:")
                        appendLine(guessed)
                    } else {
                        appendLine("No output found for this task.")
                    }
                } else {
                    if (taskResult.consoleOutput != null) {
                        appendLine("Output:")
                        appendLine(taskResult.consoleOutput)
                    } else {
                        appendLine("No output captured for this task.")
                    }
                }
            }
        } else {
            val summary = it.summary ?: TaskSummaryArgs()
            val tasks = result.taskResults.values
                .asSequence()
                .filter { it.path.startsWith(summary.taskPathPrefix) }
                .filter { summary.outcome == null || it.outcome == summary.outcome }
                .sortedBy { it.path }
                .toList()

            buildString {
                if (result.taskOutputCapturingFailed) {
                    appendLine("WARNING: Task output capturing failed for this build. Task outputs may be missing or incomplete.")
                    appendLine()
                }
                appendLine("Task Path | Outcome | Duration")
                tasks.asSequence().drop(summary.offset).let {
                    if (summary.limit != null) it.take(summary.limit) else it
                }.forEach { task ->
                    append(task.path).append(" | ")
                    append(task.outcome).append(" | ")
                    appendLine(task.duration)
                }

                if (tasks.size > (summary.offset + (summary.limit ?: Int.MAX_VALUE))) {
                    appendLine()
                    appendLine("... and ${tasks.size - (summary.offset + (summary.limit ?: 0))} more tasks")
                }
            }
        }
    }

    @Serializable
    data class BuildIdArgs(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
    )

    val lookupBuild by tool<BuildIdArgs, String>(
        name = "lookup_build",
        description = "Takes a build ID; returns a summary of that build. Works for in-progress builds.",
    ) {
        val build = buildResults.require(it.buildId)
        build.toOutputString(true)
    }


    @Serializable
    class FailureSummaryArgs

    @Serializable
    data class FailureDetailArgs(
        @Description("The failure ID to get details for.")
        val failureId: FailureId
    )

    @Serializable
    data class FailureLookupArgs(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
        @Description("Arguments for failure summary mode. Only one of `summary` or `details` may be specified.")
        val summary: FailureSummaryArgs? = null,
        @Description("Arguments for failure detail mode. Only one of `summary` or `details` may be specified.")
        val details: FailureDetailArgs? = null
    )


    val lookupBuildFailures by tool<FailureLookupArgs, String>(
        name = "lookup_build_failures",
        description = "Provides a summary of build failures (including test failures) or details for a specific failure. If `details` is provided, detailed information (including causes and stack traces) for that failure is returned. If `summary` is provided (or neither), lists all build failures. Only one of `summary` or `details` may be specified. Works for in-progress builds, but may only show test failures.",
    ) {
        require(it.summary == null || it.details == null) { "Only one of `summary` or `details` may be specified." }

        val build = buildResults.require(it.buildId)

        val failures = build.allFailures

        if (it.details != null) {
            val failure = failures[it.details.failureId]
                ?: error("No failure with ID ${it.details.failureId} found for build ${build.id}")

            return@tool buildString {
                failure.writeFailureTree(this)
            }
        }

        buildString {
            appendLine("Id | Message")
            failures.forEach { (id, failure) ->
                appendLine("${id.id} : ${failure.message}")
            }
            if (build.isRunning && failures.isEmpty()) {
                appendLine("No failures found yet. The build is still running.")
            }
        }
    }

    @Serializable
    class ProblemSummaryArgs

    @Serializable
    data class ProblemDetailArgs(
        @Description("The ProblemId of the problem to look up.")
        val problemId: ProblemId
    )

    @Serializable
    data class ProblemLookupArgs(
        @Description(BUILD_ID_DESCRIPTION)
        val buildId: BuildId? = null,
        @Description("Arguments for problem summary mode. Only one of `summary` or `details` may be specified.")
        val summary: ProblemSummaryArgs? = null,
        @Description("Arguments for problem detail mode. Only one of `summary` or `details` may be specified.")
        val details: ProblemDetailArgs? = null,
    )

    val lookupBuildProblems by tool<ProblemLookupArgs, String>(
        name = "lookup_build_problems",
        description = "Provides a summary of all problems reported during a build (errors, warnings, etc.) or details for a specific problem. If `details` is provided, detailed information (locations, details, and potential solutions) for that problem is returned. If `summary` is provided (or neither), returns a summary of all problems. Only one of `summary` or `details` may be specified. Works for in-progress builds.",
    ) {
        require(it.summary == null || it.details == null) { "Only one of `summary` or `details` may be specified." }

        val build = buildResults.require(it.buildId)

        if (it.details != null) {
            val problem = build.problems.firstOrNull { p -> p.definition.id == it.details.problemId }
                ?: error("No problem with id ${it.details.problemId} found for build ${it.buildId}")
            return@tool buildString {
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
                problem.occurences.forEach { o ->
                    append("  Locations: ")
                    appendLine(o.originLocations)
                    if (o.details != null) {
                        if (o.details.contains("\n")) {
                            appendLine("  Details: \n${o.details.prependIndent("    ")}")
                        } else {
                            appendLine("  Details: ${o.details}")
                        }
                    }

                    if (o.contextualLocations.isNotEmpty()) {
                        appendLine("  Contextual Locations: ")
                        o.contextualLocations.forEach { l -> appendLine("    $l") }
                    }

                    if (o.potentialSolutions.isNotEmpty()) {
                        appendLine("  Potential Solutions: ")
                        o.potentialSolutions.forEach { s -> appendLine("    $s") }
                    }
                }
            }
        }

        buildString {
            appendLine("Severity | Id | Display Name | Occurrences")
            build.problems.sortedBy { it.definition.severity }.forEach {
                append(it.definition.severity)
                append(" | ")
                append(it.definition.id)
                append(" | ")
                append(it.definition.displayName ?: "N/A")
                append(" | ")
                appendLine(it.numberOfOccurrences)
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
        "Gets up to `limitLines` (default 100, null means no limit) of the console output for a given build, starting at a given offset `offsetLines` (default 0). Can read from the tail instead of the head. Repeatedly call this tool using the `nextOffset` in the response to get all console output. Works for in-progress builds."
    ) {
        require(it.offsetLines >= 0) { "`offsetLines` must be non-negative" }
        require(it.limitLines == null || it.limitLines > 0) { "`limitLines` must be null or > 0" }

        val build = buildResults.require(it.buildId)
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
