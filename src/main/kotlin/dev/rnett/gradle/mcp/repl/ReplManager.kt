package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString

interface ReplManager {
    fun getOrCreateProcess(sessionId: String, config: ReplConfig, javaExecutable: String = "java"): Process
    fun terminateSession(sessionId: String)
    fun closeAll()
}

/**
 * Manager for REPL sessions.
 * Implements Step 4 of the REPL implementation plan.
 */
class DefaultReplManager(
    private val bundledJarProvider: BundledJarProvider = DefaultBundledJarProvider()
) : ReplManager {
    private val sessions = ConcurrentHashMap<String, ReplSession>()

    private data class ReplSession(
        val process: Process,
        val config: ReplConfig
    )

    /**
     * Gets an existing worker process for the session or starts a new one if:
     * 1. No process exists for the session.
     * 2. The existing process is not alive.
     * 3. The provided [config] differs from the existing one (environment changed).
     */
    override fun getOrCreateProcess(sessionId: String, config: ReplConfig, javaExecutable: String): Process {
        val existingSession = sessions[sessionId]

        if (existingSession != null) {
            if (existingSession.process.isAlive && existingSession.config == config) {
                return existingSession.process
            }
            // Terminate if config changed or process died
            terminateSession(sessionId)
        }

        return startProcess(sessionId, config, javaExecutable)
    }

    private fun startProcess(sessionId: String, config: ReplConfig, javaExecutable: String): Process {
        LOGGER.info("Starting REPL worker for session $sessionId")
        val workerJar = bundledJarProvider.extractJar(dev.rnett.gradle.mcp.BuildConfig.REPL_WORKER_JAR)

        val process = ProcessBuilder(
            javaExecutable,
            "-jar",
            workerJar.absolutePathString()
        ).apply {
            redirectError(ProcessBuilder.Redirect.INHERIT)
        }.start()

        // Send config to worker's stdin
        val configLine = Json.encodeToString(config)
        process.outputStream.bufferedWriter().apply {
            write(configLine)
            newLine()
            flush()
        }

        sessions[sessionId] = ReplSession(process, config)
        return process
    }

    /**
     * Terminates the worker process for the given session.
     */
    override fun terminateSession(sessionId: String) {
        sessions.remove(sessionId)?.let {
            LOGGER.info("Terminating REPL worker for session $sessionId")
            it.process.destroy()
            if (!it.process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                it.process.destroyForcibly()
            }
        }
    }

    /**
     * Terminates all active worker processes.
     */
    override fun closeAll() {
        sessions.keys().toList().forEach { terminateSession(it) }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultReplManager::class.java)
    }
}
