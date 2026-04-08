package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.gradle.BundledJarProvider
import dev.rnett.gradle.mcp.gradle.DefaultBundledJarProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.Scanner
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.time.Instant

sealed class ReplSession {
    data class Running(val process: Process) : ReplSession()
    data class Terminated(val exitCode: Int, val output: String) : ReplSession()
}

interface ReplManager : AutoCloseable {
    suspend fun startSession(sessionId: String, config: ReplConfig, javaExecutable: String): Process
    fun getSession(sessionId: String): ReplSession?
    suspend fun terminateSession(sessionId: String)
    suspend fun closeAll()

    suspend fun sendRequest(sessionId: String, command: ReplRequest): Flow<ReplResponse>

    override fun close() {
        kotlinx.coroutines.runBlocking {
            closeAll()
        }
    }
}

/**
 * Manager for REPL sessions.
 * Implements Step 4 of the REPL implementation plan.
 */
class DefaultReplManager(
    private val bundledJarProvider: BundledJarProvider = DefaultBundledJarProvider()
) : ReplManager {
    private val sessions = ConcurrentHashMap<String, ReplSessionState>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private data class ReplSessionState(
        val process: Process,
        val config: ReplConfig,
        val logJobs: MutableList<Job> = mutableListOf(),
        @Volatile var lastActivity: Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
        val outputBuffer: java.util.Queue<String> = java.util.concurrent.ConcurrentLinkedQueue(),
        private val _responses: MutableSharedFlow<ReplResponse> = MutableSharedFlow(200, extraBufferCapacity = 50, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    ) {
        @Volatile
        var terminalError: ReplResponse.Result.InternalError? = null
        val responses = _responses.asSharedFlow()

        suspend fun emitResponse(response: ReplResponse) {
            if (response is ReplResponse.Result.InternalError) {
                terminalError = response
            }
            _responses.emit(response)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        fun clearResponsesBuffer() {
            _responses.resetReplayCache()
        }

        fun addOutput(line: String) {
            outputBuffer.add(line)
            while (outputBuffer.size > 100) {
                outputBuffer.poll()
            }
        }

        fun getRecentOutput(limit: Int = 10): String {
            return outputBuffer.toList().takeLast(limit).joinToString("\n")
        }
    }

    override suspend fun startSession(sessionId: String, config: ReplConfig, javaExecutable: String): Process {
        // Ensure any existing session is fully terminated before starting a new one to avoid races
        if (sessions.containsKey(sessionId)) {
            terminateSession(sessionId)
        }
        return startProcess(sessionId, config, javaExecutable)
    }

    override fun getSession(sessionId: String): ReplSession? {
        val session = sessions[sessionId] ?: return null
        if (!session.process.isAlive) {
            sessions.remove(sessionId)
            val exitCode = session.process.exitValue()
            val output = session.outputBuffer.toList()
            cleanupSession(session)
            return ReplSession.Terminated(exitCode, output.joinToString("\n"))
        }
        // Keep lastActivity update for potential future use, but manager no longer enforces timeouts
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
        ).apply {
            environment().putAll(config.env)
        }.start()

        LOGGER.info("Process started: {}", process.pid())

        val session = ReplSessionState(process, config)
        sessions[sessionId] = session

        // Independent watchdog coroutine to catch crashes immediately, even if streams are hung by zombie subprocesses
        session.logJobs.add(
            scope.launch(Dispatchers.IO) {
                process.onExit().asDeferred().await()
                if (isActive && session.terminalError == null) {
                    val exitCode = process.exitValue()
                    val recentOutput = session.getRecentOutput()
                    val message = buildString {
                        append("The REPL worker process terminated unexpectedly with exit code $exitCode.")
                        if (recentOutput.isNotEmpty()) {
                            append("\n\nRecent output (last 10 lines):\n$recentOutput")
                        }
                    }
                    session.emitResponse(
                        ReplResponse.Result.InternalError(message)
                    )
                }
            }
        )

        session.logJobs.add(
            scope.launch(Dispatchers.IO) {
                try {
                    val scanner = Scanner(process.inputStream)
                    while (scanner.hasNextLine()) {
                        val line = scanner.nextLine() ?: break
                        if (line.startsWith(ReplResponse.RPC_PREFIX)) {
                            val jsonLine = line.removePrefix(ReplResponse.RPC_PREFIX)
                            try {
                                val response = Json.decodeFromString<ReplResponse>(jsonLine)
                                session.emitResponse(response)
                                if (response is ReplResponse.Logging) {
                                    logMessage(process, sessionId, response)
                                }
                            } catch (e: Exception) {
                                LOGGER.error("Failed to decode REPL RPC message: $line", e)
                                session.addOutput("STDOUT: $line")
                                logStdout(sessionId, line)
                            }
                        } else {
                            session.addOutput("STDOUT: $line")
                            logStdout(sessionId, line)
                        }
                    }
                } catch (e: Exception) {
                    LOGGER.error("Error reading REPL worker output (sessionId={})", sessionId, e)
                }
            }
        )
        session.logJobs.add(
            scope.launch(Dispatchers.IO) {
                val scanner = Scanner(process.errorStream)
                while (scanner.hasNextLine()) {
                    val line = scanner.nextLine() ?: break
                    session.addOutput("STDERR: $line")
                    MDC.put("sessionId", sessionId)
                    try {
                        LOGGER.warn("REPL Worker stderr: $line")
                    } finally {
                        MDC.remove("sessionId")
                    }
                }
            }
        )

        // Send config to worker's stdin
        val configLine = Json.encodeToString(config)
        val writer = process.outputStream.bufferedWriter()
        writer.write(configLine)
        writer.newLine()
        writer.flush()

        return process
    }

    private fun logMessage(process: Process, sessionId: String, message: ReplResponse.Logging) {
        LoggerFactory.getLogger(message.loggerName)
            .atLevel(message.level.toSlf4j())
            .addKeyValue("replSessionId", sessionId)
            .addKeyValue("replProcessId", process.pid())
            .log(message.message)
    }

    private fun ReplResponse.Logging.Level.toSlf4j(): org.slf4j.event.Level {
        return when (this) {
            ReplResponse.Logging.Level.DEBUG -> org.slf4j.event.Level.DEBUG
            ReplResponse.Logging.Level.INFO -> org.slf4j.event.Level.INFO
            ReplResponse.Logging.Level.WARN -> org.slf4j.event.Level.WARN
            ReplResponse.Logging.Level.ERROR -> org.slf4j.event.Level.ERROR
        }
    }

    /**
     * Terminates the worker process for the given session.
     */
    override suspend fun terminateSession(sessionId: String) {
        sessions.remove(sessionId)?.let {
            LOGGER.info("Terminating REPL worker for session $sessionId")
            it.process.destroy()
            withContext(Dispatchers.IO) {
                // Also attempt to kill any child processes to avoid lingering process trees on Windows
                runCatching {
                    it.process.toHandle().descendants().forEach { ph ->
                        runCatching { ph.destroyForcibly() }
                    }
                }
                it.process.destroyForcibly()
                // Ensure the process has actually exited before returning
                it.process.waitFor()
            }
            LOGGER.info("REPL worker for session $sessionId terminated with exit code ${it.process.exitValue()}")
            cleanupSession(it)
        }
    }

    private fun cleanupSession(session: ReplSessionState) {
        // Close all process streams to unblock any readers/writers
        runCatching { session.process.outputStream.close() }
        runCatching { session.process.inputStream.close() }
        runCatching { session.process.errorStream.close() }

        // Cancel log collection coroutines and wait briefly for them to finish to avoid hangs on blocked reads
        session.logJobs.forEach { job ->
            job.cancel()
        }
    }

    private fun logStdout(sessionId: String, line: String) {
        MDC.put("sessionId", sessionId)
        try {
            LOGGER.info("REPL Worker stdout: $line")
        } finally {
            MDC.remove("sessionId")
        }
    }

    override suspend fun sendRequest(sessionId: String, command: ReplRequest): Flow<ReplResponse> = flow {
        val session = sessions[sessionId] ?: error("No active REPL session with ID $sessionId")

        session.terminalError?.let {
            emit(it)
            return@flow
        }

        if (!session.process.isAlive) {
            val exitCode = session.process.exitValue()
            val recentOutput = session.getRecentOutput()
            val message = buildString {
                append("The REPL worker process terminated unexpectedly with exit code $exitCode.")
                if (recentOutput.isNotEmpty()) {
                    append("\n\nRecent output (last 10 lines):\n$recentOutput")
                }
            }
            val error = ReplResponse.Result.InternalError(message)
            session.emitResponse(error)
            emit(error)
            return@flow
        }

        val json = Json.encodeToString(command)

        session.clearResponsesBuffer()

        // Check again after clearing buffer to catch races with the log monitoring coroutines
        session.terminalError?.let {
            emit(it)
            return@flow
        }

        try {
            withContext(Dispatchers.IO) {
                val line = ReplResponse.RPC_PREFIX + json + '\n'
                session.process.outputStream.write(line.encodeToByteArray())
                session.process.outputStream.flush()
            }
        } catch (e: java.io.IOException) {
            LOGGER.warn("IOException while sending request: ${e.message}")
            val exitCode = if (!session.process.isAlive) {
                session.process.exitValue()
            } else {
                null
            }

            val recentOutput = session.getRecentOutput()
            val errorMessage = if (exitCode != null) {
                buildString {
                    append("The REPL worker process terminated unexpectedly with exit code $exitCode. ")
                    append("This usually indicates a fatal error during initialization or code execution. ")
                    if (recentOutput.isNotEmpty()) {
                        append("\n\nRecent output (last 10 lines):\n$recentOutput")
                    } else {
                        append("Check the logs for details.")
                    }
                }
            } else {
                "Failed to send request to REPL worker: ${e.message}"
            }

            val error = ReplResponse.Result.InternalError(errorMessage, e.stackTraceToString())
            session.terminalError = error
            session.emitResponse(error)
            emit(error)
            return@flow
        }

        LOGGER.info("Request written to session $sessionId")

        session.responses.collect {
            emit(it)
            if (it is ReplResponse.Result) {
                throw CancellationException("Found result")
            }
        }
    }.catch { e ->
        if (e !is CancellationException || e.message != "Found result") throw e
    }

    /**
     * Terminates all active worker processes.
     */
    override suspend fun closeAll() {
        sessions.keys().toList().forEach { terminateSession(it) }
        // Give the OS a brief moment to release any file handles on Windows
        delay(20)
        scope.cancel()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultReplManager::class.java)
    }
}

