package dev.rnett.gradle.mcp.repl

import kotlinx.serialization.json.Json
import java.io.PrintStream
import java.util.Base64

class ReplWorker(val config: ReplConfig) {
    private val json = Json { ignoreUnknownKeys = true }

    private val responder = object : Responder {
        override fun respond(value: Any?, mime: String?) {
            val data = ResultRenderer.renderResult(value, mime)
            sendResponse(data)
        }

        override fun markdown(md: String) {
            sendResponse(ReplResponse.Data(md, "text/markdown"))
        }

        override fun html(fragment: String) {
            sendResponse(ReplResponse.Data(fragment, "text/html"))
        }

        override fun image(bytes: ByteArray, mime: String) {
            val base64 = Base64.getEncoder().encodeToString(bytes)
            sendResponse(ReplResponse.Data(base64, mime))
        }
    }

    private val evaluator: KotlinScriptEvaluator = KotlinScriptEvaluator(config, responder)

    private val stdout = System.`out`
    private val stderr = System.err

    fun run() {
        val reader = System.`in`.bufferedReader()

        // Redirect stdout/stderr to capture them during evaluation
        // and send them as JSON frames
        System.setOut(PrintStream(object : java.io.OutputStream() {
            override fun write(b: Int) {
                sendResponse(ReplResponse.Output.Stdout(b.toChar().toString()))
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                sendResponse(ReplResponse.Output.Stdout(String(b, off, len)))
            }
        }, true))

        System.setErr(PrintStream(object : java.io.OutputStream() {
            override fun write(b: Int) {
                sendResponse(ReplResponse.Output.Stderr(b.toChar().toString()))
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                sendResponse(ReplResponse.Output.Stderr(String(b, off, len)))
            }
        }, true))

        while (true) {
            val line = reader.readLine() ?: break
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

    private fun sendResponse(response: ReplResponse) {
        val line = json.encodeToString(response)
        stdout.println("${ReplResponse.RPC_PREFIX}$line")
        stdout.flush()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val json = Json { ignoreUnknownKeys = true }
            val configLine = System.`in`.bufferedReader().readLine() ?: return
            val config = json.decodeFromString<ReplConfig>(configLine)

            ReplWorker(config).run()
        }
    }
}
