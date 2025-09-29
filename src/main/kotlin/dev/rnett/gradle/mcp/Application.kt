package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServer
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.mcp.add
import dev.rnett.gradle.mcp.tools.GradleExecutionTools
import dev.rnett.gradle.mcp.tools.GradleIntrospectionTools
import dev.rnett.gradle.mcp.tools.RelatedTools
import io.ktor.server.config.getAs
import io.ktor.server.engine.CommandLineConfig
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.di.dependencies
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Application(val args: Array<String>) {

    private val config = CommandLineConfig(args)

    private val json = Json {
        isLenient = true
        coerceInputValues = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }


    val gradleConfig = config.rootConfig.environment.config.property("gradle").getAs<GradleConfiguration>()

    val provider = GradleProvider(gradleConfig)

    val components: List<McpServerComponent> = listOf(
        RelatedTools(),
        GradleIntrospectionTools(provider),
        GradleExecutionTools(provider),
    )

    private fun createServer(): McpServer {
        return McpServer(
            Implementation("gradle-mcp", "0.0.1"),
            ServerOptions(
                ServerCapabilities(
                    logging = EmptyJsonObject,
                    tools = ServerCapabilities.Tools(false)
                ),
                enforceStrictCapabilities = false
            ),
            json
        ).apply {
            components.forEach { add(it) }
        }
    }

    fun startStdio() = runBlocking {
        val transport = StdioServerTransport(
            System.`in`.asSource().buffered(),
            System.out.asSink().buffered()
        )
        createServer().apply {
            connect(transport)
            suspendCoroutine {
                onClose { it.resume(Unit) }
            }
        }
    }

    fun startServer() = runBlocking {
        val server = EngineMain.createServer(args)
        server.application.apply {
            dependencies {
                provide { json }
            }

            mcp {
                createServer()
            }
        }

        server.start(wait = true)
    }

    companion object {
        @JvmStatic
        fun stdio(args: Array<String>) {
            System.err.println("Starting STDIO transport...")
            Application(args).startStdio()
        }

        @JvmStatic
        fun server(args: Array<String>) {
            System.err.println("Starting SSE transport...")
            Application(args).startServer()
        }

        @JvmStatic
        fun main(args: Array<String>): Unit = runBlocking {
            if (args.getOrNull(0) == "stdio") {
                stdio(args.drop(1).toTypedArray())
            } else {
                server(args)
            }
        }
    }
}