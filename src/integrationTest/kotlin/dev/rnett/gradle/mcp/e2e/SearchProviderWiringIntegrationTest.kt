package dev.rnett.gradle.mcp.e2e

import dev.rnett.gradle.mcp.DI
import dev.rnett.gradle.mcp.GradleVersionService
import dev.rnett.gradle.mcp.LatestStableGradleVersion
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.dependencies.SourceIndexService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.DeclarationSearch
import dev.rnett.gradle.mcp.dependencies.search.FullTextSearch
import dev.rnett.gradle.mcp.dependencies.search.GlobSearch
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.SearchResponse
import dev.rnett.gradle.mcp.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.tools.ToolNames
import dev.rnett.gradle.mcp.tools.dependencies.DependencySourceTools.SearchType
import io.ktor.server.config.MapApplicationConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class SearchProviderRegistrationTest {

    @Test
    fun `production module registers every search provider exactly once`() {
        val config = MapApplicationConfig(
            "gradle.maxConnections" to "10",
            "gradle.ttl" to "PT5M",
            "gradle.allowPublicScansPublishing" to "true"
        )
        val app = koinApplication {
            allowOverride(true)
            modules(
                DI.createModule(config),
                module {
                    single<GradleVersionService> {
                        StubGradleVersionService(
                            LatestStableGradleVersion(
                                TestFixturesBuildConfig.GRADLE_VERSION,
                                LatestStableGradleVersion.Source.BUNDLED_FALLBACK
                            )
                        )
                    }
                }
            )
        }

        try {
            val providers = app.koin.getAll<SearchProvider>()
            val providerClasses: Set<KClass<out SearchProvider>> = providers.map { it::class }.toSet()
            val expectedClasses = setOf(DeclarationSearch::class, FullTextSearch::class, GlobSearch::class)

            assertEquals(3, providers.size, "Production DI must register exactly three SearchProvider definitions")
            assertEquals(expectedClasses, providerClasses, "Production DI must register each SearchProvider implementation")
        } finally {
            app.close()
        }
    }
}

class SearchProviderMcpIntegrationTest : BaseMcpServerTest() {

    private lateinit var sourcesService: SourcesService
    private lateinit var sourceIndexService: SourceIndexService
    private lateinit var sources: SourcesDir
    private lateinit var sourceFile: java.nio.file.Path

    @BeforeEach
    fun setUpSourceFixture() {
        sourceFile = tempDir.resolve("com/example/TinySource.kt")
        sourceFile.parent.createDirectories()
        sourceFile.writeText("package com.example\nclass TinySource\n")

        sources = mockk(relaxed = true)
        every { sources.sources } returns tempDir
        every { sources.rootForSearch } returns tempDir
        every { sources.lastRefresh() } returns null
        every { sources.resolveIndexDirs(any()) } returns emptyList()

        sourcesService = server.koin.get()
        sourceIndexService = server.koin.get()
        coEvery {
            with(any<ProgressReporter>()) {
                sourcesService.resolveAndProcessProjectSources(any(), any(), any(), any(), any(), any())
            }
        } returns sources
        coEvery { sourceIndexService.search(any(), any(), any(), any()) } returns SearchResponse(
            results = listOf(
                SearchResult(
                    relativePath = "com/example/TinySource.kt",
                    file = sourceFile,
                    line = 2,
                    snippet = "class TinySource",
                    score = 1.0f,
                    matchLines = listOf(2)
                )
            )
        )
    }

    @ParameterizedTest(name = "production search provider handles {0} through MCP")
    @EnumSource(SearchType::class)
    fun `production search providers are available through MCP`(searchType: SearchType) = runTest {
        val result = server.client.callTool(
            ToolNames.SEARCH_DEPENDENCY_SOURCES,
            buildJsonObject {
                put("projectRoot", tempDir.toString())
                put("projectPath", ":")
                put("query", "TinySource")
                put("searchType", searchType.name)
            }
        ) as CallToolResult
        val text = assertNotNull(result.content.filterIsInstance<TextContent>().firstOrNull()?.text)

        assertFalse(result.isError == true, "Expected $searchType search to succeed, but MCP returned: $text")
        assertContains(text, "Sources root:")
        assertContains(text, "com/example/TinySource.kt:2")
        assertContains(text, "class TinySource")
    }
}
