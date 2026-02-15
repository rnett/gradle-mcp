package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.Application.Companion.LOGGER
import dev.rnett.gradle.mcp.mcp.McpServer
import io.ktor.server.engine.CommandLineConfig
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.netty.EngineMain
import io.ktor.server.netty.NettyApplicationEngine
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.lang.management.ManagementFactory
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed class Transport(val name: String) {
    abstract suspend fun start(application: Application, wait: Boolean)
    abstract suspend fun stop(application: Application)

    class Stdio(val input: Source, val output: Sink) : Transport("STDIO") {
        private var started = false
        override suspend fun start(application: Application, wait: Boolean) {

            if (started) error("Already started")

            started = true
            val transport = StdioServerTransport(
                input,
                output,
            )
            val mcpServer: McpServer = try {
                application.koinContext.get<McpServer>()
            } catch (t: Throwable) {
                Application.LOGGER.error("Failed to initialize MCP Server", t)
                throw t
            }
            val job = application.scope.launch {
                mcpServer.apply {
                    connect(transport)
                    suspendCoroutine {
                        onClose { it.resume(Unit) }
                    }
                }
            }
            if (wait)
                job.join()
        }

        override suspend fun stop(application: Application) {
            if (started) {
                application.koinContext.get<McpServer>().close()
            }
        }
    }

    class Sse : Transport("SSE") {
        private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

        override suspend fun start(application: Application, wait: Boolean) {
            if (server != null) error("Already started")
            val server = EngineMain.createServer(application.args)
            this.server = server
            server.application.apply {
                mcp {
                    val mcpServer: McpServer = try {
                        application.koinContext.get<McpServer>()
                    } catch (t: Throwable) {
                        LOGGER.error("Failed to initialize MCP Server", t)
                        throw t
                    }
                    mcpServer
                }
            }

            server.startSuspend(wait = wait)
        }

        override suspend fun stop(application: Application) {
            server?.stopSuspend()
        }
    }
}

class Application(val args: Array<String>, val transport: Transport) {

    init {
        val pid = try {
            ProcessHandle.current().pid().toString()
        } catch (t: Throwable) {
            ManagementFactory.getRuntimeMXBean().name.split("@")[0]
        }
        MDC.put("PID", pid)
    }

    private val config = CommandLineConfig(args)
    private val koinApp: org.koin.core.KoinApplication = DI.createKoin(config.rootConfig.environment.config)
    val koinContext: org.koin.core.Koin = koinApp.koin
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
        LOGGER.error("Unhandled exception in coroutine", throwable)
    })

    suspend fun start(wait: Boolean = true) {
        System.err.println("Starting Gradle MCP server with ${transport.name} transport...")
        withContext(Dispatchers.IO) {
            try {
                transport.start(this@Application, wait)
            } catch (t: Throwable) {
                LOGGER.error("Fatal uncaught error", t)
                System.err.println("Fatal uncaught error: ${t.message}")
                t.printStackTrace()
                throw t
            }
        }
    }

    suspend fun stop() {
        withContext(Dispatchers.IO) {
            try {
                transport.stop(this@Application)
            } catch (t: Throwable) {
                LOGGER.error("Fatal uncaught error while stopping the server", t)
                System.err.println("Fatal uncaught error: ${t.message}")
                t.printStackTrace()
                throw t
            }
        }
    }

    companion object {
        val LOGGER = LoggerFactory.getLogger(Application::class.java)

        @JvmStatic
        suspend fun stdio(args: Array<String>) {
            Application(args, Transport.Stdio(System.`in`.asSource().buffered(), System.out.asSink().buffered())).start()
        }

        @JvmStatic
        suspend fun server(args: Array<String>) {
            Application(args, Transport.Sse()).start()
        }

        @JvmStatic
        suspend fun main(args: Array<String>) {
            if (args.getOrNull(0) == "stdio") {
                stdio(args.drop(1).toTypedArray())
            } else {
                server(args)
            }
        }
    }
}