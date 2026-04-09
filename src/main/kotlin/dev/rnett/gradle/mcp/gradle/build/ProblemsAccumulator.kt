package dev.rnett.gradle.mcp.gradle.build

import dev.rnett.gradle.mcp.gradle.ProblemAggregation
import dev.rnett.gradle.mcp.gradle.ProblemId
import dev.rnett.gradle.mcp.gradle.ProblemSeverity
import dev.rnett.gradle.mcp.gradle.toId
import org.gradle.tooling.events.problems.FileLocation
import org.gradle.tooling.events.problems.Location
import org.gradle.tooling.events.problems.PluginIdLocation
import org.gradle.tooling.events.problems.Problem
import org.gradle.tooling.events.problems.TaskPathLocation
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path

@Suppress("UnstableApiUsage")
internal class ProblemsAccumulator {
    private val definitions = ConcurrentHashMap<ProblemId, ProblemAggregation.ProblemDefinition>()
    private val problems = ConcurrentHashMap<ProblemId, MutableSet<ProblemAggregation.ProblemOccurence>>()

    fun add(problem: ProblemAggregation) {
        definitions.putIfAbsent(problem.definition.id, problem.definition)
        problems.computeIfAbsent(problem.definition.id) { ConcurrentHashMap.newKeySet() }.addAll(problem.occurences)
    }

    fun add(problem: org.gradle.tooling.events.problems.ProblemAggregation) {
        add(problem.toModel())
    }

    fun add(problem: Problem) {
        add(problem.toModel())
    }

    fun aggregate(): List<ProblemAggregation> = definitions.map { (id, definition) ->
        ProblemAggregation(definition, problems[id].orEmpty().toList())
    }

    fun aggregateBySeverity(): Map<ProblemSeverity, List<ProblemAggregation>> = aggregate().groupBy { it.definition.severity }

    @Suppress("UNNECESSARY_SAFE_CALL")
    fun org.gradle.tooling.events.problems.ProblemAggregation.toModel(): ProblemAggregation {
        val aggregation = this
        return ProblemAggregation(
            definition = ProblemAggregation.ProblemDefinition(
                id = definition.id.toId(),
                displayName = definition.id.displayName,
                severity = when (definition.severity.severity) {
                    0 -> ProblemSeverity.ADVICE
                    1 -> ProblemSeverity.WARNING
                    2 -> ProblemSeverity.ERROR
                    else -> ProblemSeverity.OTHER
                },
                documentationLink = definition.documentationLink?.url
            ),
            occurences = aggregation.problemContext.map {
                ProblemAggregation.ProblemOccurence(
                    details = it.details?.details,
                    originLocations = it.originLocations.mapNotNull { it.toDescriptorString() },
                    contextualLocations = it.contextualLocations.mapNotNull { it.toDescriptorString() },
                    potentialSolutions = it.solutions.map { it.solution }
                )
            }
        )
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    fun Problem.toModel(): ProblemAggregation = ProblemAggregation(
        definition = ProblemAggregation.ProblemDefinition(
            id = definition.id.toId(),
            displayName = definition.id.displayName,
            severity = when (definition.severity.severity) {
                0 -> ProblemSeverity.ADVICE
                1 -> ProblemSeverity.WARNING
                2 -> ProblemSeverity.ERROR
                else -> ProblemSeverity.OTHER
            },
            documentationLink = definition.documentationLink?.url
        ),
        occurences = listOf(
            ProblemAggregation.ProblemOccurence(
                details = details?.details,
                originLocations = originLocations.mapNotNull { it.toDescriptorString() },
                contextualLocations = contextualLocations.mapNotNull { it.toDescriptorString() },
                potentialSolutions = solutions.map { it.solution }
            )
        )
    )

    fun Location.toDescriptorString(): String? = when (this) {
        is FileLocation -> "File: ${Path(path)}"
        is TaskPathLocation -> "Task: buildTreePath"
        is PluginIdLocation -> "Plugin: $pluginId"
        else -> null
    }
}
