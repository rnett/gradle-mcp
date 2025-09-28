package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServer
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.mcp.add
import dev.rnett.gradle.mcp.tools.GradleExecutionTools
import dev.rnett.gradle.mcp.tools.GradleIntrospectionTools
import dev.rnett.gradle.mcp.tools.RelatedTools
import io.ktor.server.application.Application
import io.ktor.server.config.property
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.serialization.json.Json

fun interface McpServerContributor {
    context(application: Application)
    fun contribute(server: McpServer)
}

suspend fun main(args: Array<String>) {
    val server = EngineMain.createServer(args)
    server.application.apply {
        dependencies {
            provide { this@apply.property<GradleConfiguration>("gradle") }
            provide(::GradleProvider)
            provide {
                Json {
                    isLenient = true
                    coerceInputValues = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
            }
        }

        val components: List<McpServerComponent> = listOf(
            RelatedTools(),
            GradleIntrospectionTools(dependencies.resolve()),
            GradleExecutionTools(dependencies.resolve()),
        )

        val server = McpServer(
            Implementation("gradle-mcp", "0.0.1"),
            ServerOptions(
                ServerCapabilities(
                    logging = EmptyJsonObject,
                    tools = ServerCapabilities.Tools(false)
                ),
                enforceStrictCapabilities = false
            ),
            dependencies.resolve()
        ).apply {
            components.forEach { add(it) }
        }

        mcp {
            server
        }
    }

    server.start(wait = true)
}