package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.build.Build
import dev.rnett.gradle.mcp.gradle.build.BuildComponentOutcome
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
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
    enum class QueryKind { DASHBOARD, CONSOLE, TASKS, TESTS, FAILURES, PROBLEMS }

    @Serializable
    data class QueryBuildArgs(
        @Description("BuildId to query. If omitted and kind=DASHBOARD, shows recent builds.")
        val buildId: BuildId? = null,

        @Description("The aspect of the build to query. Default is DASHBOARD.")
        val kind: QueryKind = QueryKind.DASHBOARD,

        @Description("A query string. Acts as a prefix filter for tasks/tests, or a regex for CONSOLE. For failures/problems, it must be the exact ID.")
        val query: String? = null,

        @Description("Output file to save the result. Useful for large console logs.")
        val outputFile: String? = null,

        @Description("Pagination settings. offset = zero-based start index (default 0); limit = max items/lines to return.")
        val pagination: PaginationInput = PaginationInput.DEFAULT_ITEMS,

        @Description("Filter tasks or tests by outcome (e.g. SUCCESS, FAILED).")
        val outcome: BuildComponentOutcome? = null,

        @Description("Index of test to show when multiple tests share the same name.")
        val testIndex: Int? = null,

        @Description("Filter tests by task path (prefix). Only applicable when kind=TESTS.")
        val taskPath: String? = null
    )

    @Serializable
    data class WaitBuildArgs(
        @Description("BuildId to wait for.")
        val buildId: BuildId,

        @Description("Max seconds to wait. Default is 600.0.")
        val timeout: Double = 600.0,

        @Description("Wait for the build to finish. Default if no other wait condition is provided.")
        val waitForFinished: Boolean = false,

        @Description("Regex to wait for in build logs (e.g., server started).")
        val waitFor: String? = null,

        @Description("Task path to wait for completion.")
        val waitForTask: String? = null,

        @Description("Only match events emitted after this call.")
        val afterCall: Boolean = false
    )

    private fun getLatestBuildsOutput(args: QueryBuildArgs, onlyCompleted: Boolean): String {
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
                    appendLine("BUILD IN PROGRESS")
                } else {
                    val br = build as FinishedBuild
                    appendLine(if (br.outcome is BuildOutcome.Success) "SUCCESS" else "FAILURE")
                }
            }
            appendLine()
            appendLine("To inspect a specific build, call `${ToolNames.QUERY_BUILD}(buildId=\"ID\", kind=\"DASHBOARD\")`.")
            appendLine("To see detailed failures, tests, or tasks, use `${ToolNames.QUERY_BUILD}(buildId=\"ID\", kind=\"...\", query=\"...\")`.")
        }
    }

    private fun getDashboardOutput(build: Build, args: QueryBuildArgs): String {
        return buildString {
            if (build is RunningBuild) {
                appendLine("--- BUILD IN PROGRESS ---")
            } else {
                appendLine("--- BUILD FINISHED ---")
            }
            appendLine("--- Summary ---")
            appendLine(build.toOutputString(true))
            appendLine()
            appendLine("--- How to Inspect Details ---")
            appendLine("- Task Outputs:      `kind=\"TASKS\"`, `query=\":path:to:task\"`")
            appendLine("- Build Failures:    `kind=\"FAILURES\"`, `query=\"ID\"`")
            appendLine("- Problems/Errors:   `kind=\"PROBLEMS\"`, `query=\"ID\"`")
            appendLine("- Full Console:      `kind=\"CONSOLE\"`")
            appendLine()
        }
    }

    internal fun getTestsOutput(build: Build, args: QueryBuildArgs): String {
        val query = args.query ?: ""
        val matched = build.testResults.all
            .filter { tr -> tr.fullName.startsWith(query) }
            .filter { tr -> args.taskPath == null || tr.taskPath?.startsWith(args.taskPath) == true }
            .filter { tr -> args.outcome == null || tr.status == args.outcome }
            .sortedByDescending { it.executionDuration }
            .toList()

        // Auto-expand logic
        if (query.isNotEmpty()) {
            val uniqueNames = matched.map { it.fullName }.distinct()
            if (uniqueNames.size == 1) {
                val targetIndex = args.testIndex ?: 0
                if (matched.size > 1 && targetIndex >= matched.size) {
                    return "${matched.size} test executions for unique prefix match '${uniqueNames.first()}' found. Pass a valid `testIndex` (0 to ${matched.size - 1}) to select one.\nSee query_build(kind='CONSOLE', buildId='${build.id}') for full logs."
                }
                val test = matched[targetIndex]
                return buildString {
                    if (test.fullName != query) {
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
                    appendLine("\nSee query_build(kind='CONSOLE', buildId='${build.id}') for full logs.")
                }
            } else if (uniqueNames.size > 1) {
                val paged = paginate(matched, args.pagination, "test results") { tr ->
                    "${tr.fullName} | ${tr.status} | ${tr.executionDuration}"
                }
                return "Multiple tests match prefix '$query':\n$paged\nPlease provide a full test name to view details.\nSee query_build(kind='CONSOLE', buildId='${build.id}') for full logs."
            }
        }

        return buildString {
            if (query.isNotEmpty() && matched.isEmpty()) {
                appendLine("Test not found: $query")
                val suiteName = query.substringBeforeLast('.', "")
                if (suiteName.isNotEmpty()) {
                    val suiteTests = build.testResults.all.filter { it.suiteName == suiteName }.toList()
                    if (suiteTests.isNotEmpty()) {
                        appendLine("Other tests in suite '$suiteName':")
                        suiteTests.take(10).forEach { appendLine("  - ${it.fullName}") }
                        if (suiteTests.size > 10) appendLine("  - ...")
                    }
                }
                val containsMatches = build.testResults.all.filter { it.fullName.contains(query, ignoreCase = true) }.toList()
                if (containsMatches.isNotEmpty()) {
                    appendLine("Tests containing '$query':")
                    containsMatches.take(10).forEach { appendLine("  - ${it.fullName}") }
                    if (containsMatches.size > 10) appendLine("  - ...")
                }
                if (build.isRunning && suiteName.isEmpty() && containsMatches.isEmpty()) {
                    appendLine("The build is still running, so it may not have been executed yet.")
                }
            } else {
                appendLine("Total matching results: ${matched.size}")
                appendLine("Test | Outcome | Duration | Task | Metadata")
                val paged = paginate(matched, args.pagination, "test results") { tr ->
                    "${tr.fullName} | ${tr.status} | ${tr.executionDuration} | ${tr.taskPath ?: "N/A"} | ${tr.metadata.mapValues { if (it.value.length > 20) it.value.take(20) + " ... (truncated by gradle-mcp)" else it.value }}"
                }
                append(paged)
            }
            appendLine("\nSee query_build(kind='CONSOLE', buildId='${build.id}') for full logs.")
        }
    }

    internal fun getTasksOutput(result: Build, args: QueryBuildArgs): String {
        val query = args.query ?: ""
        val tasks = result.taskResults.values
            .asSequence()
            .filter { it.path.startsWith(query) }
            .filter { args.outcome == null || it.outcome == args.outcome }
            .sortedBy { it.path }
            .toList()

        // Auto-expand logic
        if (query.isNotEmpty()) {
            if (tasks.size == 1) {
                val taskResult = tasks.single()
                return buildString {
                    if (taskResult.path != query) {
                        appendLine("Note: Showing details for unique prefix match: ${taskResult.path}")
                        appendLine()
                    }
                    appendLine("Task: ${taskResult.path}")
                    appendLine("Outcome: ${taskResult.outcome}")
                    appendLine("Duration: ${taskResult.duration}")
                    taskResult.provenance?.let { appendLine("Provenance: $it") }
                    appendLine()

                    val relatedTests = result.testResults.all.filter { it.taskPath == taskResult.path }.toList()
                    if (relatedTests.isNotEmpty()) {
                        val passedCount = relatedTests.count { it.status == BuildComponentOutcome.SUCCESS }
                        val failedCount = relatedTests.count { it.status == BuildComponentOutcome.FAILED }
                        val skippedCount = relatedTests.count { it.status == BuildComponentOutcome.SKIPPED }
                        val cancelledCount = relatedTests.count { it.status == BuildComponentOutcome.CANCELLED }

                        appendLine()
                        appendLine("Tests: ${relatedTests.size} (${passedCount} passed, ${failedCount} failed, ${skippedCount} skipped${if (cancelledCount > 0) ", $cancelledCount cancelled" else ""})")
                        appendLine("To list all tests for this task, use `query_build(kind='TESTS', query='')` and check filtering.")
                        appendLine()
                    }

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
                    appendLine("\nSee query_build(kind='CONSOLE', buildId='${result.id}') for full logs.")
                }
            } else if (tasks.size > 1) {
                val paged = paginate(tasks, args.pagination, "tasks") { task ->
                    "${task.path}${task.provenance?.let { " ($it)" } ?: ""} | ${task.outcome} | ${task.duration}"
                }
                return "Multiple tasks match prefix '$query':\n$paged\nPlease provide a full task path to view details.\nSee query_build(kind='CONSOLE', buildId='${result.id}') for full logs."
            }
        }

        val tasksWithTests = result.testResults.all.mapNotNull { it.taskPath }.toSet()

        return buildString {
            if (query.isNotEmpty() && tasks.isEmpty()) {
                appendLine("No tasks match prefix '$query'.")
            } else {
                if (result.taskOutputCapturingFailed) {
                    appendLine("WARNING: Task output capturing failed for this build. Task outputs may be missing or incomplete.")
                    appendLine()
                }
                appendLine("Task Path | Outcome | Duration")
                val paged = paginate(tasks, args.pagination, "tasks") { task ->
                    "${task.path}${task.provenance?.let { " ($it)" } ?: ""} | ${task.outcome} | ${task.duration}"
                }
                append(paged)

                if (tasks.any { it.path in tasksWithTests }) {
                    appendLine()
                    appendLine("Some of these tasks ran tests.")
                }
            }
            appendLine("\nSee query_build(kind='CONSOLE', buildId='${result.id}') for full logs.")
        }
    }

    internal fun getFailuresOutput(build: Build, args: QueryBuildArgs): String {
        val allFailures = if (build is FinishedBuild) build.allFailures else build.allTestFailures
        val query = args.query

        if (!query.isNullOrEmpty()) {
            val failure = allFailures.entries.firstOrNull { it.key.id == query }?.value
            if (failure != null) {
                return buildString {
                    failure.writeFailureTree(this)
                    appendLine("\nSee query_build(kind='CONSOLE', buildId='${build.id}') for full logs.")
                }
            } else {
                return "No failure with ID $query found.\nSee query_build(kind='CONSOLE', buildId='${build.id}') for full logs."
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
            appendLine("\nSee query_build(kind='CONSOLE', buildId='${build.id}') for full logs.")
        }
    }

    internal fun getProblemsOutput(build: Build, args: QueryBuildArgs): String {
        val query = args.query

        if (!query.isNullOrEmpty()) {
            val problem = build.problems.firstOrNull { p -> p.definition.id.id == query }
            if (problem != null) {
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
                    appendLine("\nSee query_build(kind='CONSOLE', buildId='${build.id}') for full logs.")
                }
            } else {
                return "No problem with ID $query found.\nSee query_build(kind='CONSOLE', buildId='${build.id}') for full logs."
            }
        }

        val sortedProblems = build.problems.sortedBy { it.definition.severity }

        return buildString {
            appendLine("Severity | Id | Display Name | Occurrences")
            val paged = paginate(sortedProblems, args.pagination, "problems") { problem ->
                "${problem.definition.severity} | ${problem.definition.id} | ${problem.definition.displayName ?: "N/A"} | ${problem.numberOfOccurrences}"
            }
            append(paged)
            appendLine("\nSee query_build(kind='CONSOLE', buildId='${build.id}') for full logs.")
        }
    }

    internal fun getConsoleOutput(build: Build, args: QueryBuildArgs): String {
        val output = build.consoleOutput.toString()
        val query = args.query

        if (!query.isNullOrEmpty()) {
            val regex = query.toRegex()
            val matchingLines = mutableListOf<String>()
            val limit = args.pagination.limit
            val offset = args.pagination.offset

            var scanPos = 0
            var lineNum = 1
            while (scanPos < output.length) {
                val lineEnd = output.indexOf('\n', scanPos).let { if (it == -1) output.length else it }
                val line = output.substring(scanPos, lineEnd)
                if (regex.containsMatchIn(line)) {
                    matchingLines.add("$lineNum: $line")
                }
                scanPos = lineEnd + 1
                lineNum++
            }

            val pagedLines = matchingLines.drop(offset).take(limit)
            val hasMore = offset + limit < matchingLines.size
            return paginate(pagedLines, args.pagination, "matched lines", total = matchingLines.size, isAlreadyPaged = true, hasMore = hasMore)
        }

        val limit = args.pagination.limit
        val offset = args.pagination.offset

        // If offset is 0, we do a tail.
        if (offset == 0) {
            val tailLines = mutableListOf<String>()
            var pos = output.length
            if (pos > 0 && output[pos - 1] == '\n') pos-- // skip trailing newline

            // For outputFile, limit is Int.MAX_VALUE. We want the full log.
            // For normal tool calls, if limit is default (usually 20), we show that.
            val actualLimit = if (limit == Int.MAX_VALUE && args.outputFile == null) 20 else limit

            while (tailLines.size < actualLimit && pos >= 0) {
                val lineStart = if (pos == 0) 0 else output.lastIndexOf('\n', pos - 1) + 1
                tailLines.add(0, output.substring(lineStart, pos))
                pos = lineStart - 1
            }
            val hasMore = pos >= 0

            return paginate(tailLines, args.pagination, "lines", total = null, isAlreadyPaged = true, isTail = true, hasMore = hasMore)
        } else {
            // For head
            val needed = offset + limit

            val headLines = mutableListOf<String>()
            var pos = 0
            while (headLines.size < needed && pos <= output.length) {
                val lineEnd = output.indexOf('\n', pos).let { if (it == -1) output.length else it }
                headLines.add(output.substring(pos, lineEnd))
                pos = lineEnd + 1
            }
            val hasMore = pos <= output.length && pos > 0

            val pagedHead = headLines.drop(offset).take(limit)
            return paginate(pagedHead, args.pagination, "lines", total = null, isAlreadyPaged = true, hasMore = hasMore)
        }
    }

    private fun getConsoleTail(build: Build, lines: Int = 5): String {
        val output = build.consoleOutput.toString()
        val tailLines = mutableListOf<String>()
        var pos = output.length
        if (pos > 0 && output[pos - 1] == '\n') pos-- // skip trailing newline
        while (tailLines.size < lines && pos >= 0) {
            val lineStart = if (pos == 0) 0 else output.lastIndexOf('\n', pos - 1) + 1
            tailLines.add(0, output.substring(lineStart, pos))
            pos = lineStart - 1
        }
        return tailLines.joinToString("\n")
    }

    private fun waitForMatch(build: Build, args: WaitBuildArgs): String? {
        val waitForRegex = args.waitFor?.toRegex()
        val waitForTask = args.waitForTask

        if (waitForRegex != null) {
            val console = build.consoleOutput.toString()
            if (waitForRegex.containsMatchIn(console)) return "Matched regex: ${args.waitFor}"
        }

        if (waitForTask != null) {
            if (build is RunningBuild && build.completedTaskPaths.contains(waitForTask)) return "Matched task: $waitForTask"
            if (build is FinishedBuild && build.taskResults.containsKey(waitForTask)) return "Matched task: $waitForTask"
        }

        return null
    }

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
    val queryBuild by tool<QueryBuildArgs, String>(
        ToolNames.QUERY_BUILD,
        """|
            |Queries build information.
            |
            |If called with no arguments, returns a dashboard of recent builds.
            |
            |### Query Kinds
            |- DASHBOARD (default): Recent builds. If buildId is provided, shows a detailed summary of that build.
            |- CONSOLE: Console logs. `query` acts as a regex filter.
            |- TASKS: Task outputs. `query` acts as a prefix filter on the task path.
            |- TESTS: Test outputs. `query` acts as a prefix filter on the test name.
            |- FAILURES: Build failures. `query` is the exact FailureId.
            |- PROBLEMS: Compilation/configuration problems. `query` is the exact ProblemId.
            |
            |If a query for TASKS, TESTS, FAILURES, or PROBLEMS matches exactly one item, it auto-expands to full details. Otherwise, it returns a summary list with a hint to refine the query.
            |See query_build(kind='CONSOLE', buildId='...') for full logs.
        """.trimMargin()
    ) { inputArgs ->
        val args = if (inputArgs.outputFile != null) {
            inputArgs.copy(pagination = PaginationInput(offset = 0, limit = Int.MAX_VALUE))
        } else {
            inputArgs
        }

        val output = if (args.buildId == null) {
            getLatestBuildsOutput(args, false)
        } else {
            val build = buildResults.getBuild(args.buildId)
                ?: throw IllegalArgumentException("Unknown or expired build ID: ${args.buildId}")

            val rawOutput = when (args.kind) {
                QueryKind.DASHBOARD -> getDashboardOutput(build, args)
                QueryKind.CONSOLE -> getConsoleOutput(build, args)
                QueryKind.TASKS -> getTasksOutput(build, args)
                QueryKind.TESTS -> getTestsOutput(build, args)
                QueryKind.FAILURES -> getFailuresOutput(build, args)
                QueryKind.PROBLEMS -> getProblemsOutput(build, args)
            }

            if (args.kind == QueryKind.DASHBOARD) {
                rawOutput
            } else {
                buildString {
                    if (build is RunningBuild) {
                        appendLine("--- BUILD IN PROGRESS ---")
                    } else {
                        appendLine("--- BUILD FINISHED ---")
                    }
                    val header = when (args.kind) {
                        QueryKind.CONSOLE -> "--- Console Output ---"
                        QueryKind.TASKS -> "--- Tasks ---"
                        QueryKind.TESTS -> "--- Tests ---"
                        QueryKind.FAILURES -> "--- Failures ---"
                        QueryKind.PROBLEMS -> "--- Problems ---"
                        QueryKind.DASHBOARD -> ""
                    }
                    if (header.isNotEmpty()) {
                        appendLine(header)
                    }
                    appendLine(rawOutput)
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
                "Output written to $absolutePath ($charCount characters, $lineCount lines)"
            } catch (e: Exception) {
                "Error writing to file ${args.outputFile}: ${e.message}"
            }
        } else {
            output
        }
    }

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
    val waitBuild by tool<WaitBuildArgs, String>(
        ToolNames.WAIT_BUILD,
        """|
            |Waits for a background build to reach a specific condition and returns the final console tail.
            |
            |Use `timeout` (seconds) with `waitFor` (regex), `waitForTask` (path), or `waitForFinished=true` to monitor active builds.
            |If no wait condition (regex or task) is provided, it defaults to waiting for the build to finish.
            |
            |Set `afterCall=true` to only match events emitted after this call.
            |See query_build(kind='CONSOLE', buildId='...') for full logs.
        """.trimMargin()
    ) { args ->
        var build = buildResults.getBuild(args.buildId)
            ?: throw IllegalArgumentException("Unknown or expired build ID: ${args.buildId}")

        val waitForRegex = args.waitFor?.toRegex()
        val waitForTask = args.waitForTask

        if (build is RunningBuild) {
            val runningBuild = build

            val waitResult = withTimeoutOrNull(args.timeout.seconds) {
                coroutineScope {
                    val progressJob = launch(Dispatchers.Default) {
                        runningBuild.progressTracker.progress.collectLatest { p ->
                            progressReporter.report(p.progress, 1.0, p.message)
                        }
                    }

                    val regexJob = if (waitForRegex != null) async {
                        if (!args.afterCall && waitForRegex.containsMatchIn(runningBuild.consoleOutput.toString())) {
                            return@async true
                        }
                        runningBuild.logLines.firstOrNull { line: String ->
                            waitForRegex.containsMatchIn(line)
                        } != null
                    } else null

                    val taskJob = if (waitForTask != null) async {
                        if (!args.afterCall && runningBuild.completedTaskPaths.contains(waitForTask)) {
                            return@async true
                        }
                        runningBuild.completingTasks.firstOrNull { it == waitForTask } != null
                    } else null

                    val finishJob = async { runningBuild.awaitFinished() }

                    try {
                        select<Boolean> {
                            regexJob?.onAwait { it }
                            taskJob?.onAwait { it }
                            finishJob.onAwait {
                                if (waitForRegex != null || waitForTask != null) {
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
                                    true
                                }
                            }
                        }
                    } finally {
                        regexJob?.cancel()
                        taskJob?.cancel()
                        finishJob.cancel()
                        progressJob.cancelAndJoin()
                    }
                }
            }
            if (waitResult == null) {
                return@tool "Wait timed out after ${args.timeout}s. Current console tail:\n\n" + getConsoleTail(build) + "\n\nSee query_build(kind='CONSOLE', buildId='${args.buildId}') for full logs."
            }
            build = buildResults.getBuild(args.buildId)!!
        } else {
            // Already finished, check condition
            if (waitForRegex != null || waitForTask != null) {
                val match = waitForMatch(build, args)
                if (match == null) {
                    val condition = if (waitForRegex != null) "matching regex: ${args.waitFor}" else "completing task: ${args.waitForTask}"
                    throw RuntimeException("Build already finished without $condition")
                }
            }
        }

        buildString {
            if (build is RunningBuild) {
                appendLine("Wait condition met. Build is still running.")
            } else {
                appendLine("Build finished with outcome: ${(build as FinishedBuild).outcome.javaClass.simpleName}")
            }
            appendLine("Console Tail:")
            appendLine(getConsoleTail(build))
            appendLine("\nSee query_build(kind='CONSOLE', buildId='${args.buildId}') for full logs.")
        }
    }
}
