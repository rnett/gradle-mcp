package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.Application.Companion.LOGGER
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.mcp.closeServer
import io.ktor.server.engine.CommandLineConfig
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.netty.EngineMain
import io.ktor.server.netty.NettyApplicationEngine
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicBoolean
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
                output
            ) {
                // Defaults are intentional; identical to the deprecated 2-arg constructor's behavior.
            }
            val mcpServer = Application.resolveServer(application)
            val job = application.scope.launch {
                val session = mcpServer.createSession(transport)
                suspendCoroutine {
                    session.onClose { it.resume(Unit) }
                }
            }
            if (wait)
                job.join()
        }

        override suspend fun stop(application: Application) {
            if (started) {
                Application.closeResolvedServer(application)
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
                    Application.resolveServer(application)
                }
            }

            server.startSuspend(wait = wait)
        }

        override suspend fun stop(application: Application) {
            server?.stopSuspend()
            Application.closeResolvedServer(application)
        }
    }

    class StreamableHttp : Transport("STREAMABLE_HTTP") {
        private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

        override suspend fun start(application: Application, wait: Boolean) {
            if (server != null) error("Already started")
            val server = EngineMain.createServer(application.args)
            this.server = server
            server.application.apply {
                mcpStreamableHttp {
                    Application.resolveServer(application)
                }
            }
            server.startSuspend(wait = wait)
        }

        override suspend fun stop(application: Application) {
            server?.stopSuspend()
            Application.closeResolvedServer(application)
        }
    }
}

class Application(val args: Array<String>, val transport: Transport) {

    private val stopped = AtomicBoolean(false)

    private val shutdownHook = Thread {
        try {
            runBlocking { stop() }
        } catch (t: Throwable) {
            try {
                LOGGER.warn("Shutdown hook stop() failed", t)
            } catch (_: Throwable) {
                // Last resort: suppress logging failures during shutdown.
            }
        }
    }

