package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.gradle.BackgroundBuildManager
import dev.rnett.gradle.mcp.gradle.BuildResults
import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.InitScriptProvider
import dev.rnett.gradle.mcp.mcp.McpServer
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.mcp.add
import dev.rnett.gradle.mcp.repl.DefaultReplManager
import dev.rnett.gradle.mcp.repl.ReplManager
import dev.rnett.gradle.mcp.tools.BackgroundBuildTools
import dev.rnett.gradle.mcp.tools.GradleBuildLookupTools
import dev.rnett.gradle.mcp.tools.GradleExecutionTools
import dev.rnett.gradle.mcp.tools.GradleIntrospectionTools
import dev.rnett.gradle.mcp.tools.ReplTools
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
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
        single { BackgroundBuildManager() }
        single { BuildResults(get()) }
        single<ReplManager> { DefaultReplManager(get()) }
        single<GradleProvider> {
            DefaultGradleProvider(
                get(),
                initScriptProvider = get(),
                backgroundBuildManager = get(),
                buildResults = get()
            )
        }

        single {
            val provider: GradleProvider = get()
            val replManager: ReplManager = get()
            listOf<McpServerComponent>(
                GradleIntrospectionTools(provider),
                GradleExecutionTools(provider),
                ReplTools(provider, replManager),
                BackgroundBuildTools(provider),
                GradleBuildLookupTools(provider.buildResults),
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

    fun components(provider: GradleProvider, replManager: ReplManager): List<McpServerComponent> = listOf(
        GradleIntrospectionTools(provider),
        GradleExecutionTools(provider),
        ReplTools(provider, replManager),
        BackgroundBuildTools(provider),
        GradleBuildLookupTools(provider.buildResults),
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
