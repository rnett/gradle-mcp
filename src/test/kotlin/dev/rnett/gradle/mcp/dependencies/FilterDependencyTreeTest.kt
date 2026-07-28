package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FilterDependencyTreeTest {

    private fun dep(
        group: String,
        name: String,
        version: String? = "1.0.0",
        children: List<GradleDependency> = emptyList(),
        latestVersion: String? = null
    ) = GradleDependency(
        id = "$group:$name:$version",
        group = group,
        name = name,
        version = version,
        latestVersion = latestVersion,
        sourcesFile = null,
        children = children
    )

    private fun report(dependencies: List<GradleDependency>) = GradleDependencyReport(
        projects = listOf(
            GradleProjectDependencies(
                path = ":test-project",
                sourceSets = emptyList(),
                repositories = emptyList(),
                configurations = listOf(
                    GradleConfigurationDependencies(
                        name = "implementation",
                        description = null,
                        isResolvable = false,
                        dependencies = dependencies
                    )
                ),
                jdkHome = null,
                jdkVersion = null
            )
        )
    )

    @Test
    fun `matching parent with non-matching children prunes children`() {
        val matchingChild = dep("org.lib", "a", "2.0.0")
        val nonMatchingChild = dep("org.other", "b", "1.0.0", children = listOf(dep("org.other", "c", "1.0.0")))

        val input = report(listOf(dep("org.lib", "parent", "1.0.0", children = listOf(matchingChild, nonMatchingChild))))
        val result = filterDependencyTree(input, DependencyFilterMatcher(Regex("^org\\.lib:.*$")))

        val rootConfig = result.projects.first().configurations.first()
        val rootDep = rootConfig.dependencies.first()

        // Parent matches and is kept
        assertEquals("org.lib", rootDep.group)
        // Only matching child preserved
        assertEquals(1, rootDep.children.size)
        assertEquals("org.lib", rootDep.children[0].group)
        // Non-matching child (and its grandchild) pruned
        assertEquals(null, rootDep.children.find { it.name == "b" })
    }

    @Test
    fun `non-matching parent with matching child preserves parent as structural node`() {
        val matchingChild = dep("org.lib", "target", "1.0.0")

        val input = report(listOf(dep("org.other", "parent", "1.0.0", children = listOf(matchingChild))))
        val result = filterDependencyTree(input, DependencyFilterMatcher(Regex("^org\\.lib:.*$")))

        val rootConfig = result.projects.first().configurations.first()
        val rootDep = rootConfig.dependencies.first()

        // Non-matching parent kept because it has a matching child
        assertEquals("org.other", rootDep.group)
        // Matching child preserved
        assertEquals(1, rootDep.children.size)
        assertEquals("org.lib", rootDep.children[0].group)
    }

    @Test
    fun `both parent and child matching keeps both`() {
        val matchingChild = dep("org.lib", "sub", "1.0.0")

        val input = report(listOf(dep("org.lib", "parent", "1.0.0", children = listOf(matchingChild))))
        val result = filterDependencyTree(input, DependencyFilterMatcher(Regex("^org\\.lib:.*$")))

        val rootConfig = result.projects.first().configurations.first()
        val rootDep = rootConfig.dependencies.first()

        assertEquals("org.lib", rootDep.group)
        assertEquals(1, rootDep.children.size)
        assertEquals("org.lib", rootDep.children[0].group)
    }

    @Test
    fun `neither parent nor child matching prunes both`() {
        val child = dep("org.lib", "child", "1.0.0")

        val input = report(listOf(dep("org.other", "parent", "1.0.0", children = listOf(child))))
        val result = filterDependencyTree(input, DependencyFilterMatcher(Regex("^org.another:.*$")))

        // Everything pruned
        assertEquals(0, result.projects.first().configurations.first().dependencies.size)
    }

    @Test
    fun `version filter interacts with tree pruning`() {
        val matchingChild = dep("org.lib", "b", "2.0.0", latestVersion = "2.0.0")
        val nonMatchingVersionChild = dep("org.lib", "a", "1.0.0", latestVersion = "1.5.0")

        val input = report(listOf(dep("org.lib", "parent", "1.0.0", children = listOf(matchingChild, nonMatchingVersionChild))))
        val matcher = DependencyFilterMatcher(dependencyFilterRegex = Regex("^org\\.lib:.*$"), versionFilterRegex = Regex("^2\\..*$"))
        val result = filterDependencyTree(input, matcher)

        val rootConfig = result.projects.first().configurations.first()
        val rootDep = rootConfig.dependencies.first()

        // Parent matches coordinate but not version; kept because child matches
        assertEquals("org.lib", rootDep.group)
        // Only version-matching child preserved
        assertEquals(1, rootDep.children.size)
        assertEquals("2.0.0", rootDep.children[0].latestVersion)
    }

    @Test
    fun `empty list input returns empty`() {
        val input = report(emptyList())
        val result = filterDependencyTree(input, DependencyFilterMatcher(Regex("^org\\.lib:.*$")))

        assertEquals(0, result.projects.first().configurations.first().dependencies.size)
    }

    @Test
    fun `no filter null matcher keeps all`() {
        val input = report(listOf(dep("org.lib", "a", "1.0.0"), dep("org.other", "b", "2.0.0")))
        val result = filterDependencyTree(input, null)

        assertEquals(2, result.projects.first().configurations.first().dependencies.size)
    }

    @Test
    fun `no filter matcher with no filters keeps all`() {
        val input = report(listOf(dep("org.lib", "a", "1.0.0"), dep("org.other", "b", "2.0.0")))
        val result = filterDependencyTree(input, DependencyFilterMatcher(null))

        assertEquals(2, result.projects.first().configurations.first().dependencies.size)
    }
}
