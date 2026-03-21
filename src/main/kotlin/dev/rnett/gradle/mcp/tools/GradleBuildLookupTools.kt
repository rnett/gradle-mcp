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
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
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
        @Description("Providing the managed BuildId to inspect authoritatively. If omitted, returns the high-level build dashboard showing active and recently completed builds.")
        val buildId: BuildId? = null,

        @Description("Applying a surgical lookup mode: 'summary' (default) or 'details'. Use 'details' for exhaustive, deep-dive information.")
        val mode: LookupMode = LookupMode.summary,

        @Description("Specifying the maximum number of seconds to wait for the requested condition(s). If omitted, the tool returns immediately with the current build status.")
        val timeout: Double? = null,
        @Description("Waiting for the build to finish authoritatively. This is the default behavior if 'timeout' is provided but no other wait conditions are specified.")
        val waitForFinished: Boolean = false,
        @Description("Providing a regex pattern to wait for in the build logs authoritatively. Ideal for detecting when a server has started or a specific event has occurred. Uses 'timeout' for the maximum wait duration.")
        val waitFor: String? = null,
        @Description("Providing a task path to wait for completion authoritatively. The most surgical way to monitor specific task progress. Uses 'timeout' for the maximum wait duration.")
        val waitForTask: String? = null,
        @Description("Setting to true only looks for matches emitted after this call. Only applies if 'timeout' and a wait condition ('waitFor', 'waitForTask', or 'waitForFinished') are provided.")
        val afterCall: Boolean = false,

        val pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS,

        @Description("Filtering task results. In 'summary' mode, a prefix of the task path. In 'details' mode, providing a full path or unique prefix of the task will return its exhaustive results (outcome, duration, and console output).")
        val taskPath: String? = null,
        @Description("Filtering task results by outcome (summary mode only).")
        val taskOutcome: TaskOutcome? = null,

        @Description("Filtering test results. In 'summary' mode, a prefix of the test name. In 'details' mode, providing a full name or unique prefix of the test will return its exhaustive results (status, duration, stack trace, and console output). ALWAYS use this with `mode=\"details\"` to see individual test outputs; generic task output lacks this metadata.")
        val testName: String? = null,
        @Description("Filtering test results by outcome (summary mode only).")
        val testOutcome: TestOutcome? = null,
        @Description("Specifying the index of the test to show if multiple tests have the same name (details mode only).")
        val testIndex: Int? = null,

        @Description("Providing the failure ID to get details for (details mode only). Use this for surgical analysis of build-level failures.")
        val failureId: FailureId? = null,

        @Description("Providing the ProblemId of the problem to look up (details mode only).")
        val problemId: ProblemId? = null,

        @Description("Returning the last 'limit' lines of the console output instead of the first. Useful for checking the end of long logs. Specify this to get raw console output.")
        val consoleTail: Boolean? = null
    )

    private fun getLatestBuildsOutput(args: InspectBuildArgs, onlyCompleted: Boolean): String {
        val maxBuilds = args.pagination.limit
        val completed = buildResults.latestFinished(maxBuilds)
        val active = if (onlyCompleted) emptyList() else buildResults.listRunningBuilds()

        val all = (completed.map { it to false } + active.map { it to true })
            .sortedByDescending { (b, _) -> b.startTime }
            .take(maxBuilds)

        if (all.isEmpty()) {
            return "No builds found"
        }

        return buildString {
            appendLine("BuildId | Command line | Seconds ago | Status")
            all.forEach { (build, isActive) ->
                val id = build.id
                val commandLine = build.args.renderCommandLine()
                val secondsAgo = Clock.System.now().minus(build.startTime).inWholeSeconds

                append(id).append(" | ")
                append(commandLine).append(" | ")
                append(secondsAgo).append("s ago | ")

                if (isActive) {
                    val rb = build as RunningBuild
                    appendLine(rb.status)
                } else {
                    val br = build as FinishedBuild
                    appendLine(if (br.outcome is BuildOutcome.Success) "SUCCESS" else "FAILURE")
                }
            }
            appendLine()
            appendLine("To inspect a specific build, call `${ToolNames.INSPECT_BUILD}(buildId=\"ID\", mode=\"summary\")`.")
            appendLine("To see detailed failures, tests, or tasks, use `${ToolNames.INSPECT_BUILD}(buildId=\"ID\", mode=\"details\", ...)` with `failureId`, `testName`, or `taskPath` respectively.")
        }
    }

    internal fun getTestsOutput(build: Build, args: InspectBuildArgs): String {
        if (args.mode == LookupMode.details) {
            require(!args.testName.isNullOrEmpty()) { "testName is required for details mode" }
            val allTests = build.testResults.all.toList()
            val exactMatches = allTests.filter { it.testName == args.testName }

            val test = if (exactMatches.isNotEmpty()) {
                val targetIndex = args.testIndex ?: 0
                if (exactMatches.size > 1 && targetIndex >= exactMatches.size) {
                    return "${exactMatches.size} test executions with this name found. Pass a valid `testIndex` (0 to ${exactMatches.size - 1}) to select one."
                }
                if (exactMatches.size > 1) exactMatches[targetIndex] else exactMatches.single()
            } else {
                val prefixMatches = allTests.filter { it.testName.startsWith(args.testName) }
                if (prefixMatches.isEmpty()) {
                    val isRunning = build is RunningBuild || build.isRunning
                    if (isRunning) {
                        return "Test not found. The build is still running, so it may not have been executed yet."
                    } else {
                        error("Test not found: ${args.testName}")
                    }
                }

                if (prefixMatches.size > 1) {
                    val matches = prefixMatches.map { it.testName }.distinct()
                    if (matches.size > 1) {
                        val firstMatches = matches.take(10)
                        return buildString {
                            appendLine("Multiple tests match prefix '${args.testName}':")
                            firstMatches.forEach { appendLine("  - $it") }
                            if (matches.size > 10) appendLine("  - ... and ${matches.size - 10} more")
                            appendLine("\nPlease provide a full test name or use `mode=\"summary\"` to see all results.")
                        }
                    } else {
                        // All matches have same name, but multiple executions
                        val targetIndex = args.testIndex ?: 0
                        if (prefixMatches.size > 1 && targetIndex >= prefixMatches.size) {
                            return "${prefixMatches.size} test executions for unique prefix match '${prefixMatches.first().testName}' found. Pass a valid `testIndex` (0 to ${prefixMatches.size - 1}) to select one."
                        }
                        prefixMatches[targetIndex]
                    }
                } else {
                    prefixMatches.single()
                }
            }

            return buildString {
                if (test.testName != args.testName) {
                    appendLine("Note: Showing details for unique prefix match: ${test.testName}")
                    appendLine()
                }
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
            .sortedByDescending { it.executionDuration }
            .toList()

        return buildString {
            appendLine("Total matching results: ${matched.size}")
            appendLine("Test | Outcome | Duration | Metadata")
            val paged = paginate(matched, args.pagination, "test results") { tr ->
                "${tr.testName} | ${tr.status} | ${tr.executionDuration} | ${
                    tr.metadata.mapValues {
                        if (it.value.length > 20) it.value.take(20) + " ... (truncated by gradle-mcp)" else it.value
                    }
                }"
            }
            append(paged)
        }
    }

    internal fun getTasksOutput(result: Build, args: InspectBuildArgs): String {
        if (args.mode == LookupMode.details) {
            require(!args.taskPath.isNullOrEmpty()) { "taskPath is required for details mode" }
            val exactMatch = result.taskResults[args.taskPath]

            val taskResult = if (exactMatch != null) {
                exactMatch
            } else {
                val prefixMatches = result.taskResults.values.filter { it.path.startsWith(args.taskPath) }
                if (prefixMatches.isEmpty()) {
                    throw IllegalArgumentException("Task not found in build ${result.id}: ${args.taskPath}")
                }

                if (prefixMatches.size > 1) {
                    val matches = prefixMatches.map { it.path }.distinct().take(10)
                    return buildString {
                        appendLine("Multiple tasks match prefix '${args.taskPath}':")
                        matches.forEach { appendLine("  - $it") }
                        if (prefixMatches.size > 10) appendLine("  - ... and ${prefixMatches.size - 10} more")
                        appendLine("\nPlease provide a full task path or use `mode=\"summary\"` to see all results.")
                    }
                }
                prefixMatches.single()
            }

            return buildString {
                if (taskResult.path != args.taskPath) {
                    appendLine("Note: Showing details for unique prefix match: ${taskResult.path}")
                    appendLine()
                }
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

    internal fun getFailuresOutput(build: Build, args: InspectBuildArgs): String {
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

    internal fun getProblemsOutput(build: Build, args: InspectBuildArgs): String {
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

    internal fun getConsoleOutput(build: Build, args: InspectBuildArgs): String {
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
            |    -   **Crucial for Tests**: ALWAYS use `mode="details"` with `testName` to see the individual test case's console output, metadata, and stack trace. THIS IS THE ONLY WAY TO SEE THE TEST'S CONSOLE OUTPUT.
            |
            |### How to Inspect Details
            |
            |- **Individual Tests (INCLUDING TEST CONSOLE OUTPUT)**:  `testName="FullTestName"`, `mode="details"` (REQUIRED for full output/stack trace).
            |- **Task Outputs**:      `taskPath=":path:to:task"`, `mode="details"`.
            |- **Build Failures**:    `failureId="ID"`, `mode="details"` (use summary mode first to find IDs).
            |- **Problems/Errors**:   `problemId="ID"`, `mode="details"` (use summary mode first to find IDs).
            |- **Full Console (EXCEPT TESTS)**:      `consoleTail=true` (tail) or `consoleTail=false` (head).
            |
            |### Wait & Progress Monitoring
            |- Use `timeout` (seconds) with `waitFor` (regex), `waitForTask` (path), or `waitForFinished` (boolean) to monitor active builds and real-time test progress (e.g., pass/fail counts).
            |- If `timeout` is omitted, the tool returns immediately with the current build status.
            |- If `timeout` is provided but `waitFor` and `waitForTask` are omitted, the tool defaults to waiting for the build to finish (equivalent to `waitForFinished=true`).
            |- If the build finishes before the requested regex or task is found, the tool returns an error.
            |- Set `afterCall=true` to only look for events emitted after the tool is called.
        """.trimMargin()
    ) { args ->
        if (args.buildId == null) {
            return@tool getLatestBuildsOutput(args, false)
        }

        var build = buildResults.getBuild(args.buildId)
            ?: throw IllegalArgumentException("Unknown or expired build ID: ${args.buildId}")

        val startLines = if (args.afterCall && build is RunningBuild) build.consoleOutputLines.size else 0

        if (args.timeout != null && build is RunningBuild) {
            val runningBuild = build
            val waitForRegex = args.waitFor?.toRegex()
            val waitForTask = args.waitForTask
            val waitForFinished = args.waitForFinished || (waitForRegex == null && waitForTask == null)

            val waitResult = withTimeoutOrNull(args.timeout.seconds) {
                coroutineScope {
                    val progressJob = launch(Dispatchers.Default) {
                        runningBuild.progressTracker.progress.collectLatest { p ->
                            progressReporter.report(p.progress, 1.0, p.message)
                        }
                    }

                    try {
                        select {
                            if (waitForRegex != null) {
                                async {
                                    if (!args.afterCall && waitForRegex.containsMatchIn(runningBuild.consoleOutput.toString())) {
                                        return@async true
                                    }
                                    runningBuild.logLines.firstOrNull { line: String ->
                                        waitForRegex.containsMatchIn(line)
                                    } != null
                                }.onAwait { it }
                            }
                            if (waitForTask != null) {
                                async {
                                    if (!args.afterCall && runningBuild.completedTaskPaths.contains(waitForTask)) {
                                        return@async true
                                    }
                                    runningBuild.completingTasks.firstOrNull { it == waitForTask } != null
                                }.onAwait { it }
                            }
                            async { runningBuild.awaitFinished() }.onAwait {
                                if (waitForRegex != null || waitForTask != null) {
                                    // Check if the condition was met in the very last moment (e.g. in the final log lines)
                                    val finalMatched = if (waitForRegex != null) {
                                        waitForRegex.containsMatchIn(runningBuild.consoleOutput.toString())
                                    } else {
                                        runningBuild.completedTaskPaths.contains(waitForTask)
                                    }

                                    if (!finalMatched) {
                                        val condition = if (waitForRegex != null) "matching regex: ${args.waitFor}" else "completing task: ${args.waitForTask}"
                                        throw RuntimeException("Build finished without $condition")
                                    }
                                    true
                                } else {
                                    waitForFinished
                                }
                            }
                        }
                    } finally {
                        progressJob.cancelAndJoin()
                    }
                }
            }
            if (waitResult == false) {
                // This should only happen if waitForFinished was false but the build finished.
                // But if waitForFinished was true and it finished, it returns true.
                // If regex/task was set and it finished, it throws.
                // So this branch should be unreachable or return immediately.
            }

            // re-get build to pick up any changes (e.g. it might have finished)
            val buildId = args.buildId
            build = requireNotNull(buildResults.getBuild(buildId)) { "No build found with ID $buildId" }
        }

        val hasTasks = args.taskPath != null || args.taskOutcome != null
        val hasTests = args.testName != null || args.testOutcome != null || args.testIndex != null
        val hasFailures = args.failureId != null
        val hasProblems = args.problemId != null
        val hasConsole = args.consoleTail != null

        val setOptionsCount = listOf(hasTasks, hasTests, hasFailures, hasProblems, hasConsole).count { it }
        require(setOptionsCount <= 1) { "Only one section (tasks, tests, failures, problems, or console) may be specified at a time." }

        buildString {
            if (build is RunningBuild) {
                appendLine("--- BUILD IN PROGRESS ---")
            } else {
                appendLine("--- BUILD FINISHED ---")
            }

            val waitForRegex = args.waitFor?.toRegex()
            if (waitForRegex != null) {
                val matchingLines = build.consoleOutputLines.drop(startLines).filter { line -> line.isNotBlank() && waitForRegex.containsMatchIn(line) }
                if (matchingLines.isNotEmpty()) {
                    appendLine("Matching lines for '${args.waitFor}':")
                    matchingLines.forEach { appendLine("    $it") }
                    appendLine()
                } else if (args.timeout != null) {
                    appendLine("No matched lines - build completed or wait timed out")
                    appendLine()
                }
            }

            if (setOptionsCount == 0) {
                if (args.mode == LookupMode.details) {
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
                appendLine("- Full Console:      `consoleTail=true`, `consoleTail=false` (head)")
                appendLine("- Filtering:         Use `testOutcome`, `taskOutcome` with `mode=\"summary\"` to narrow down results.")
                appendLine()
            } else {
                if (hasTasks) {
                    appendLine("--- Tasks ---")
                    appendLine(getTasksOutput(build, args))
                    appendLine()
                }
                if (hasFailures) {
                    appendLine("--- Failures ---")
                    appendLine(getFailuresOutput(build, args))
                    appendLine()
                }
                if (hasProblems) {
                    appendLine("--- Problems ---")
                    appendLine(getProblemsOutput(build, args))
                    appendLine()
                }
                if (hasTests) {
                    appendLine("--- Tests ---")
                    appendLine(getTestsOutput(build, args))
                    appendLine()
                }
                if (hasConsole) {
                    appendLine("--- Console Output ---")
                    appendLine(getConsoleOutput(build, args))
                    appendLine()
                }
            }
        }
    }
}
