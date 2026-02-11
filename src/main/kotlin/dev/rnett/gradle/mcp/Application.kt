package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServer
import io.ktor.server.engine.CommandLineConfig
import io.ktor.server.netty.EngineMain
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class Application(val args: Array<String>) {

    private val config = CommandLineConfig(args)
    private val koinApp: org.koin.core.KoinApplication = DI.createKoin(config.rootConfig.environment.config)
    private val koinContext: org.koin.core.Koin = koinApp.koin

    val provider: GradleProvider = koinContext.get<GradleProvider>()

    fun startStdio() = runBlocking {
        val transport = StdioServerTransport(
            System.`in`.asSource().buffered(),
            System.out.asSink().buffered()
        )
        val mcpServer: McpServer = koinContext.get<McpServer>()
        mcpServer.apply {
            onClose { provider.close() }
            connect(transport)
            suspendCoroutine {
                onClose { it.resume(Unit) }
            }
        }
    }

    fun startServer() = runBlocking {
        val server = EngineMain.createServer(args)
        server.application.apply {
            mcp {
                val mcpServer: McpServer = koinContext.get<McpServer>()
                mcpServer.onClose { provider.close() }
                mcpServer
            }
        }

        server.start(wait = true)
    }

    companion object {

        @JvmStatic
        fun stdio(args: Array<String>) {
            System.err.println("Starting Gradle MCP server with STDIO transport...")
            Application(args).startStdio()
        }

        @JvmStatic
        fun server(args: Array<String>) {
            System.err.println("Starting Gradle MCP server with SSE transport...")
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