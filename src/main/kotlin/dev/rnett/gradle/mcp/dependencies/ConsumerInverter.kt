package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.dependencies.model.ConsumerEdge
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport

/** Identity of a dependency node in a report, mirroring the dedup key used by [GradleProjectDependencies.allDependencies]. */
internal typealias ConsumerNodeKey = Triple<String, String?, List<String>>

/** Identity of a consumer edge used to deduplicate reverse edges for one child node. */
private data class ConsumerEdgeKey(
    val parentIdentity: String,
    val fromConfiguration: String?,
    val variant: String?
)

/**
 * Computes and attaches direct consumer edges for every node in [this] report.
 *
 * The inversion is a single pass over the forward graph: for every parent -> child edge the parent
 * is recorded as a lightweight [ConsumerEdge] on the child. Edges are deduplicated by
 * `(commonComponentId ?: syntheticId(group, name, version, variant)) + fromConfiguration + variant`,
 * so diamond shapes produce one edge per distinct direct parent rather than one per root path, and
 * cycle shapes terminate safely. The resulting consumer map is computed once and memoized for the
 * whole invocation, then attached to every matching node.
 */
internal fun GradleDependencyReport.withConsumers(): GradleDependencyReport {
    val consumerEdges = computeConsumerEdges()
    return copy(
        projects = projects.map { project ->
            project.copy(
                configurations = project.configurations.map { configuration ->
                    configuration.copy(
                        dependencies = configuration.dependencies.map { it.attachConsumers(consumerEdges) }
                    )
                }
            )
        }
    )
}

private fun GradleDependencyReport.computeConsumerEdges(): Map<ConsumerNodeKey, List<ConsumerEdge>> {
    val edgesByChild = mutableMapOf<ConsumerNodeKey, MutableMap<ConsumerEdgeKey, ConsumerEdge>>()
    projects.forEach { project ->
        project.configurations.forEach { configuration ->
            configuration.dependencies.forEach { root ->
                collectConsumerEdges(root, mutableSetOf(), edgesByChild)
            }
        }
    }
    return edgesByChild.mapValues { (_, edges) -> edges.values.toList() }
}

private fun collectConsumerEdges(
    parent: GradleDependency,
    ancestors: MutableSet<ConsumerNodeKey>,
    edgesByChild: MutableMap<ConsumerNodeKey, MutableMap<ConsumerEdgeKey, ConsumerEdge>>
) {
    val parentIdentity = parent.commonComponentId ?: syntheticParentIdentity(parent)
    parent.children.forEach { child ->
        val childKey = child.identityKey()
        val consumerEdge = parent.toConsumerEdge()
        val edgeKey = ConsumerEdgeKey(parentIdentity, parent.fromConfiguration, parent.variant)
        edgesByChild.getOrPut(childKey) { linkedMapOf() }[edgeKey] = consumerEdge

        // Path-based ancestor tracking guarantees termination on cycle shapes while still
        // recording every reverse edge along the (finite) path.
        if (ancestors.add(childKey)) {
            collectConsumerEdges(child, ancestors, edgesByChild)
            ancestors.remove(childKey)
        }
    }
}

private fun GradleDependency.attachConsumers(consumerEdges: Map<ConsumerNodeKey, List<ConsumerEdge>>): GradleDependency =
    copy(
        // When consumers were requested every node exposes a list; nodes with no direct parents
        // get an empty list. The field stays null (absent) only when inversion was not requested.
        consumers = consumerEdges[identityKey()] ?: emptyList(),
        children = children.map { it.attachConsumers(consumerEdges) }
    )

private fun GradleDependency.identityKey(): ConsumerNodeKey = Triple(id, variant, capabilities)

private fun GradleDependency.toConsumerEdge(): ConsumerEdge = ConsumerEdge(
    id = id,
    group = group,
    name = name,
    version = version,
    variant = variant,
    fromConfiguration = fromConfiguration,
    path = id
)

/**
 * Folds a parent's GAV, variant, and id into a stable identity used when the parent has no
 * `commonComponentId` of its own.
 */
private fun syntheticParentIdentity(parent: GradleDependency): String =
    buildString {
        append(parent.group ?: "")
        append(':')
        append(parent.name)
        append(':')
        append(parent.version ?: "")
        append(':')
        append(parent.variant ?: "")
        append('|')
        append(parent.id)
    }
