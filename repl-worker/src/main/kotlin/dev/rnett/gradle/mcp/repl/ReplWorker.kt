package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.repl.ReplResponse.Result.CompilationError
import dev.rnett.gradle.mcp.repl.ReplResponse.Result.InternalError
import dev.rnett.gradle.mcp.repl.ReplResponse.Result.RuntimeError
import dev.rnett.gradle.mcp.repl.ReplResponse.Result.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.PrintStream
import java.util.Scanner
import kotlin.time.Duration.Companion.minutes

class ReplWorker(val config: ReplConfig, val scanner: Scanner) {

    private val evaluator: KotlinScriptEvaluator = KotlinScriptEvaluator(config, ::sendResponse)

    fun run() = runBlocking {
        // Redirect stdout/stderr to capture them during evaluation
        // and send them as JSON frames
        System.setOut(PrintStream(ReplOutputStream {
            sendResponse(ReplResponse.Output.Stdout(it))
        }, true))

        System.setErr(PrintStream(ReplOutputStream {
            sendResponse(ReplResponse.Output.Stderr(it))
        }, true))

        var lastActivity = System.currentTimeMillis()

        val timeoutJob = launch {
            while (isActive) {
                delay(1.minutes)
                if (System.currentTimeMillis() - lastActivity > 15.minutes.inWholeMilliseconds) {
                    System.err.println("REPL worker timed out after 15 minutes of inactivity")
                    System.exit(0)
                }
            }
        }

        while (isActive) {
            val line = withContext(Dispatchers.IO) {
                if (scanner.hasNextLine()) scanner.nextLine() else null
            } ?: break

            lastActivity = System.currentTimeMillis()
            if (line.isBlank()) continue

            if (!line.startsWith(ReplResponse.RPC_PREFIX)) {
                continue
            }

            LOGGER.info("Repl request received $line")

            try {
                val jsonLine = line.removePrefix(ReplResponse.RPC_PREFIX)
                val request = json.decodeFromString<ReplRequest>(jsonLine)
                val result = evaluator.evaluate(request.code)
                handleResult(result)
            } catch (e: Exception) {
                LOGGER.error("Repl code execution failed with exception", e)
                sendResponse(ReplResponse.Result.InternalError(e.message ?: e.toString(), e.stackTraceToString()))
            }
        }
        timeoutJob.cancel()
    }

    private fun handleResult(result: KotlinScriptEvaluator.EvalResult) {
        LOGGER.info("Repl request finished with result {}", result)
        when (result) {
            is KotlinScriptEvaluator.EvalResult.Success -> {
                sendResponse(Success(result.data))
            }

            is KotlinScriptEvaluator.EvalResult.CompilationError -> {
                sendResponse(CompilationError(result.message, result.location?.toString()))
            }

            is KotlinScriptEvaluator.EvalResult.RuntimeError -> {
                sendResponse(RuntimeError(result.message, result.stackTrace))
            }

            is KotlinScriptEvaluator.EvalResult.InternalError -> {
                sendResponse(InternalError(result.message, result.stackTrace))
            }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val stdout = System.`out`
        private val LOGGER by lazy { LoggerFactory.getLogger(ReplWorker::class.java) }

        fun sendResponse(response: ReplResponse) {
            val line = json.encodeToString(response)
            stdout.println("${ReplResponse.RPC_PREFIX}$line")
            stdout.flush()
        }

        @JvmStatic
        fun main(args: Array<String>) {
            Scanner(System.`in`).use { scanner ->
                if (!scanner.hasNextLine()) return
                val configLine = scanner.nextLine() ?: return
                val config = json.decodeFromString<ReplConfig>(configLine)
                ReplWorker(config, scanner).run()
            }
        }
    }
}
