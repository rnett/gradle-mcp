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
import org.slf4j.LoggerFactory
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class Application(val args: Array<String>) {

    private val config = CommandLineConfig(args)
    private val koinApp: org.koin.core.KoinApplication = DI.createKoin(config.rootConfig.environment.config)
    private val koinContext: org.koin.core.Koin = koinApp.koin

    val provider: GradleProvider = koinContext.get<GradleProvider>()
    val replManager: dev.rnett.gradle.mcp.repl.ReplManager = koinContext.get<dev.rnett.gradle.mcp.repl.ReplManager>()

    fun startStdio() = runBlocking {
        val transport = StdioServerTransport(
            System.`in`.asSource().buffered(),
            System.out.asSink().buffered()
        )
        val mcpServer: McpServer = try {
            koinContext.get<McpServer>()
        } catch (t: Throwable) {
            LOGGER.error("Failed to initialize MCP Server", t)
            throw t
        }
        mcpServer.apply {
            onClose {
                provider.close()
                runBlocking {
                    replManager.closeAll()
                }
            }
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
                val mcpServer: McpServer = try {
                    koinContext.get<McpServer>()
                } catch (t: Throwable) {
                    LOGGER.error("Failed to initialize MCP Server", t)
                    throw t
                }
                mcpServer.onClose {
                    provider.close()
                    runBlocking {
                        replManager.closeAll()
                    }
                }
                mcpServer
            }
        }

        server.start(wait = true)
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(Application::class.java)

        @JvmStatic
        fun stdio(args: Array<String>) {
            runAndLogErrors {
                System.err.println("Starting Gradle MCP server with STDIO transport...")
                Application(args).startStdio()
            }
        }

        @JvmStatic
        fun server(args: Array<String>) {
            runAndLogErrors {
                System.err.println("Starting Gradle MCP server with SSE transport...")
                Application(args).startServer()
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.getOrNull(0) == "stdio") {
                stdio(args.drop(1).toTypedArray())
            } else {
                server(args)
            }
        }

        private inline fun runAndLogErrors(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                LOGGER.error("Fatal uncaught error", t)
                System.err.println("Fatal uncaught error: ${t.message}")
                t.printStackTrace()
            }
        }
    }
}