package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

sealed class ReplSession {
    data class Running(val process: Process) : ReplSession()
    data class Terminated(val exitCode: Int, val output: String) : ReplSession()
}

interface ReplManager {
    fun startSession(sessionId: String, config: ReplConfig, javaExecutable: String): Process
    fun getSession(sessionId: String): ReplSession?
    suspend fun terminateSession(sessionId: String)
    suspend fun closeAll()
}

/**
 * Manager for REPL sessions.
 * Implements Step 4 of the REPL implementation plan.
 */
class DefaultReplManager(
    private val bundledJarProvider: BundledJarProvider = DefaultBundledJarProvider(),
    private val timeout: kotlin.time.Duration = 15.minutes,
    private val checkInterval: kotlin.time.Duration = 1.minutes
) : ReplManager {
    private val sessions = ConcurrentHashMap<String, ReplSessionState>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            while (isActive) {
                delay(checkInterval)
                checkTimeouts()
            }
        }
    }

    private suspend fun checkTimeouts() {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        sessions.keys().toList().forEach { sessionId ->
            val session = sessions[sessionId] ?: return@forEach
            if (now - session.lastActivity > timeout) {
                LOGGER.info("Session $sessionId timed out after $timeout of inactivity")
                terminateSession(sessionId)
            }
        }
    }

    private data class ReplSessionState(
        val process: Process,
        val config: ReplConfig,
        val logJobs: List<Job>,
        @Volatile var lastActivity: Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
        val outputBuffer: java.util.Queue<String> = java.util.concurrent.ConcurrentLinkedQueue()
    ) {
        fun addOutput(line: String) {
            outputBuffer.add(line)
            while (outputBuffer.size > 50) {
                outputBuffer.poll()
            }
        }
    }

    override fun startSession(sessionId: String, config: ReplConfig, javaExecutable: String): Process {
        scope.launch {
            terminateSession(sessionId)
        }
        return startProcess(sessionId, config, javaExecutable)
    }

    override fun getSession(sessionId: String): ReplSession? {
        val session = sessions[sessionId] ?: return null
        if (!session.process.isAlive) {
            val exitCode = session.process.exitValue()
            val output = session.outputBuffer.toList()
            scope.launch {
                terminateSession(sessionId)
            }
            return ReplSession.Terminated(exitCode, output.joinToString("\n"))
        }
        session.lastActivity = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        return ReplSession.Running(session.process)
    }

    private fun startProcess(sessionId: String, config: ReplConfig, javaExecutable: String): Process {
        LOGGER.info("Starting REPL worker for session $sessionId with javaExecutable=$javaExecutable and config=$config")
        val workerJar = bundledJarProvider.extractJar(dev.rnett.gradle.mcp.BuildConfig.REPL_WORKER_JAR)

        val process = ProcessBuilder(
            javaExecutable,
            "-jar",
            workerJar.absolutePathString()
        ).start()

        var session: ReplSessionState? = null

        val logJobs = listOf(
            scope.launch(Dispatchers.IO) {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        session?.addOutput(line)
                        MDC.put("sessionId", sessionId)
                        try {
                            LOGGER.info("REPL Worker stdout: $line")
                        } finally {
                            MDC.remove("sessionId")
                        }
                    }
                }
            },
            scope.launch(Dispatchers.IO) {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        session?.addOutput(line)
                        MDC.put("sessionId", sessionId)
                        try {
                            LOGGER.warn("REPL Worker stderr: $line")
                        } finally {
                            MDC.remove("sessionId")
                        }
                    }
                }
            }
        )

        // Send config to worker's stdin
        val configLine = Json.encodeToString(config)
        process.outputStream.bufferedWriter().apply {
            write(configLine)
            newLine()
            flush()
        }

        val newSession = ReplSessionState(process, config, logJobs)
        session = newSession
        sessions[sessionId] = newSession
        return process
    }

    /**
     * Terminates the worker process for the given session.
     */
    override suspend fun terminateSession(sessionId: String) {
        sessions.remove(sessionId)?.let {
            LOGGER.info("Terminating REPL worker for session $sessionId")
            it.logJobs.forEach { job -> job.cancel() }
            it.process.destroy()
            withContext(Dispatchers.IO) {
                if (!it.process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    it.process.destroyForcibly()
                }
            }
        }
    }

    /**
     * Terminates all active worker processes.
     */
    override suspend fun closeAll() {
        sessions.keys().toList().forEach { terminateSession(it) }
        scope.cancel()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultReplManager::class.java)
    }
}
