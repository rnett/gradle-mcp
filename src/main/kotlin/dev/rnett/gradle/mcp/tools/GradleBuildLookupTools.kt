package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.ProblemId
import dev.rnett.gradle.mcp.gradle.build.Build
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.FailureId
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.gradle.build.TaskOutcome
import dev.rnett.gradle.mcp.gradle.build.TestOutcome
import dev.rnett.gradle.mcp.gradle.build.failuresIfFailed
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.io.path.absolutePathString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

class GradleBuildLookupTools(val buildResults: BuildManager) : McpServerComponent("Lookup Tools", "Tools for looking up detailed information about past Gradle builds ran by this MCP server.") {

    @Serializable
    enum class LookupMode {
        @Description("Return a summary list of items.")
        summary,

        @Description("Return detailed information about a specific item.")
        details
    }

    @Serializable
    data class TestOptions(
        @Description("In 'summary' mode, a prefix of the test name to filter by. In 'details' mode, the full name of the test.")
        val name: String = "",
        @Description("Filter results by outcome (summary mode only).")
        val outcome: TestOutcome? = null,
        @Description("The index of the test to show if multiple tests have the same name (details mode only).")
        val testIndex: Int = 0
    )

    @Serializable
    data class TaskOptions(
        @Description("In 'summary' mode, a prefix of the task path to filter by. In 'details' mode, the full path of the task.")
        val path: String = "",
        @Description("Filter results by outcome (summary mode only).")
        val outcome: TaskOutcome? = null
    )

    @Serializable
    data class FailureOptions(
        @Description("The failure ID to get details for (details mode only).")
        val id: FailureId? = null
    )

    @Serializable
    data class ProblemOptions(
        @Description("The ProblemId of the problem to look up (details mode only).")
        val id: ProblemId? = null
    )

    @Serializable
    data class ConsoleOptions(
        @Description("If true, return the last `limit` lines of the console output instead of the first.")
        val tail: Boolean = false
    )

    @Serializable
    data class InspectBuildArgs(
        @Description("The build to inspect. If omitted, returns the build dashboard (active builds + recent history).")
        val buildId: BuildId? = null,

        @Description("The lookup mode: 'summary' (default) or 'details'.")
        val mode: LookupMode = LookupMode.summary,

        @Description("Max seconds to wait for an active build to reach a state or finish.")
        val wait: Double? = null,
        @Description("Regex pattern to wait for in the build logs.")
        val waitFor: String? = null,
        @Description("Task path to wait for completion.")
        val waitForTask: String? = null,
        @Description("If true, only look for waitFor or waitForTask matches emitted after this call. Only applies if wait and (waitFor or waitForTask) are also provided.")
        val afterCall: Boolean = false,

        @Description("The maximum number of results to return (applies to tasks, tests, console lines, and the build dashboard).")
        val limit: Int? = null,
        @Description("The offset to start from in the results (applies to tasks, tests, and console lines).")
        val offset: Int = 0,

        @Description("Options for task lookup.")
        val tasks: TaskOptions? = null,
        @Description("Options for test lookup.")
        val tests: TestOptions? = null,
        @Description("Options for failure lookup.")
        val failures: FailureOptions? = null,
        @Description("Options for problem lookup.")
        val problems: ProblemOptions? = null,
        @Description("Options for console output.")
        val console: ConsoleOptions? = null
    )

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    private fun getLatestBuildsOutput(args: InspectBuildArgs, onlyCompleted: Boolean): String {
        val maxBuilds = args.limit ?: 5
        val completed = buildResults.latestFinished(maxBuilds)
        val active = if (onlyCompleted) emptyList() else buildResults.listRunningBuilds()

        val all = (completed.map { it to false } + active.map { it to true })
            .sortedByDescending { (b, _) -> b.id.timestamp }
            .take(maxBuilds)

        if (all.isEmpty()) {
            return "No builds found"
        }

        return buildString {
            appendLine("BuildId | Command line | Seconds ago | Status | Build failures | Test failures")
            all.forEach { (build, isActive) ->
                val id = build.id
                val commandLine = build.args.renderCommandLine()
                val secondsAgo = Clock.System.now().minus(id.timestamp).inWholeSeconds

                append(id).append(" | ")
                append(commandLine).append(" | ")
                append(secondsAgo).append("s ago | ")

                if (isActive) {
                    val rb = build as RunningBuild
                    append(rb.status).append(" | ")
                    append("-").append(" | ")
                    appendLine("-")
                } else {
                    val br = build as FinishedBuild
                    append(if (br.outcome is BuildOutcome.Success) "SUCCESS" else "FAILURE").append(" | ")
                    append(br.outcome.failuresIfFailed?.size ?: 0).append(" | ")
                    appendLine(br.testResults.failed.size)
                }
            }
        }
    }

