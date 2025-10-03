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
    val id: ProblemId,
    val displayName: String,
    @Description("The severity of the problem. ERROR will fail a build.")
    val severity: ProblemSeverity,
    val documentationLink: String?,
    val occurences: List<ProblemOccurence>
) {

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

enum class ProblemSeverity {
    ADVICE, WARNING, ERROR, OTHER
}

@Suppress("SENSELESS_COMPARISON")
val ProblemGroup.fqName: String get() = if (parent == null) name else parent.fqName + "." + name

@Suppress("UNNECESSARY_SAFE_CALL")
fun org.gradle.tooling.events.problems.ProblemAggregation.toModel(): ProblemAggregation = ProblemAggregation(
    id = definition.id.toId(),
    displayName = definition.id.displayName,
    severity = when (definition.severity.severity) {
        0 -> ProblemSeverity.ADVICE
        1 -> ProblemSeverity.WARNING
        2 -> ProblemSeverity.ERROR
        else -> ProblemSeverity.OTHER
    },
    documentationLink = definition.documentationLink?.url,
    occurences = problemContext.map {
        ProblemAggregation.ProblemOccurence(
            details = it.details?.details,
            originLocations = it.originLocations.mapNotNull { it.toDescriptorString() },
            contextualLocations = it.contextualLocations.mapNotNull { it.toDescriptorString() },
            potentialSolutions = it.solutions.map { it.solution }
        )
    }
)

fun org.gradle.tooling.events.problems.Location.toDescriptorString(): String? = when (this) {
    is FileLocation -> "File: ${Path(path)}"
    is TaskPathLocation -> "Task: buildTreePath"
    is PluginIdLocation -> "Plugin: $pluginId"
    else -> null
}
