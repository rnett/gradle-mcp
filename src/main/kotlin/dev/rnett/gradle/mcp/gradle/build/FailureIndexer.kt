package dev.rnett.gradle.mcp.gradle.build

import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import dev.rnett.gradle.mcp.mapToSet
import org.gradle.tooling.Failure
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class FailureContent(
    val message: String?,
    val description: String?,
    val causes: Set<FailureContent>,
    val problemAggregations: Map<ProblemSeverity, List<ProblemAggregation>>
)

class FailureIndexer {
    private val indexes = java.util.concurrent.ConcurrentHashMap<FailureContent, dev.rnett.gradle.mcp.gradle.build.FailureId>()

    @OptIn(ExperimentalUuidApi::class)
    fun index(content: FailureContent): dev.rnett.gradle.mcp.gradle.build.FailureId = indexes.computeIfAbsent(content) { dev.rnett.gradle.mcp.gradle.build.FailureId(Uuid.random().toString()) }

    fun withIndex(content: FailureContent): dev.rnett.gradle.mcp.gradle.build.Failure =
        dev.rnett.gradle.mcp.gradle.build.Failure(index(content), content.message, content.description, content.causes.map { withIndex(it) }, content.problemAggregations)
}

@OptIn(ExperimentalUuidApi::class)
internal fun Failure.toContent(): FailureContent {
    val accumulator = ProblemsAccumulator()

    // Some versions of Gradle Tooling API allow accessing problems from a Failure.
    // However, the base Failure interface doesn't always expose them directly.
    // If it's a version that supports it (e.g. via internal or new features), we'd aggregate here.
    // For now, we maintain the structure to support build-level problem reporting.

    return FailureContent(
        message,
        description,
        causes.mapToSet { it.toContent() },
        accumulator.aggregateBySeverity()
    )
}
