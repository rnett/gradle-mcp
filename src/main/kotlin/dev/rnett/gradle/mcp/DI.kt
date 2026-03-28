package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.dependencies.DefaultGradleDependencyService
import dev.rnett.gradle.mcp.dependencies.DefaultGradleSourceService
import dev.rnett.gradle.mcp.dependencies.DefaultSourceIndexService
import dev.rnett.gradle.mcp.dependencies.DefaultSourceStorageService
import dev.rnett.gradle.mcp.dependencies.DefaultSourcesService
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.GradleSourceService
import dev.rnett.gradle.mcp.dependencies.SourceIndexService
import dev.rnett.gradle.mcp.dependencies.SourceStorageService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.gradle.DefaultDistributionDownloaderService
import dev.rnett.gradle.mcp.dependencies.gradle.DistributionDownloaderService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.ContentExtractorService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DefaultContentExtractorService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DefaultGradleDocsIndexService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DefaultGradleDocsService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DefaultMarkdownService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsIndexService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.HtmlConverter
import dev.rnett.gradle.mcp.dependencies.gradle.docs.MarkdownService
import dev.rnett.gradle.mcp.dependencies.search.DefaultIndexService
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultGradleConnectionService
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleConnectionService
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.gradle.build.BuildExecutionService
import dev.rnett.gradle.mcp.gradle.build.DefaultBuildExecutionService
import dev.rnett.gradle.mcp.lucene.LuceneReaderCache
import dev.rnett.gradle.mcp.maven.DefaultDepsDevService
import dev.rnett.gradle.mcp.maven.DefaultMavenCentralService
import dev.rnett.gradle.mcp.maven.DefaultMavenRepoService
import dev.rnett.gradle.mcp.maven.DepsDevService
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
import dev.rnett.gradle.mcp.utils.DefaultEnvProvider
import dev.rnett.gradle.mcp.utils.EnvProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.xml.xml
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
        install(HttpTimeout) {
            requestTimeoutMillis = 300000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 300000
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
        single<EnvProvider> { DefaultEnvProvider }
        single<ReplManager> { DefaultReplManager(get()) }
        single<ReplEnvironmentService> { DefaultReplEnvironmentService(get()) }
        single<MarkdownService> { DefaultMarkdownService(get()) }
        single { HtmlConverter(get()) }
        single { LuceneReaderCache() }
        single<DistributionDownloaderService> { DefaultDistributionDownloaderService(get(), get()) }
        single<ContentExtractorService> { DefaultContentExtractorService(get(), get(), get()) }
        single<GradleDocsIndexService> { DefaultGradleDocsIndexService(get(), get(), get(), get()) }
        single<GradleVersionService> { DefaultGradleVersionService(get()) }
        single<GradleDocsService> { DefaultGradleDocsService(get(), get(), get(), get()) }
        single<GradleDependencyService> { DefaultGradleDependencyService(get()) }
        single<MavenRepoService> { DefaultMavenRepoService(get()) }
        single<MavenCentralService> { DefaultMavenCentralService(get()) }
        single<DepsDevService> { DefaultDepsDevService(get()) }
        single<IndexService> { DefaultIndexService(get()) }
        single<SourceStorageService> { DefaultSourceStorageService(get()) }
        single<CoroutineDispatcher> { Dispatchers.IO }
        single<SourceIndexService> { DefaultSourceIndexService(get()) }
        single<SourcesService> { DefaultSourcesService(get(), get(), get(), get()) }
        single<GradleSourceService> { DefaultGradleSourceService(get(), get(), get(), get(), get()) }
        single { BuildManager() }
        single<GradleConnectionService> { DefaultGradleConnectionService() }
        single<BuildExecutionService> { DefaultBuildExecutionService(envProvider = get()) }
        single<GradleProvider> {
            DefaultGradleProvider(
                get(),
                connectionService = get(),
                executionService = get(),
                buildManager = get()
            )
        }

        single {
            val provider: GradleProvider = get()
            val replManager: ReplManager = get()
            val replEnvironmentService: ReplEnvironmentService = get()
            val gradleDocsService: GradleDocsService = get()
            val gradleVersionService: GradleVersionService = get()
            val gradleDependencyService: GradleDependencyService = get()
            val depsDevService: DepsDevService = get()
            val sourcesService: SourcesService = get()
            val gradleSourceService: GradleSourceService = get()
            val indexService: SourceIndexService = get()
            components(
                provider,
                replManager,
                replEnvironmentService,
                gradleDocsService,
                gradleVersionService,
                gradleDependencyService,
                depsDevService,
                sourcesService,
                gradleSourceService,
                indexService
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
        gradleVersionService: GradleVersionService,
        gradleDependencyService: GradleDependencyService,
        depsDevService: DepsDevService,
        sourcesService: SourcesService,
        gradleSourceService: GradleSourceService,
        indexService: SourceIndexService
    ): List<McpServerComponent> = listOf(
        GradleExecutionTools(provider),
        ReplTools(provider, replManager, replEnvironmentService),
        GradleBuildLookupTools(provider.buildManager),
        GradleDocsTools(gradleDocsService, gradleVersionService),
        GradleDependencyTools(gradleDependencyService),
        DependencySearchTools(depsDevService),
        DependencySourceTools(sourcesService, gradleSourceService, indexService),
        SkillTools()
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