    init {
        val pid = try {
            ProcessHandle.current().pid().toString()
        } catch (t: Throwable) {
            ManagementFactory.getRuntimeMXBean().name.split("@")[0]
        }
        MDC.put("PID", pid)
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook)
        } catch (e: IllegalStateException) {
            LOGGER.debug("Failed to register shutdown hook: JVM already shutting down", e)
        } catch (e: SecurityException) {
            LOGGER.warn("Failed to register shutdown hook: security manager denied", e)
        }
    }

    private val config = CommandLineConfig(args)
    internal val koinApp: org.koin.core.KoinApplication = DI.createKoin(config.rootConfig.environment.config)
    val koinContext: org.koin.core.Koin = koinApp.koin
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
        LOGGER.error("Unhandled exception in coroutine", throwable)
    })

    suspend fun start(wait: Boolean = true) {
        System.err.println("Starting Gradle MCP server with ${transport.name} transport...")
        withContext(Dispatchers.IO) {
            try {
                // Construct the server eagerly at startup for all transports so the bounded latest-stable-version
                // resolution runs before any request arrives and never on Netty threads.
                resolveServer(this@Application)
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
        if (!stopped.compareAndSet(false, true)) return

        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        } catch (_: IllegalArgumentException) {
            // Hook was not registered.
        } catch (_: IllegalStateException) {
            // JVM is already shutting down; hook is running or cannot be removed.
        } catch (_: SecurityException) {
            LOGGER.warn("Failed to remove shutdown hook: security manager denied")
        }

        withContext(Dispatchers.IO) {
            var firstError: Throwable? = null

            fun recordError(t: Throwable) {
                if (firstError == null) firstError = t
                LOGGER.error("Error during Application.stop()", t)
            }

            // 1. Close MCP server and components (via transport). Best-effort.
            try {
                transport.stop(this@Application)
            } catch (t: Throwable) {
                recordError(t)
            }

            // 2. Cancel the application coroutine scope. Best-effort.
            try {
                scope.cancel()
            } catch (t: Throwable) {
                recordError(t)
            }

            // 3. Dispose AutoCloseable singletons held by Koin (defense-in-depth).
            //    Primary disposal is via Koin onClose callbacks registered in DI.createModule
            //    (HttpClient, LuceneReaderCache, GradleConnectionService, GradleProvider, ReplManager).
            //    This explicit pass ensures any bean that was created but missed an onClose binding
            //    is still closed, and that close failures are isolated per bean.
            try {
                val closeables = buildList<AutoCloseable> {
                    koinContext.getOrNull<io.ktor.client.HttpClient>()?.let { add(it) }
                    koinContext.getOrNull<dev.rnett.gradle.mcp.lucene.LuceneReaderCache>()?.let { add(it) }
                    (koinContext.getOrNull<dev.rnett.gradle.mcp.gradle.GradleConnectionService>() as? AutoCloseable)?.let { add(it) }
                    (koinContext.getOrNull<dev.rnett.gradle.mcp.gradle.GradleProvider>() as? AutoCloseable)?.let { add(it) }
                    (koinContext.getOrNull<dev.rnett.gradle.mcp.repl.ReplManager>() as? AutoCloseable)?.let { add(it) }
                }
                for (c in closeables) {
                    try {
                        c.close()
                    } catch (t: Throwable) {
                        LOGGER.warn("Failed to close AutoCloseable bean {}", c::class.simpleName, t)
                    }
                }
            } catch (t: Throwable) {
                recordError(t)
            }

            // 4. Close the Koin container (drops remaining instances, invokes onClose callbacks, closes scopes).
            try {
                koinApp.close()
            } catch (t: Throwable) {
                recordError(t)
            }

            firstError?.let { throw it }
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(Application::class.java)

        /**
         * Resolves the SDK server from the Koin context, with consistent error handling across all transports.
         */
        @JvmStatic
        fun resolveServer(application: Application): Server = try {
            application.koinContext.get<Server>()
        } catch (t: Throwable) {
            LOGGER.error("Failed to initialize MCP Server", t)
            throw t
        }

        suspend fun closeResolvedServer(application: Application) {
            val server = application.koinContext.get<Server>()
            val components = application.koinContext.get<List<McpServerComponent>>()
            closeServer(server, components)
        }

        @JvmStatic
        fun stdio(args: Array<String>) = runBlocking {
            val out = System.out
            System.setOut(System.err)
            val app = Application(args, Transport.Stdio(System.`in`.asSource().buffered(), out.asSink().buffered()))
            try {
                app.start()
            } finally {
                app.stop()
            }
        }

        @JvmStatic
        fun server(args: Array<String>) = runBlocking {
            // Streamable HTTP is the default Ktor transport; SSE remains available via Transport.Sse.
            val app = Application(args, Transport.StreamableHttp())
            try {
                app.start()
            } finally {
                app.stop()
            }
        }

        @JvmStatic
        fun streamableHttp(args: Array<String>) = runBlocking {
            val app = Application(args, Transport.StreamableHttp())
            try {
                app.start()
            } finally {
                app.stop()
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            if (System.getenv("JBANG_CDS_DUMP") == "true" || args.contains("--version") || args.contains("-v")) {
                println("gradle-mcp version ${BuildConfig.APP_VERSION}")
                if (System.getenv("JBANG_CDS_DUMP") == "true") {
                    // Warm up the default Ktor transport (Streamable HTTP) so the CDS archive captures its classes.
                    val app = Application(args, Transport.StreamableHttp())
                    app.koinContext.get<Server>()
                }
                return
            }

            val mode = args.getOrNull(0)
            if (mode == "stdio" || args.isEmpty()) {
                stdio(if (mode == "stdio") args.drop(1).toTypedArray() else args)
            } else if (mode == "server") {
                server(args.drop(1).toTypedArray())
            } else if (mode == "streamable-http") {
                streamableHttp(args.drop(1).toTypedArray())
            } else {
                server(args)
            }
        }
    }
}
