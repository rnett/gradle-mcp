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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
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
        @Description("Return a summary list of items. Ideal for high-level overviews.")
        summary,

        @Description("Return authoritative, exhaustive information about a specific item. This is the professionally recommended way to get full test failures, stack traces, and console logs.")
        details
    }

    @Serializable
    data class InspectBuildArgs(
        @Description("The managed BuildId to inspect authoritatively. If omitted, returns the high-level build dashboard showing active and recently completed builds.")
        val buildId: BuildId? = null,

        @Description("Applying a surgical lookup mode: 'summary' (default) or 'details'. Use 'details' for exhaustive, deep-dive information.")
        val mode: LookupMode = LookupMode.summary,

        @Description("Maximum seconds to wait for an active build to reach a state or finish authoritatively. Use this for managed progress monitoring.")
        val wait: Double? = null,
        @Description("Regex pattern to wait for in the build logs authoritatively. Ideal for detecting when a server has started or a specific event has occurred.")
        val waitFor: String? = null,
        @Description("Task path to wait for completion authoritatively. The most surgical way to monitor specific task progress.")
        val waitForTask: String? = null,
        @Description("Setting to true only looks for matches emitted after this call. Only applies if 'wait' and ('waitFor' or 'waitForTask') are provided.")
        val afterCall: Boolean = false,

        val pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS,

        @Description("Filter task results. In 'summary' mode, a prefix of the task path. In 'details' mode, the full path of the task. Specify this to get task details. DO NOT use this for tests; use testName instead.")
        val taskPath: String? = null,
        @Description("Filter task results by outcome (summary mode only).")
        val taskOutcome: TaskOutcome? = null,

        @Description("Filter test results. In 'summary' mode, a prefix of the test name. In 'details' mode, the full name of the test. Specify this to get test details. ALWAYS use this with `mode=\"details\"` instead of taskPath to see individual test outputs, metadata, and stack traces. Generic task output lacks test-specific diagnostic information.")
        val testName: String? = null,
        @Description("Filter test results by outcome (summary mode only).")
        val testOutcome: TestOutcome? = null,
        @Description("The index of the test to show if multiple tests have the same name (details mode only).")
        val testIndex: Int? = null,

        @Description("The failure ID to get details for (details mode only). Use this for surgical analysis of build-level failures.")
        val failureId: FailureId? = null,

        @Description("The ProblemId of the problem to look up (details mode only).")
        val problemId: ProblemId? = null,

        @Description("If true, return the last 'limit' lines of the console output instead of the first. Useful for checking the end of long logs. Specify this to get raw console output.")
        val consoleTail: Boolean? = null
    )

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    private fun getLatestBuildsOutput(args: InspectBuildArgs, onlyCompleted: Boolean): String {
        val maxBuilds = args.pagination.limit
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
            appendLine()
            appendLine("To inspect a specific build, call `inspect_build(buildId=\"ID\")`.")
        }
    }

    private fun getTestsOutput(build: Build, args: InspectBuildArgs): String {
        if (args.mode == LookupMode.details) {
            require(!args.testName.isNullOrEmpty()) { "testName is required for details mode" }
            val tests = build.testResults.all.filter { t -> t.testName == args.testName }.toList()

            if (tests.isEmpty()) {
                val isRunning = build is RunningBuild || build.isRunning
                if (isRunning) {
                    return "Test not found. The build is still running, so it may not have been executed yet."
                } else {
                    error("Test not found")
                }
            }

            val targetIndex = args.testIndex ?: 0
            if (tests.size > 1 && targetIndex >= tests.size) {
                return "${tests.size} test executions with this name found. Pass a valid `testIndex` (0 to ${tests.size - 1}) to select one."
            }

            val test = if (tests.size > 1) tests[targetIndex] else tests.single()

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

                appendLine("Test console output:")
                appendLine(test.consoleOutput)
            }
        }

        val testNamePrefix = args.testName ?: ""
        val matched = build.testResults.all
            .filter { tr -> tr.testName.startsWith(testNamePrefix) }
            .filter { tr -> args.testOutcome == null || tr.status == args.testOutcome }
            .toList()

        return buildString {
            appendLine("Total matching results: ${matched.size}")
            appendLine("Test | Outcome | Metadata")
            val paged = paginate(matched, args.pagination, "test results") { tr ->
                "${tr.testName} | ${tr.status} | ${
                    tr.metadata.mapValues {
                        if (it.value.length > 20) it.value.take(20) + " ... (truncated by gradle-mcp)" else it.value
                    }
                }"
            }
            append(paged)
        }
    }

    private fun getTasksOutput(result: Build, args: InspectBuildArgs): String {
        if (args.mode == LookupMode.details) {
            require(!args.taskPath.isNullOrEmpty()) { "taskPath is required for details mode" }
            val taskResult = result.taskResults[args.taskPath]
                ?: throw IllegalArgumentException("Task not found in build ${result.id}: ${args.taskPath}")

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
            val taskPathPrefix = args.taskPath ?: ""
            val tasks = result.taskResults.values
                .asSequence()
                .filter { it.path.startsWith(taskPathPrefix) }
                .filter { args.taskOutcome == null || it.outcome == args.taskOutcome }
                .sortedBy { it.path }
                .toList()

            return buildString {
                if (result.taskOutputCapturingFailed) {
                    appendLine("WARNING: Task output capturing failed for this build. Task outputs may be missing or incomplete.")
                    appendLine()
                }
                appendLine("Task Path | Outcome | Duration")
                val paged = paginate(tasks, args.pagination, "tasks") { task ->
                    "${task.path} | ${task.outcome} | ${task.duration}"
                }
                append(paged)
            }
        }
    }

    private fun getFailuresOutput(build: Build, args: InspectBuildArgs): String {
        val allFailures = if (build is FinishedBuild) build.allFailures else build.allTestFailures

        if (args.mode == LookupMode.details) {
            val failureId = args.failureId ?: error("failureId is required for details mode")
            val failure = allFailures[failureId]
                ?: error("No failure with ID $failureId found for build ${build.id}")

            return buildString {
                failure.writeFailureTree(this)
            }
        }

        val failuresList = allFailures.toList()

        return buildString {
            appendLine("Id | Message")
            val paged = paginate(failuresList, args.pagination, "failures") { (id, failure) ->
                "${id.id} : ${failure.message}"
            }
            append(paged)
            if (build.isRunning && allFailures.isEmpty()) {
                appendLine("No failures found yet. The build is still running.")
            }
        }
    }

    private fun getProblemsOutput(build: Build, args: InspectBuildArgs): String {
        if (args.mode == LookupMode.details) {
            val problemId = args.problemId ?: error("problemId is required for details mode")
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

        val sortedProblems = build.problems.sortedBy { it.definition.severity }

        return buildString {
            appendLine("Severity | Id | Display Name | Occurrences")
            val paged = paginate(sortedProblems, args.pagination, "problems") { problem ->
                "${problem.definition.severity} | ${problem.definition.id} | ${problem.definition.displayName ?: "N/A"} | ${problem.numberOfOccurrences}"
            }
            append(paged)
        }
    }

    private fun getConsoleOutput(build: Build, args: InspectBuildArgs): String {
        return if (args.consoleTail == true) {
            val totalLines = build.consoleOutputLines.size
            val start = (totalLines - args.pagination.offset - args.pagination.limit).coerceAtLeast(0)
            val end = (totalLines - args.pagination.offset).coerceAtLeast(0)
            val pagedLines = build.consoleOutputLines.subList(start, end)

            buildString {
                appendLine("Lines $start to $end of $totalLines lines (tailing mode)")
                appendLine()
                append(pagedLines.joinToString("\n"))
                if (start > 0) {
                    appendLine("\n---")
                    appendLine("To see more previous lines, use: `offset=${args.pagination.offset + args.pagination.limit}`, `limit=${args.pagination.limit}`.")
                    appendLine("---")
                }
            }
        } else {
            paginateText(build.consoleOutputLines.joinToString("\n"), args.pagination)
        }
    }

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
    val inspectBuild by tool<InspectBuildArgs, String>(
        ToolNames.INSPECT_BUILD,
        """
            |Surgically inspects detailed build information, monitors progress, and performs post-mortem diagnostics.
            |ALWAYS use this tool to investigate test failures, task outputs, and build-level errors instead of reading raw console logs.
            |
            |### Surgical Lookup Modes
            |
            |1.  **Summary Mode (`mode="summary"`)**
            |    -   **Best for**: Finding BuildIds, TaskPaths, TestNames, and FailureIds.
            |    -   **Default behaviour**: Shows a high-level dashboard of recent builds if `buildId` is omitted.
            |
            |2.  **Details Mode (`mode="details"`)**
            |    -   **Best for**: Exhaustive analysis of a specific item (requires `testName`, `taskPath`, `failureId`, or `problemId`).
            |    -   **Crucial for Tests**: ALWAYS use `mode="details"` with `testName` to see the individual test case's full output, metadata, and stack trace.
            |
            |### How to Inspect Details
            |
            |- **Individual Tests**:  `testName="FullTestName"`, `mode="details"` (REQUIRED for full output/stack trace).
            |- **Task Outputs**:      `taskPath=":path:to:task"`, `mode="details"`.
            |- **Build Failures**:    `failureId="ID"`, `mode="details"` (use summary mode first to find IDs).
            |- **Problems/Errors**:   `problemId="ID"`, `mode="details"` (use summary mode first to find IDs).
            |- **Full Console**:      `consoleTail=true` (tail) or `consoleTail=false` (head).
            |
            |### Wait & Progress Monitoring
            |- Use `wait` (seconds) with `waitFor` (regex) or `waitForTask` (path) to monitor active builds.
            |- Set `afterCall=true` to only look for events emitted after the tool is called.
        """.trimMargin()
    ) {
        if (it.buildId == null) {
            return@tool getLatestBuildsOutput(it, false)
        }

        var build = buildResults.getBuild(it.buildId)
            ?: throw IllegalArgumentException("Unknown or expired build ID: ${it.buildId}")

        val startLines = if (it.afterCall && build is RunningBuild) build.consoleOutputLines.size else 0

        if (it.wait != null && build is RunningBuild) {
            val runningBuild = build
            val waitForRegex = it.waitFor?.toRegex()
            val waitForTask = it.waitForTask

            withTimeoutOrNull(it.wait.seconds) {
                coroutineScope {
                    select {
                        onTimeout(it.wait.seconds) {}
                        if (waitForRegex != null) {
                            async {
                                if (!it.afterCall && waitForRegex.containsMatchIn(runningBuild.logBuffer)) {
                                    return@async
                                }
                                runningBuild.logLines.firstOrNull { line: String ->
                                    waitForRegex.containsMatchIn(line)
                                }
                            }.onAwait { }
                        }
                        if (waitForTask != null) {
                            async {
                                if (!it.afterCall && runningBuild.completedTaskPaths.contains(waitForTask)) {
                                    return@async
                                }
                                runningBuild.completingTasks.firstOrNull { taskPath: String ->
                                    taskPath == waitForTask
                                }
                            }.onAwait { }
                        }
                        async { runningBuild.awaitFinished() }.onAwait { }
                    }
                    coroutineContext.cancelChildren()
                }
            }
            // re-get build to pick up any changes (e.g. it might have finished)
            build = buildResults.getBuild(it.buildId)!!
        }

        val hasTasks = it.taskPath != null || it.taskOutcome != null
        val hasTests = it.testName != null || it.testOutcome != null || it.testIndex != null
        val hasFailures = it.failureId != null
        val hasProblems = it.problemId != null
        val hasConsole = it.consoleTail != null

        val setOptionsCount = listOf(hasTasks, hasTests, hasFailures, hasProblems, hasConsole).count { it }
        require(setOptionsCount <= 1) { "Only one section (tasks, tests, failures, problems, or console) may be specified at a time." }

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

            if (setOptionsCount == 0) {
                if (it.mode == LookupMode.details) {
                    appendLine("WARNING: Details mode requires a section (e.g. testName, taskPath) to be specified. Showing build summary instead.")
                }
                appendLine("--- Summary ---")
                appendLine(build.toOutputString(true))
                appendLine()
                appendLine("--- How to Inspect Details ---")
                appendLine("- Individual Tests:  `testName=\"FullTestName\"`, `mode=\"details\"` (REQUIRED for full output/stack trace)")
                appendLine("- Task Outputs:      `taskPath=\":path:to:task\"`, `mode=\"details\"`")
                appendLine("- Build Failures:    `failureId=\"ID\"`, `mode=\"details\"` (use summary mode first to find IDs)")
                appendLine("- Problems/Errors:   `problemId=\"ID\"`, `mode=\"details\"` (use summary mode first to find IDs)")
                appendLine("- Full Console:      `consoleTail=true` (tail) or `consoleTail=false` (head)")
                appendLine("- Filtering:         Use `testOutcome`, `taskOutcome` with `mode=\"summary\"` to narrow down results.")
                appendLine()
            } else {
                if (hasTasks) {
                    appendLine("--- Tasks ---")
                    appendLine(getTasksOutput(build, it))
                    appendLine()
                }
                if (hasFailures) {
                    appendLine("--- Failures ---")
                    appendLine(getFailuresOutput(build, it))
                    appendLine()
                }
                if (hasProblems) {
                    appendLine("--- Problems ---")
                    appendLine(getProblemsOutput(build, it))
                    appendLine()
                }
                if (hasTests) {
                    appendLine("--- Tests ---")
                    appendLine(getTestsOutput(build, it))
                    appendLine()
                }
                if (hasConsole) {
                    appendLine("--- Console Output ---")
                    appendLine(getConsoleOutput(build, it))
                    appendLine()
                }
            }
        }
    }
}
