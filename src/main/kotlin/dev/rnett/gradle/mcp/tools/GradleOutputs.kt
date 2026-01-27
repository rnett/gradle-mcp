package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.BuildResult
import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemId
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
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


fun BuildResult.toOutputString(includeArgs: Boolean = true): String {
    return buildString {
        appendLine("Gradle MCP Build ID: $id")
        if (includeArgs) {
            appendLine("Command line: ${args.renderCommandLine()}")
        }
        appendLine("Status: ${if (isSuccessful) "Success" else "Failure"}")

        appendLine()
        if (buildFailures != null) {
            appendLine("Build Failures: ${buildFailures.size} - use `lookup_build_failures_summary` or `lookup_build_failure_details` tools for more details")
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
        appendLine("Problems:     ${problemsSummary.totalCount} - use `lookup_build_problems_summary` or `lookup_build_problem_details` tools for more details")
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

        appendLine("Tests:      ${testResults.totalCount} - use `lookup_build_tests` and `lookup_build_test_details` tool for more details")
        appendLine("  Passed:   ${testResults.passed.size}")
        appendLine("  Skipped:  ${testResults.skipped.size}")
        appendLine("  Failed:   ${testResults.failed.size}")
        appendLine(OutputFormatter.listResults(testResults.failed, 20, "  ") {
            it.testName
        })

        appendLine("Console output: ${consoleOutputLines.size} lines, last 50 shown")
        consoleOutputLines.takeLast(50).forEach { appendLine("  $it") }
    }


}

fun Map<ProblemSeverity, List<ProblemAggregation>>.toSummary(): ProblemsSummary {
    fun counts(severity: ProblemSeverity) = this[severity].orEmpty().associate { it.definition.id to ProblemsSummary.ProblemSummary(it.definition.displayName, it.numberOfOccurrences) }
    return ProblemsSummary(
        errorCounts = counts(ProblemSeverity.ERROR),
        warningCounts = counts(ProblemSeverity.WARNING),
        adviceCounts = counts(ProblemSeverity.ADVICE),
        otherCounts = counts(ProblemSeverity.OTHER),
    )
}


@Serializable
data class ProblemsSummary(
    val errorCounts: Map<ProblemId, ProblemSummary>,
    val warningCounts: Map<ProblemId, ProblemSummary>,
    val adviceCounts: Map<ProblemId, ProblemSummary>,
    val otherCounts: Map<ProblemId, ProblemSummary>,
) {
    val totalCount = errorCounts.values.sumOf { it.occurences } + warningCounts.values.sumOf { it.occurences } + adviceCounts.values.sumOf { it.occurences } + otherCounts.values.sumOf { it.occurences }

    @Serializable
    data class ProblemSummary(
        val displayName: String?,
        val occurences: Int
    )
}