    private fun getTestsOutput(build: Build, args: InspectBuildArgs): String {
        val testOptions = args.tests ?: TestOptions()
        if (args.mode == LookupMode.details) {
            require(testOptions.name.isNotEmpty()) { "Test name is required for details mode" }
            val tests = build.testResults.all.filter { t -> t.testName == testOptions.name }.toList()

            if (tests.isEmpty()) {
                val isRunning = build is RunningBuild || build.isRunning
                if (isRunning) {
                    return "Test not found. The build is still running, so it may not have been executed yet."
                } else {
                    error("Test not found")
                }
            }

            if (tests.size > 1 && testOptions.testIndex >= tests.size) {
                return "${tests.size} test executions with this name found. Pass a valid `testIndex` (0 to ${tests.size - 1}) to select one."
            }

            val test = if (tests.size > 1) tests[testOptions.testIndex] else tests.single()

            return buildString {
                append(test.testName).append(" - ").appendLine(test.status)
                appendLine("Duration: ${test.executionDuration}")

                if (!test.failures.isNullOrEmpty()) {
                    appendLine("Failures:")
                    test.failures.forEach { f ->
                        f.writeFailureTree(this, "  ")
                    }
                }

                if (test.metadata.isNotEmpty()) {
                    appendLine("Metadata:")
                    test.metadata.forEach { (key, value) ->
                        appendLine("  $key: $value")
                    }
                }

                if (test.attachments.isNotEmpty()) {
                    appendLine("Attached files:")
                    test.attachments.forEach { attachment ->
                        appendLine("  ${attachment.path.absolutePathString()}")
                    }
                }

                appendLine("Console output:")
                appendLine(test.consoleOutput)
            }
        }

        require(args.offset >= 0) { "`offset` must be non-negative" }
        val limit = args.limit ?: 20
        require(limit > 0) { "`limit` must be > 0" }

        val matched = build.testResults.all
            .filter { tr -> tr.testName.startsWith(testOptions.name) }
            .filter { tr -> testOptions.outcome == null || tr.status == testOptions.outcome }

        val results = matched
            .drop(args.offset)
            .take(limit)
            .toList()

        return buildString {
            appendLine("Total matching results: ${matched.count()}")
            appendLine("Test | Outcome | Metadata")
            results.forEach { tr ->
                appendLine(
                    "${tr.testName} | ${tr.status} | ${
                        tr.metadata.mapValues {
                            if (it.value.length > 20) it.value.take(20) + " ... (truncated by gradle-mcp)" else it.value
                        }
                    }"
                )
            }
        }
    }

    private fun getTasksOutput(result: Build, args: InspectBuildArgs): String {
        val taskOptions = args.tasks ?: TaskOptions()
        if (args.mode == LookupMode.details) {
            require(taskOptions.path.isNotEmpty()) { "Task path is required for details mode" }
            val taskResult = result.taskResults[taskOptions.path]
                ?: throw IllegalArgumentException("Task not found in build ${result.id}: ${taskOptions.path}")

            return buildString {
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
            val tasks = result.taskResults.values
                .asSequence()
                .filter { it.path.startsWith(taskOptions.path) }
                .filter { taskOptions.outcome == null || it.outcome == taskOptions.outcome }
                .sortedBy { it.path }
                .toList()

            val limit = args.limit ?: 50
            return buildString {
                if (result.taskOutputCapturingFailed) {
                    appendLine("WARNING: Task output capturing failed for this build. Task outputs may be missing or incomplete.")
                    appendLine()
                }
                appendLine("Task Path | Outcome | Duration")
                tasks.asSequence().drop(args.offset).take(limit).forEach { task ->
                    append(task.path).append(" | ")
                    append(task.outcome).append(" | ")
                    appendLine(task.duration)
                }

                if (tasks.size > (args.offset + limit)) {
                    appendLine()
                    appendLine("... and ${tasks.size - (args.offset + limit)} more tasks")
                }
            }
        }
    }

    private fun getFailuresOutput(build: Build, args: InspectBuildArgs): String {
        val failureOptions = args.failures ?: FailureOptions()
        val allFailures = if (build is FinishedBuild) build.allFailures else build.allTestFailures

        if (args.mode == LookupMode.details) {
            val failureId = failureOptions.id ?: error("Failure ID is required for details mode")
            val failure = allFailures[failureId]
                ?: error("No failure with ID $failureId found for build ${build.id}")

            return buildString {
                failure.writeFailureTree(this)
            }
        }

        return buildString {
            appendLine("Id | Message")
            allFailures.forEach { (id, failure) ->
                appendLine("${id.id} : ${failure.message}")
            }
            if (build.isRunning && allFailures.isEmpty()) {
                appendLine("No failures found yet. The build is still running.")
            }
        }
    }

    private fun getProblemsOutput(build: Build, args: InspectBuildArgs): String {
        val problemOptions = args.problems ?: ProblemOptions()
        if (args.mode == LookupMode.details) {
            val problemId = problemOptions.id ?: error("Problem ID is required for details mode")
            val problem = build.problems.firstOrNull { p -> p.definition.id == problemId }
                ?: error("No problem with id $problemId found for build ${build.id}")
            return buildString {
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

        return buildString {
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

    private fun getConsoleOutput(build: Build, args: InspectBuildArgs): String {
        val consoleOptions = args.console ?: ConsoleOptions()
        require(args.offset >= 0) { "`offset` must be non-negative" }
        val limit = args.limit ?: 100
        require(limit > 0) { "`limit` must be > 0" }

        val start: Int
        val end: Int
        val lines: List<String>
        val nextOffset: Int?
        when {
            consoleOptions.tail -> {
                end = build.consoleOutputLines.size - args.offset
                start = end - limit
                nextOffset = start.takeIf { it > 0 }
                lines = build.consoleOutputLines.subList(start.coerceAtLeast(0), end.coerceIn(0, build.consoleOutputLines.size))
            }

            else -> {
                end = (args.offset + limit)
                start = args.offset
                nextOffset = end.takeIf { it < build.consoleOutputLines.size }
                lines = build.consoleOutputLines.subList(args.offset, end.coerceAtMost(build.consoleOutputLines.size))
            }
        }

        return buildString {
            appendLine("Lines $start to $end of ${build.consoleOutputLines.size} lines, ${if (nextOffset != null) "next offset: $nextOffset" else "reached end of stream"}")
            appendLine()
            append(lines.joinToString("\n"))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val inspectBuild by tool<InspectBuildArgs, String>(
        ToolNames.INSPECT_BUILD,
        """
            |The central tool for retrieving build information, monitoring progress, and diagnosing failures. It acts as a "Dashboard" and "Deep Dive" tool for all builds (active and historical).
            |
            |### Usage Overview
            |- **Dashboard**: Call without `buildId` to see active and recent builds.
            |- **Monitoring**: Use `wait`, `waitFor`, or `waitForTask` with a `buildId` to monitor an active build.
            |- **Deep Dive**: Specify a `buildId` and exactly one of the detail sections (`tasks`, `tests`, `failures`, `problems`, or `console`).
            |- **Modes**: Use `mode="summary"` (default) for lists and `mode="details"` for deep dives into specific items (requires `name`, `path`, or `id` in the section options).
            |- **Pagination**: Use top-level `limit` and `offset` for lists and console output.
            |
            |### Section Options
            |Only one of the following may be specified per call:
            |- `tasks`: List tasks or get task output.
            |- `tests`: List tests or get test details/stack traces.
            |- `failures`: List build failures or get failure trees.
            |- `problems`: List build problems or get problem details.
            |- `console`: Read console logs with pagination.
            |
            |If no section is specified, the build summary is returned.
            |
            |For detailed diagnostic workflows, refer to the `gradle-build` and `gradle-test` skills.
        """.trimMargin()
    ) {
        if (it.buildId == null) {
            return@tool getLatestBuildsOutput(it, false)
        }

        var build = buildResults.getBuild(it.buildId)
            ?: throw IllegalArgumentException("Unknown or expired build ID: ${it.buildId}")

        val startLines = if (it.afterCall && build is RunningBuild) build.consoleOutputLines.size else 0

        if (it.wait != null && build is RunningBuild) {
            val waitForRegex = it.waitFor?.toRegex()
            val waitForTask = it.waitForTask

            withTimeoutOrNull(it.wait.seconds) {
                coroutineScope {
                    select {
                        onTimeout(it.wait.seconds) {}
                        if (waitForRegex != null) {
                            if (!it.afterCall && waitForRegex.containsMatchIn(build.logBuffer)) {
                                launch { }.onJoin {}
                            } else {
                                async {
                                    build.logLines.firstOrNull { line: String ->
                                        waitForRegex.containsMatchIn(line)
                                    }
                                }.onAwait { }
                            }
                        }
                        if (waitForTask != null) {
                            if (!it.afterCall && build.completedTaskPaths.contains(waitForTask)) {
                                launch { }.onJoin {}
                            } else {
                                async {
                                    build.completedTasks.firstOrNull { taskPath: String ->
                                        taskPath == waitForTask
                                    }
                                }.onAwait { }
                            }
                        }
                        async { build.awaitFinished() }.onAwait { }
                    }
                }
            }
            // re-get build to pick up any changes (e.g. it might have finished)
            build = buildResults.getBuild(it.buildId)!!
        }

        val setOptions = listOfNotNull(it.tasks, it.tests, it.failures, it.problems, it.console)
        require(setOptions.size <= 1) { "Only one of tasks, tests, failures, problems, or console may be specified." }

        buildString {
            if (build is RunningBuild) {
                appendLine("--- BUILD IN PROGRESS ---")
            } else {
                appendLine("--- BUILD FINISHED ---")
            }

            val waitForRegex = it.waitFor?.toRegex()
            if (waitForRegex != null) {
                val matchingLines = build.consoleOutputLines.drop(startLines).filter { line -> line.isNotBlank() && waitForRegex.containsMatchIn(line) }
                if (matchingLines.isNotEmpty()) {
                    appendLine("Matching lines for '${it.waitFor}':")
                    matchingLines.forEach { appendLine("    $it") }
                    appendLine()
                } else if (it.wait != null) {
                    appendLine("No matched lines - build completed or wait timed out")
                    appendLine()
                }
            }

            if (setOptions.isEmpty()) {
                if (it.mode == LookupMode.details) {
                    appendLine("WARNING: Details mode requires a section (tasks, tests, etc.) to be specified. Showing build summary instead.")
                }
                appendLine("--- Summary ---")
                appendLine(build.toOutputString(true))
                appendLine()
            } else {
                if (it.tasks != null) {
                    appendLine("--- Tasks ---")
                    appendLine(getTasksOutput(build, it))
                    appendLine()
                }
                if (it.failures != null) {
                    appendLine("--- Failures ---")
                    appendLine(getFailuresOutput(build, it))
                    appendLine()
                }
                if (it.problems != null) {
                    appendLine("--- Problems ---")
                    appendLine(getProblemsOutput(build, it))
                    appendLine()
                }
                if (it.tests != null) {
                    appendLine("--- Tests ---")
                    appendLine(getTestsOutput(build, it))
                    appendLine()
                }
                if (it.console != null) {
                    appendLine("--- Console Output ---")
                    appendLine(getConsoleOutput(build, it))
                    appendLine()
                }
            }
        }
    }
}
