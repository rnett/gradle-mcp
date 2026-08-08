package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.dependencies.model.ConsumerEdge
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConsumerInverterTest {

    private fun dep(
        id: String,
        variant: String? = null,
        commonComponentId: String? = null,
        fromConfiguration: String? = null,
        children: List<GradleDependency> = emptyList()
    ) = GradleDependency(
        id = id,
        group = "g",
        name = id.substringBefore(":"),
        version = "1",
        variant = variant,
        commonComponentId = commonComponentId,
        fromConfiguration = fromConfiguration,
        children = children
    )

    private fun report(vararg roots: GradleDependency): GradleDependencyReport = GradleDependencyReport(
        projects = listOf(
            GradleProjectDependencies(
                path = ":",
                sourceSets = emptyList(),
                repositories = emptyList(),
                configurations = listOf(
                    GradleConfigurationDependencies(
                        name = "compileClasspath",
                        description = null,
                        isResolvable = true,
                        dependencies = roots.toList()
                    )
                )
            )
        )
    )

    private fun consumersOf(report: GradleDependencyReport, id: String): List<ConsumerEdge>? {
        val stack = ArrayDeque<GradleDependency>()
        report.projects.flatMap { it.configurations }.forEach { stack.addAll(it.dependencies) }
        val seen = mutableSetOf<Triple<String, String?, List<String>>>()
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current.id == id) return current.consumers
            if (seen.add(Triple(current.id, current.variant, current.capabilities))) {
                stack.addAll(current.children)
            }
        }
        error("Dependency $id not found in report")
    }

    @Test
    fun `simple chain attaches direct parents as consumers`() {
        val report = report(dep("A", children = listOf(dep("B", children = listOf(dep("C"))))))

        val inverted = report.withConsumers()

        val cConsumers = consumersOf(inverted, "C")!!
        assertEquals(1, cConsumers.size)
        val bEdge = cConsumers.single()
        assertEquals("B", bEdge.id)
        assertEquals("B", bEdge.path)
        assertNull(bEdge.variant)

        val bConsumers = consumersOf(inverted, "B")!!
        assertEquals(1, bConsumers.size)
        assertEquals("A", bConsumers.single().id)

        // The root has no direct parents: exposed as an empty list, not null.
        assertEquals(emptyList(), consumersOf(inverted, "A"))
    }

    @Test
    fun `diamond produces one consumer edge per distinct direct parent`() {
        // A -> B -> C and A -> D -> C: C has direct parents B and D, not one edge per root path.
        val report = report(
            dep("A", children = listOf(dep("B", children = listOf(dep("C"))), dep("D", children = listOf(dep("C")))))
        )

        val inverted = report.withConsumers()

        val cConsumers = consumersOf(inverted, "C")!!
        assertEquals(2, cConsumers.size, "Expected one edge per distinct direct parent, got: $cConsumers")
        assertEquals(setOf("B", "D"), cConsumers.map { it.id }.toSet())
    }

    @Test
    fun `repeated paths for the same parent deduplicate to one edge`() {
        // A -> P -> C and D -> P -> C: the same parent P reaches C twice but yields one edge.
        val p = dep("P", children = listOf(dep("C")))
        val report = report(dep("A", children = listOf(p)), dep("D", children = listOf(p)))

        val inverted = report.withConsumers()

        val cConsumers = consumersOf(inverted, "C")!!
        assertEquals(1, cConsumers.size, "Same parent through multiple paths must deduplicate: $cConsumers")
        assertEquals("P", cConsumers.single().id)
    }

    @Test
    fun `cycle shapes terminate and record reverse edges`() {
        // A -> B -> A forms a cycle; inversion must terminate and still record the reverse edges.
        val report = report(dep("A", children = listOf(dep("B", children = listOf(dep("A"))))))

        val inverted = report.withConsumers()

        assertEquals(listOf("B"), consumersOf(inverted, "A")!!.map { it.id })
        assertEquals(listOf("A"), consumersOf(inverted, "B")!!.map { it.id })
    }

    @Test
    fun `distinct parents without common component ids remain separate`() {
        // Two ordinary parents in the same configuration both lack commonComponentId.
        val report = report(
            dep("P1", children = listOf(dep("C"))),
            dep("P2", children = listOf(dep("C")))
        )

        val inverted = report.withConsumers()

        val cConsumers = consumersOf(inverted, "C")!!
        assertEquals(2, cConsumers.size, "Distinct ordinary parents must not collapse: $cConsumers")
        assertEquals(setOf("P1", "P2"), cConsumers.map { it.id }.toSet())
    }

    @Test
    fun `parents sharing a common component id collapse to one edge`() {
        // Platform parents with different ids but the same commonComponentId are one consumer.
        val report = report(
            dep("g:p-jvm:1", commonComponentId = "g:p:1", children = listOf(dep("C"))),
            dep("g:p:1", commonComponentId = "g:p:1", children = listOf(dep("C")))
        )

        val inverted = report.withConsumers()

        val cConsumers = consumersOf(inverted, "C")!!
        assertEquals(1, cConsumers.size, "Same commonComponentId must collapse: $cConsumers")
    }

    @Test
    fun `variant distinct direct parents remain separate and distinguishable`() {
        // Same coordinates and source configuration, differing only by variant.
        val report = report(
            dep("g:p:1", variant = "jvm", children = listOf(dep("C"))),
            dep("g:p:1", variant = "js", children = listOf(dep("C")))
        )

        val inverted = report.withConsumers()

        val cConsumers = consumersOf(inverted, "C")!!
        assertEquals(2, cConsumers.size, "Variant-distinct parents must remain separate: $cConsumers")
        assertEquals(setOf("jvm", "js"), cConsumers.map { it.variant }.toSet())
    }

    @Test
    fun `consumer edge carries fromConfiguration and null variant`() {
        val report = report(
            dep("A", fromConfiguration = "implementation", children = listOf(dep("C")))
        )

        val inverted = report.withConsumers()

        val edge = consumersOf(inverted, "C")!!.single()
        assertEquals("implementation", edge.fromConfiguration)
        assertNull(edge.variant)
    }

    @Test
    fun `transitive target's consumers include only direct parents not root ancestors`() {
        // Application A -> library B -> library C: C's consumers are [B], never A.
        val report = report(dep("A", children = listOf(dep("B", children = listOf(dep("C"))))))

        val inverted = report.withConsumers()

        val cConsumers = consumersOf(inverted, "C")!!
        assertEquals(listOf("B"), cConsumers.map { it.id })
        assertTrue(cConsumers.none { it.id == "A" }, "Transitive consumers must not be added")
    }
}
