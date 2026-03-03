package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.gradle.dependencies.DefaultGradleDependencyService
import dev.rnett.gradle.mcp.gradle.dependencies.DefaultSourcesService
import dev.rnett.gradle.mcp.gradle.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.gradle.dependencies.SourcesService
import dev.rnett.gradle.mcp.gradle.dependencies.search.DefaultIndexService
import dev.rnett.gradle.mcp.gradle.dependencies.search.IndexService
import dev.rnett.gradle.mcp.maven.DefaultMavenCentralService
import dev.rnett.gradle.mcp.maven.DefaultMavenRepoService
import dev.rnett.gradle.mcp.maven.MavenCentralService
import dev.rnett.gradle.mcp.maven.MavenRepoService
import dev.rnett.gradle.mcp.mcp.McpServer
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.mcp.add
import dev.rnett.gradle.mcp.repl.DefaultReplEnvironmentService
import dev.rnett.gradle.mcp.repl.DefaultReplManager
import dev.rnett.gradle.mcp.repl.ReplEnvironmentService
import dev.rnett.gradle.mcp.repl.ReplManager
import dev.rnett.gradle.mcp.tools.GradleBuildLookupTools
import dev.rnett.gradle.mcp.tools.GradleDocsTools
import dev.rnett.gradle.mcp.tools.GradleExecutionTools
import dev.rnett.gradle.mcp.tools.ReplTools
import dev.rnett.gradle.mcp.tools.dependencies.DependencySearchTools
import dev.rnett.gradle.mcp.tools.dependencies.DependencySourceTools
import dev.rnett.gradle.mcp.tools.dependencies.GradleDependencyTools
import dev.rnett.gradle.mcp.tools.skills.SkillTools
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.xml.*
import io.ktor.server.config.*
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

object DI {
    val json = Json {
        isLenient = true
        coerceInputValues = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        decodeEnumsCaseInsensitive = true
    }

    val xml = XML {
        defaultPolicy {
            ignoreUnknownChildren()
        }
    }

    fun createHttpClient(json: Json = DI.json, xml: XML = DI.xml) = HttpClient {
        install(ContentNegotiation) {
            json(json)
            xml(xml, io.ktor.http.ContentType.Application.Xml)
            xml(xml, io.ktor.http.ContentType.Text.Xml)
        }
    }

    fun createModule(config: ApplicationConfig): Module = module {
        single { config }
        single { xml }
        single { json }
        single { config.property("gradle").getAs<GradleConfiguration>() }
        single { DefaultInitScriptProvider() } bind InitScriptProvider::class
        single { DefaultBundledJarProvider() } bind BundledJarProvider::class
        single { createHttpClient(get(), get()) }
        single { GradleMcpEnvironment.fromEnv() }
        single<ReplManager> { DefaultReplManager(get()) }
        single<ReplEnvironmentService> { DefaultReplEnvironmentService(get()) }
        single<MarkdownService> { DefaultMarkdownService(get()) }
        single<GradleDocsService> { DefaultGradleDocsService(get(), get(), get()) }
        single<GradleDependencyService> { DefaultGradleDependencyService(get()) }
        single<MavenRepoService> { DefaultMavenRepoService(get()) }
        single<MavenCentralService> { DefaultMavenCentralService(get()) }
        single<IndexService> { DefaultIndexService(get()) }
        single<SourcesService> { DefaultSourcesService(get(), get(), get()) }
        single { BuildManager() }
        single<GradleProvider> {
            DefaultGradleProvider(
                get(),
                initScriptProvider = get(),
                buildManager = get()
            )
        }

        single {
            val provider: GradleProvider = get()
            val replManager: ReplManager = get()
            val replEnvironmentService: ReplEnvironmentService = get()
            val gradleDocsService: GradleDocsService = get()
            val gradleDependencyService: GradleDependencyService = get()
            val mavenRepoService: MavenRepoService = get()
            val mavenCentralService: MavenCentralService = get()
            val sourcesService: SourcesService = get()
            components(
                provider,
                replManager,
                replEnvironmentService,
                gradleDocsService,
                gradleDependencyService,
                mavenRepoService,
                mavenCentralService,
                sourcesService
            )
        }

        single {
            val components: List<McpServerComponent> = get()
            McpServer(
                Implementation("gradle-mcp", BuildConfig.APP_VERSION),
                ServerOptions(
                    ServerCapabilities(
                        logging = EmptyJsonObject,
                        tools = ServerCapabilities.Tools(false)
                    ),
                    enforceStrictCapabilities = false
                ),
                get(),
                components
            ).apply {
                components.forEach { add(it) }
            }
        }
    }

    fun createKoin(config: ApplicationConfig): org.koin.core.KoinApplication = org.koin.dsl.koinApplication {
        modules(createModule(config))
    }

    fun components(
        provider: GradleProvider,
        replManager: ReplManager,
        replEnvironmentService: ReplEnvironmentService,
        gradleDocsService: GradleDocsService,
        gradleDependencyService: GradleDependencyService,
        mavenRepoService: MavenRepoService,
        mavenCentralService: MavenCentralService,
        sourcesService: SourcesService,
    ): List<McpServerComponent> = listOf(
        GradleExecutionTools(provider),
        ReplTools(provider, replManager, replEnvironmentService),
        GradleBuildLookupTools(provider.buildManager),
        GradleDocsTools(gradleDocsService),
        GradleDependencyTools(gradleDependencyService),
        DependencySearchTools(mavenRepoService, mavenCentralService),
        DependencySourceTools(sourcesService),
        SkillTools(),
    )

    fun createServer(json: Json, components: List<McpServerComponent>): McpServer {
        return McpServer(
            Implementation("gradle-mcp", BuildConfig.APP_VERSION),
            ServerOptions(
                ServerCapabilities(
                    logging = EmptyJsonObject,
                    tools = ServerCapabilities.Tools(false)
                ),
                enforceStrictCapabilities = false
            ),
            json,
            components
        ).apply {
            components.forEach { add(it) }
        }
    }
}
