package dev.rnett.gradle.mcp.maven

import dev.rnett.gradle.mcp.DI
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class MavenServicesIntegrationTest {

    private val client = DI.createHttpClient()

    private val mavenService = DefaultMavenRepoService(client)
    private val mavenCentralService = DefaultMavenCentralService(client)

    @Test
    fun `DefaultMavenCentralService searchCentral returns real results`() = runTest {
        val response = mavenCentralService.searchCentral("kotlin-stdlib", 0, 10)

        assertTrue(response.numFound > 0, "Expected at least one result for 'kotlin-stdlib'")
        val artifactIds = response.docs.map { it.artifactId }
        assertContains(artifactIds, "kotlin-stdlib")

        val stdlib = response.docs.find { it.artifactId == "kotlin-stdlib" }!!
        assertEquals("org.jetbrains.kotlin", stdlib.groupId)
    }

    @Test
    fun `DefaultMavenService getVersions returns real versions`() = runTest {
        val versions = mavenService.getVersions(MAVEN_CENTRAL_URL, "org.jetbrains.kotlin", "kotlin-stdlib")

        assertTrue(versions.isNotEmpty(), "Expected versions for 'kotlin-stdlib'")
        assertContains(versions, "2.0.0")
        assertContains(versions, "1.9.0")
    }

    private fun assertEquals(expected: Any, actual: Any) {
        kotlin.test.assertEquals(expected, actual)
    }
}
