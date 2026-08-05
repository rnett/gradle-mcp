package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.LatestStableGradleVersion
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.GradleSourceService
import dev.rnett.gradle.mcp.dependencies.SourceIndexService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsService
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.maven.DepsDevService
import dev.rnett.gradle.mcp.repl.ReplEnvironmentService
import dev.rnett.gradle.mcp.repl.ReplManager
import dev.rnett.gradle.mcp.utils.EnvProvider
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleToolVersionDescriptionTest {

    private val sentinelNote = "<SENTINEL 9.99.99>"

    private fun descriptionFor(server: io.modelcontextprotocol.kotlin.sdk.server.Server, toolName: String): String =
        server.tools[toolName]?.tool?.description.orEmpty()

    @Test
    fun `GradleDocsTools description includes injected latest stable version note`() {
        val docsTools = GradleDocsTools(mockk(relaxed = true), mockk(relaxed = true), sentinelNote)
        val server = DI.createServer(DI.json, listOf(docsTools))

        val description = descriptionFor(server, ToolNames.GRADLE_DOCS)

        assertTrue(description.contains(sentinelNote), "GRADLE_DOCS description should include the injected note")
    }

    @Test
    fun `GradleExecutionTools description does not include the latest stable version note`() {
        val executionTools = GradleExecutionTools(mockk(relaxed = true))
        val server = DI.createServer(DI.json, listOf(executionTools))

        val description = descriptionFor(server, ToolNames.GRADLE)

        assertFalse(description.contains(sentinelNote), "GRADLE description should not include the injected note")
    }

    @Test
    fun `sentinel note appears in exactly GRADLE_DOCS description`() {
        val components = DI.components(
            provider = mockk<GradleProvider>(relaxed = true),
            replManager = mockk<ReplManager>(relaxed = true),
            replEnvironmentService = mockk<ReplEnvironmentService>(relaxed = true),
            envProvider = mockk<EnvProvider>(relaxed = true),
            gradleDocsService = mockk<GradleDocsService>(relaxed = true),
            gradleVersionService = mockk<GradleVersionService>(relaxed = true),
            gradleDependencyService = mockk<GradleDependencyService>(relaxed = true),
            depsDevService = mockk<DepsDevService>(relaxed = true),
            sourcesService = mockk<SourcesService>(relaxed = true),
            gradleSourceService = mockk<GradleSourceService>(relaxed = true),
            indexService = mockk<SourceIndexService>(relaxed = true),
            searchProviders = emptyList<SearchProvider>(),
            latestStableGradleVersionNote = sentinelNote
        )
        val server = DI.createServer(DI.json, components)

        val toolsWithSentinel = server.tools
            .filter { it.value.tool.description.orEmpty().contains(sentinelNote) }
            .keys
            .sorted()

        assertEquals(listOf(ToolNames.GRADLE_DOCS), toolsWithSentinel)
    }

    @Test
    fun `components register with default docs note when version resolution is unavailable`() {
        val components = DI.components(
            provider = mockk<GradleProvider>(relaxed = true),
            replManager = mockk<ReplManager>(relaxed = true),
            replEnvironmentService = mockk<ReplEnvironmentService>(relaxed = true),
            envProvider = mockk<EnvProvider>(relaxed = true),
            gradleDocsService = mockk<GradleDocsService>(relaxed = true),
            gradleVersionService = ThrowingVersionService(),
            gradleDependencyService = mockk<GradleDependencyService>(relaxed = true),
            depsDevService = mockk<DepsDevService>(relaxed = true),
            sourcesService = mockk<SourcesService>(relaxed = true),
            gradleSourceService = mockk<GradleSourceService>(relaxed = true),
            indexService = mockk<SourceIndexService>(relaxed = true),
            searchProviders = emptyList<SearchProvider>()
        )
        val server = DI.createServer(DI.json, components)

        val gradleDescription = descriptionFor(server, ToolNames.GRADLE)
        val gradleDocsDescription = descriptionFor(server, ToolNames.GRADLE_DOCS)

        assertFalse(gradleDescription.contains(BuildConfig.GRADLE_VERSION), "GRADLE description should not contain the bundled Gradle version note")
        assertTrue(gradleDocsDescription.contains(BuildConfig.GRADLE_VERSION), "GRADLE_DOCS description should contain the bundled Gradle version")
    }

    @Test
    fun `fetched live note truthfully describes a live resolution`() {
        val note = latestStableGradleVersionNote(
            LatestStableGradleVersion("9.99.99", LatestStableGradleVersion.Source.FETCHED_LIVE)
        )

        assertTrue(note.contains("latest stable Gradle version is **9.99.99**"))
        assertTrue(note.contains("https://services.gradle.org/versions/current"))
    }

    @Test
    fun `bundled fallback note truthfully disclaims the bundled version`() {
        val note = latestStableGradleVersionNote(
            LatestStableGradleVersion("8.88.88", LatestStableGradleVersion.Source.BUNDLED_FALLBACK)
        )

        assertTrue(note.contains("8.88.88"))
        assertTrue(note.contains("could not be verified"))
        assertTrue(note.contains("newer versions may exist"))
        assertFalse(note.contains("latest stable Gradle version is **"), "Fallback note must not claim the bundled version is the live latest")
    }

    @Test
    fun `docs note describes the startup check policy`() {
        val note = latestStableGradleVersionNoteForDocs(BuildConfig.GRADLE_VERSION)

        assertTrue(note.contains(BuildConfig.GRADLE_VERSION))
        assertTrue(note.contains("https://services.gradle.org/versions/current"))
        assertTrue(note.contains("if that check fails"))
    }

    private class ThrowingVersionService : GradleVersionService {
        override suspend fun resolveVersion(version: String?): String = throw UnsupportedOperationException()
        override suspend fun resolveLatestStable(): LatestStableGradleVersion = throw UnsupportedOperationException()
    }
}
