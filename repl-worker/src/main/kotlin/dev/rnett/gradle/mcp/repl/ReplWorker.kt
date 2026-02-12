package dev.rnett.gradle.mcp.repl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

            try {
                val jsonLine = line.removePrefix(ReplResponse.RPC_PREFIX)
                val request = json.decodeFromString<ReplRequest>(jsonLine)
                val result = evaluator.evaluate(request.code)
                handleResult(result)
            } catch (e: Exception) {
                sendResponse(ReplResponse.Result.RuntimeError(e.message ?: e.toString(), e.stackTraceToString()))
            }
        }
        timeoutJob.cancel()
    }

    private fun handleResult(result: KotlinScriptEvaluator.EvalResult) {
        when (result) {
            is KotlinScriptEvaluator.EvalResult.Success -> {
                sendResponse(ReplResponse.Result.Success(result.data))
            }

            is KotlinScriptEvaluator.EvalResult.CompilationError -> {
                sendResponse(ReplResponse.Result.CompilationError(result.message))
            }

            is KotlinScriptEvaluator.EvalResult.RuntimeError -> {
                sendResponse(ReplResponse.Result.RuntimeError(result.message, result.stackTrace))
            }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val stdout = System.`out`

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
