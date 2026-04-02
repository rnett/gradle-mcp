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
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

class GradleBuildLookupTools(val buildResults: BuildManager) : McpServerComponent("Lookup Tools", "Tools for looking up detailed information about past Gradle builds ran by this MCP server.") {

    @Serializable
    enum class LookupMode {
        @Description("Summary list of items. Ideal for high-level overviews and finding IDs.")
        summary,

        @Description("Exhaustive info for a specific item. Required for full test output, stack traces, and console logs.")
        details
    }

    @Serializable
    data class InspectBuildArgs(
        @Description("BuildId to inspect. If omitted, shows the active/recent builds dashboard.")
        val buildId: BuildId? = null,

        @Description("'summary' (default) or 'details'. Use 'details' with testName/taskPath for full output.")
        val mode: LookupMode = LookupMode.summary,

        @Description("Max seconds to wait for a condition. If omitted, returns immediately with current status.")
        val timeout: Double? = null,
        @Description("Wait for the build to finish. Default if 'timeout' is set and no other wait condition is provided.")
        val waitForFinished: Boolean = false,
        @Description("Regex to wait for in build logs (e.g., server started). Requires 'timeout'.")
        val waitFor: String? = null,
        @Description("Task path to wait for completion. Requires 'timeout'.")
        val waitForTask: String? = null,
        @Description("Only match events emitted after this call. Requires 'timeout' and a wait condition.")
        val afterCall: Boolean = false,

        val pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS,

        @Description("Task path prefix (summary) or full/unique-prefix path (details) for task output and outcome.")
        val taskPath: String? = null,
        @Description("Filter task results by outcome (summary mode only).")
        val taskOutcome: TaskOutcome? = null,

        @Description("Test name prefix (summary) or full/unique prefix (details). Use mode='details' for stack traces.")
        val testName: String? = null,
        @Description("Filter test results by outcome (summary mode only).")
        val testOutcome: TestOutcome? = null,
        @Description("Index of test to show when multiple tests share the same name (details mode only).")
        val testIndex: Int? = null,

        @Description("Failure ID to get details for (details mode only).")
        val failureId: FailureId? = null,

        @Description("ProblemId to look up (details mode only).")
        val problemId: ProblemId? = null,

        @Description("true = tail raw console output; false = head. Specify to get raw console output.")
        val consoleTail: Boolean? = null,

        @Description("If specified, write the output to the given file path instead of returning it. The response will include the file absolute path and its length (in characters and lines).")
        val outputFile: String? = null
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
            val exactMatches = allTests.filter { it.fullName == args.testName }

            val test = if (exactMatches.isNotEmpty()) {
                val targetIndex = args.testIndex ?: 0
                if (exactMatches.size > 1 && targetIndex >= exactMatches.size) {
                    return "${exactMatches.size} test executions with this name found. Pass a valid `testIndex` (0 to ${exactMatches.size - 1}) to select one."
                }
                if (exactMatches.size > 1) exactMatches[targetIndex] else exactMatches.single()
            } else {
                val prefixMatches = allTests.filter { it.fullName.startsWith(args.testName) }
                if (prefixMatches.isEmpty()) {
                    val isRunning = build is RunningBuild || build.isRunning
                    if (isRunning) {
                        return "Test not found. The build is still running, so it may not have been executed yet."
                    } else {
                        val allResults = build.testResults.all.toList()

                        // Try suite-based fallback
                        if (args.testName.contains('.')) {
                            val potentialSuite = args.testName.substringBeforeLast('.')
                            val suiteTests = allResults.filter { it.suiteName == potentialSuite }.map { it.fullName }.distinct()
                            if (suiteTests.isNotEmpty()) {
                                return buildString {
                                    appendLine("Test not found: ${args.testName}")
                                    appendLine("\nOther tests in suite '$potentialSuite':")
                                    suiteTests.take(10).forEach { appendLine("  - $it") }
                                    if (suiteTests.size > 10) appendLine("  - ... and ${suiteTests.size - 10} more")
                                }
                            }
                        }

                        val substringMatches = allResults.filter { it.fullName.contains(args.testName, ignoreCase = true) }
                            .map { it.fullName }.distinct()
                        if (substringMatches.isNotEmpty()) {
                            return buildString {
                                appendLine("Test not found: ${args.testName}")
                                appendLine("\nTests containing '${args.testName}':")
                                substringMatches.take(10).forEach { appendLine("  - $it") }
                                if (substringMatches.size > 10) appendLine("  - ... and ${substringMatches.size - 10} more")
                            }
                        } else {
                            error("Test not found: ${args.testName}")
                        }
                    }
                }

                if (prefixMatches.size > 1) {
                    val matches = prefixMatches.map { it.fullName }.distinct()
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
                            return "${prefixMatches.size} test executions for unique prefix match '${prefixMatches.first().fullName}' found. Pass a valid `testIndex` (0 to ${prefixMatches.size - 1}) to select one."
                        }
                        prefixMatches[targetIndex]
                    }
                } else {
                    prefixMatches.single()
                }
            }

            return buildString {
                if (test.fullName != args.testName) {
                    appendLine("Note: Showing details for unique prefix match: ${test.fullName}")
                    appendLine()
                }
                append(test.fullName).append(" - ").appendLine(test.status)
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
                    val isRunning = result.activeOperations.contains(args.taskPath)
                    if (isRunning) {
                        return "Task ${args.taskPath} is currently running. Detailed results and isolated output are only available AFTER the task completes."
                    }

                    val executedTasks = result.taskResults.keys + result.taskOutputs.keys
                    if (executedTasks.isEmpty()) {
                        return "No tasks found in build ${result.id}. This might be because the build failed during configuration."
                    }

                    return buildString {
                        val taskPath = args.taskPath
                        appendLine("Task $taskPath not found in executed tasks for build ${result.id}.")
                        if (result.isRunning && (result.args.allAdditionalArguments.contains(taskPath) || result.args.allAdditionalArguments.any { arg -> arg.endsWith(taskPath) })) {
                            appendLine("The task might still be waiting to execute or is a long-running task like `run` that never completes.")
                        }
                        appendLine()
                        appendLine("Executed tasks:")
                        appendLine(paginate(executedTasks.toList().sorted(), args.pagination, "tasks"))
                    }
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
            val offset = args.pagination.offset
            val limit = args.pagination.limit
            val start = (totalLines - offset - limit).coerceAtLeast(0)
            val end = (totalLines - offset).coerceAtLeast(0)
            val pagedLines = build.consoleOutputLines.subList(start, end)

            paginate(pagedLines, args.pagination, "lines", total = totalLines, isAlreadyPaged = true, isTail = true)
        } else {
            paginateText(build.consoleOutputLines.joinToString("\n"), args.pagination)
        }
    }

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
    val inspectBuild by tool<InspectBuildArgs, String>(
        ToolNames.INSPECT_BUILD,
        """
            |Inspects build information, monitors progress, and performs post-mortem diagnostics; ALWAYS use instead of raw console logs for test failures, task outputs, and build errors.
            |
            |**Note:** Only builds executed by this MCP server session are listed. External Gradle runs are not tracked.
            |
            |### Lookup Modes
            |- **`mode="summary"`** (default): Dashboard/overview; best for finding BuildIds, TestNames, FailureIds. When a build ID is provided, shows a detailed summary including recent error context and currently running tasks.
            |- **`mode="details"`**: Exhaustive analysis; requires `testName`, `taskPath`, `failureId`, or `problemId`.
            |
            |### How to Inspect Details
            |- Tests (incl. console output): `testName="FullTestName"`, `mode="details"` — REQUIRED for stack traces and test output.
            |- Task outputs: `taskPath=":path:to:task"`, `mode="details"`.
            |- Build failures: `failureId="ID"`, `mode="details"` (use summary first to find IDs).
            |- Full console: `consoleTail=true` (tail) or `consoleTail=false` (head).
            |- Pagination: Use `offset` and `limit` to navigate through long console logs or large task/test lists.
            |
            |### Wait & Progress Monitoring
            |Use `timeout` (seconds) with `waitFor` (regex), `waitForTask` (path), or `waitForFinished=true` to monitor active builds.
            |If `timeout` is set but no wait condition is specified, defaults to waiting for the build to finish.
            |Set `afterCall=true` to only match events emitted after this call.
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

        val output = buildString {
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

        if (args.outputFile != null) {
            try {
                val path = Path(args.outputFile)
                path.writeText(output)
                val absolutePath = path.absolutePathString()
                val charCount = output.length
                val lineCount = output.lineSequence().count()
                return@tool "Output written to $absolutePath ($charCount characters, $lineCount lines)"
            } catch (e: Exception) {
                return@tool "Error writing to file ${args.outputFile}: ${e.message}"
            }
        } else {
            return@tool output
        }
    }
}
