package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemId
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import dev.rnett.gradle.mcp.gradle.build.Build
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import kotlinx.serialization.Serializable

object OutputFormatter {
    fun <T> listResults(results: Collection<T>, limit: Int, indent: String = "", item: (T) -> String): String {
        if (results.isEmpty()) return ""
        return buildString {
            results.take(limit).forEach {
                append("$indent - ")
                appendLine(item(it))
            }
            if (results.size > limit) {
                appendLine("$indent ... ${results.size - limit} more not shown.")
            }
        }
    }
}


fun Build.toOutputString(includeArgs: Boolean = true): String {
    if (args.isHelp || args.isVersion) {
        return consoleOutput.toString()
    }
    return buildString {
        appendLine("Gradle MCP Build ID: $id")
        if (includeArgs) {
            appendLine("Command line: ${args.renderCommandLine()}")
        }
        appendLine(
            "Status: ${
                if (this@toOutputString is FinishedBuild) {
                    if (this@toOutputString.outcome is BuildOutcome.Success) "Success" else "Failure"
                } else {
                    "Running (still running, some information may be incomplete)"
                }
            }"
        )

        appendLine()
        val buildFailures = if (this@toOutputString is FinishedBuild) {
            allBuildFailures.values
        } else {
            testResults.failed.asSequence().flatMap { it.failures.orEmpty().asSequence() }.toList()
        }

        if (buildFailures.isNotEmpty()) {
            appendLine("Failures: ${buildFailures.size} - use `${ToolNames.INSPECT_BUILD}` tool for more details")
            appendLine(OutputFormatter.listResults(buildFailures, 10) {
                buildString {
                    append(it.id.id)
                    if (it.message != null)
                        append(": ${it.message}")
                }
            })
        }

        fun formatProblem(e: Pair<ProblemId, ProblemsSummary.ProblemSummary>) = buildString {
            append(e.second.displayName ?: "N/A")
            append(" ")
            append("(id: ${e.first.id})")
            append(" ")
            append(" - occurred ${e.second.occurences} times")
        }

        val problemsSummary = problems.toSummary()
        appendLine("Problems:     ${problemsSummary.totalCount} - use `${ToolNames.INSPECT_BUILD}` tool for more details")
        if (problemsSummary.errorCounts.isNotEmpty()) {
            appendLine("  Errors:     ${problemsSummary.errorCounts.size}")
            appendLine(OutputFormatter.listResults(problemsSummary.errorCounts.toList(), 5, item = ::formatProblem))
        }
        if (problemsSummary.warningCounts.isNotEmpty()) {
            appendLine("  Warnings:   ${problemsSummary.warningCounts.size}")
            appendLine(OutputFormatter.listResults(problemsSummary.warningCounts.toList(), 3, item = ::formatProblem))
        }
        if (problemsSummary.adviceCounts.isNotEmpty()) {
            appendLine("  Advice:     ${problemsSummary.adviceCounts.size}")
            appendLine(OutputFormatter.listResults(problemsSummary.adviceCounts.toList(), 3, item = ::formatProblem))
        }
        if (problemsSummary.otherCounts.isNotEmpty()) {
            appendLine("  Other:      ${problemsSummary.otherCounts.size}")
            appendLine(OutputFormatter.listResults(problemsSummary.otherCounts.toList(), 3, item = ::formatProblem))
        }
        appendLine()

        appendLine("Tests:      ${testResults.totalCount} - use `${ToolNames.INSPECT_BUILD}` tool for more details")
        appendLine("  Passed:   ${testResults.passed.size}")
        appendLine("  Skipped:  ${testResults.skipped.size}")
        appendLine("  Failed:   ${testResults.failed.size}")
        appendLine(OutputFormatter.listResults(testResults.failed, 20, "  ") {
            it.testName
        })

        val consoleLines = consoleOutput.lines()
        val lineLimit = if (this@toOutputString.status == BuildOutcome.Success) 10 else 50
        appendLine("Console output: ${consoleLines.size} lines, last $lineLimit shown")
        consoleLines.takeLast(lineLimit).forEach { appendLine("  $it") }
    }
}

fun List<ProblemAggregation>.toSummary(): ProblemsSummary {
    val grouped = this.groupBy { it.definition.severity }
    fun counts(severity: ProblemSeverity) = grouped[severity].orEmpty().associate { it.definition.id to ProblemsSummary.ProblemSummary(it.definition.displayName, it.numberOfOccurrences) }
    return ProblemsSummary(
        errorCounts = counts(ProblemSeverity.ERROR),
        warningCounts = counts(ProblemSeverity.WARNING),
        adviceCounts = counts(ProblemSeverity.ADVICE),
        otherCounts = counts(ProblemSeverity.OTHER)
    )
}


@Serializable
data class ProblemsSummary(
    val errorCounts: Map<ProblemId, ProblemSummary>,
    val warningCounts: Map<ProblemId, ProblemSummary>,
    val adviceCounts: Map<ProblemId, ProblemSummary>,
    val otherCounts: Map<ProblemId, ProblemSummary>
) {
    val totalCount = errorCounts.values.sumOf { it.occurences } + warningCounts.values.sumOf { it.occurences } + adviceCounts.values.sumOf { it.occurences } + otherCounts.values.sumOf { it.occurences }

    @Serializable
    data class ProblemSummary(
        val displayName: String?,
        val occurences: Int
    )
}