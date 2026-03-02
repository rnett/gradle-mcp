package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultGradleDependencyService
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleDependencyService
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.mcp.McpServer
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.mcp.add
import dev.rnett.gradle.mcp.repl.DefaultReplEnvironmentService
import dev.rnett.gradle.mcp.repl.DefaultReplManager
import dev.rnett.gradle.mcp.repl.ReplEnvironmentService
import dev.rnett.gradle.mcp.repl.ReplManager
import dev.rnett.gradle.mcp.tools.BackgroundBuildTools
import dev.rnett.gradle.mcp.tools.GradleBuildLookupTools
import dev.rnett.gradle.mcp.tools.GradleDocsTools
import dev.rnett.gradle.mcp.tools.GradleExecutionTools
import dev.rnett.gradle.mcp.tools.ReplTools
import io.ktor.client.*
import io.ktor.server.config.*
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import kotlinx.serialization.json.Json
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

    fun createModule(config: ApplicationConfig): Module = module {
        single { config }
        single { json }
        single { config.property("gradle").getAs<GradleConfiguration>() }
        single { DefaultInitScriptProvider() } bind InitScriptProvider::class
        single { DefaultBundledJarProvider() } bind BundledJarProvider::class
        single { HttpClient() }
        single { GradleMcpEnvironment.fromEnv() }
        single<ReplManager> { DefaultReplManager(get()) }
        single<ReplEnvironmentService> { DefaultReplEnvironmentService(get()) }
        single<MarkdownService> { DefaultMarkdownService(get()) }
        single<GradleDocsService> { DefaultGradleDocsService(get(), get(), get()) }
        single<GradleDependencyService> { DefaultGradleDependencyService(get()) }
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
            components(provider, replManager, replEnvironmentService, gradleDocsService)
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
    ): List<McpServerComponent> = listOf(
        GradleExecutionTools(provider),
        ReplTools(provider, replManager, replEnvironmentService),
        BackgroundBuildTools(provider),
        GradleBuildLookupTools(provider.buildManager),
        GradleDocsTools(gradleDocsService),
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
