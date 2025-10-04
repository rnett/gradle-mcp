package dev.rnett.gradle.mcp.gradle

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.gradle.tooling.events.problems.FileLocation
import org.gradle.tooling.events.problems.PluginIdLocation
import org.gradle.tooling.events.problems.ProblemGroup
import org.gradle.tooling.events.problems.TaskPathLocation
import kotlin.io.path.Path

@Serializable
@JvmInline
@Description("The identifier of a problem. Use with `lookup_build_problem_details`. Note that the same problem may occur in different places in the build.")
value class ProblemId(val id: String) {
    constructor(group: String, name: String) : this("$group.$name")
}

fun org.gradle.tooling.events.problems.ProblemId.toId() = ProblemId(name, group.fqName)

@Serializable
@Description("A problem that occurred during a Gradle build, along with information about all of its occurences")
data class ProblemAggregation(
    val definition: ProblemDefinition,
    val occurences: List<ProblemOccurence>
) {
    @Serializable
    data class ProblemDefinition(
        val id: ProblemId,
        val displayName: String?,
        val severity: ProblemSeverity,
        val documentationLink: String?,
    )

    val numberOfOccurrences: Int = occurences.size

    @Serializable
    data class ProblemOccurence(
        @Description("Detailed information about the problem")
        val details: String?,
        val originLocations: List<String>,
        @Description("Additional locations that didn't cause the problem, but are part of its context")
        val contextualLocations: List<String>,
        val potentialSolutions: List<String>
    )
}

@Description("The severity of the problem. ERROR will fail a build.")
enum class ProblemSeverity {
    ADVICE, WARNING, ERROR, OTHER
}

@Suppress("SENSELESS_COMPARISON")
val ProblemGroup.fqName: String get() = if (parent == null) name else parent.fqName + "." + name

class ProblemsAccumulator {
    private val definitions = mutableMapOf<ProblemId, ProblemAggregation.ProblemDefinition>()
    private val problems = mutableMapOf<ProblemId, MutableSet<ProblemAggregation.ProblemOccurence>>()

    fun add(problem: ProblemAggregation) {
        definitions.putIfAbsent(problem.definition.id, problem.definition)
        problems.getOrPut(problem.definition.id) { mutableSetOf() }.addAll(problem.occurences)
    }

    fun add(problem: org.gradle.tooling.events.problems.ProblemAggregation) {
        add(problem.toModel())
    }

    fun add(problem: org.gradle.tooling.events.problems.Problem) {
        add(problem.toModel())
    }

    fun aggregate(): List<ProblemAggregation> = definitions.map { (id, definition) ->
        ProblemAggregation(definition, problems[id].orEmpty().toList())
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    fun org.gradle.tooling.events.problems.ProblemAggregation.toModel(): ProblemAggregation = ProblemAggregation(
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
        occurences = problemContext.map {
            ProblemAggregation.ProblemOccurence(
                details = it.details?.details,
                originLocations = it.originLocations.mapNotNull { it.toDescriptorString() },
                contextualLocations = it.contextualLocations.mapNotNull { it.toDescriptorString() },
                potentialSolutions = it.solutions.map { it.solution }
            )
        }
    )

    @Suppress("UNNECESSARY_SAFE_CALL")
    fun org.gradle.tooling.events.problems.Problem.toModel(): ProblemAggregation = ProblemAggregation(
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

    fun org.gradle.tooling.events.problems.Location.toDescriptorString(): String? = when (this) {
        is FileLocation -> "File: ${Path(path)}"
        is TaskPathLocation -> "Task: buildTreePath"
        is PluginIdLocation -> "Plugin: $pluginId"
        else -> null
    }

}
