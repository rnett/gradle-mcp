package dev.rnett.gradle.mcp.gradle

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.gradle.tooling.events.problems.FileLocation
import org.gradle.tooling.events.problems.PluginIdLocation
import org.gradle.tooling.events.problems.TaskPathLocation

fun org.gradle.tooling.events.problems.Problem.toModel(): Problem = Problem(
    definition = ProblemDefinition(
        id = ProblemId(
            definition.id.name,
            definition.id.group.displayName,
            definition.id.displayName
        ),
        severity = when (definition.severity.severity) {
            0 -> "ADVICE"
            1 -> "WARNING"
            2 -> "ERROR"
            else -> "UNKNOWN"
        },
        documentationLink = definition.documentationLink?.url
    ),
    contextualLabel = contextualLabel.contextualLabel,
    details = details.details,
    originLocations = originLocations.mapNotNull { it.toModel() },
    contextualLocations = contextualLocations.mapNotNull { it.toModel() },
    solutions = solutions.map { it.solution }
)

fun org.gradle.tooling.events.problems.Location.toModel(): Location? = when (this) {
    is FileLocation -> Location.File(path)
    is TaskPathLocation -> Location.TaskPath(buildTreePath)
    is PluginIdLocation -> Location.Plugin(pluginId)
    else -> null
}

@Serializable
@Description("A problem that occurred during a Gradle build")
data class Problem(
    @Description("The definition of the problem type")
    val definition: ProblemDefinition,
    @Description("The human-readable label of this instance of the problem")
    val contextualLabel: String,
    @Description("Detailed information about the problem")
    val details: String,
    @Description("Locations this problem occurred within the build")
    val originLocations: List<Location>,
    @Description("Additional locations that didn't cause the problem, but are part of its context")
    val contextualLocations: List<Location>,
    @Description("Provided solutions to the problem")
    val solutions: List<String>
)

@Serializable
data class ProblemDefinition(
    @Description("The problem's ID")
    val id: ProblemId,
    @Description("The severity of the problem. ERROR will fail a build, WARNING will not.")
    val severity: String,
    @Description("An optional link to the documentation about this problem.")
    val documentationLink: String?
)

@Description("The identifier of a problem")
@Serializable
data class ProblemId(
    @Description("The short name of the problem")
    val name: String,
    @Description("The problem's group")
    val group: String,
    @Description("The display name of the problem")
    val displayName: String
)

@Description("A location of a problem within a Gradle build")
@Serializable
sealed interface Location {
    @Description("A file")
    @Serializable
    data class File(val path: String) : Location

    @Description("A Gradle task")
    @Serializable
    data class TaskPath(val taskPath: String) : Location

    @Description("A gradle plugin")
    @Serializable
    data class Plugin(val pluginId: String) : Location
}