package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.tools.ToolNames
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.gradle.tooling.events.problems.ProblemGroup

@Serializable
@JvmInline
@Description("The identifier of a problem. Use with `${ToolNames.LOOKUP_BUILD_PROBLEMS}`. Note that the same problem may occur in different places in the build.")
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

