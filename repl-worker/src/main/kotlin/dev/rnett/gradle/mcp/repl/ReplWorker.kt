package dev.rnett.gradle.mcp.repl

import kotlinx.serialization.json.Json
import java.io.File
import java.io.PrintStream
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.previousSnippets
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

class ReplWorker(val config: ReplConfig) {
    private val json = Json { ignoreUnknownKeys = true }

    private val compilationConfiguration = ScriptCompilationConfiguration {
        jvm {
            // Add provided classpath
            updateClasspath(config.classpath.map { File(it) })

            // In a real implementation we might want to filter or prioritize this
            // dependenciesFromCurrentContext(wholeClasspath = true)
        }

        // Handle compiler plugins and args if needed
        // This often requires more advanced configuration in the host
        compilerOptions.append(config.compilerArgs)

        // Use K2 if possible (optional, depends on kotlin-scripting version)
        // compilerOptions.append("-Xuse-k2")
    }

    private var evaluationConfiguration = ScriptEvaluationConfiguration {
        jvm {
            // Ensure we can use types from the host if needed
            baseClassLoader(ReplWorker::class.java.classLoader)
        }
    }

    private val host = BasicJvmScriptingHost()

    private val stdout = System.`out`
    private val stderr = System.err

    fun run() {
        val reader = System.`in`.bufferedReader()

        // Redirect stdout/stderr to capture them during evaluation
        // and send them as JSON frames
        System.setOut(PrintStream(object : java.io.OutputStream() {
            override fun write(b: Int) {
                sendResponse(ReplResponse.Output("stdout", b.toChar().toString()))
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                sendResponse(ReplResponse.Output("stdout", String(b, off, len)))
            }
        }, true))

        System.setErr(PrintStream(object : java.io.OutputStream() {
            override fun write(b: Int) {
                sendResponse(ReplResponse.Output("stderr", b.toChar().toString()))
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                sendResponse(ReplResponse.Output("stderr", String(b, off, len)))
            }
        }, true))

        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue

            try {
                val request = json.decodeFromString<ReplRequest>(line)
                evaluate(request.code)
            } catch (e: Exception) {
                sendResponse(ReplResponse.Result(false, e.stackTraceToString()))
            }
        }
    }

    private fun evaluate(code: String) {
        val result = host.eval(code.toScriptSource(), compilationConfiguration, evaluationConfiguration)

        when (result) {
            is ResultWithDiagnostics.Success -> {
                val evalResult = result.value.returnValue

                // Update evaluation configuration with the new snapshot to preserve state
                evaluationConfiguration = ScriptEvaluationConfiguration(evaluationConfiguration) {
                    previousSnippets.append(result.value)
                }

                when (evalResult) {
                    is ResultValue.Value -> {
                        sendResponse(ReplResponse.Result(true, evalResult.value.toString(), renderKind = "text"))
                    }

                    is ResultValue.Unit -> {
                        sendResponse(ReplResponse.Result(true, "Unit", renderKind = "text"))
                    }

                    is ResultValue.Error -> {
                        sendResponse(ReplResponse.Result(false, evalResult.error.stackTraceToString()))
                    }

                    else -> {
                        sendResponse(ReplResponse.Result(true, "Success", renderKind = "text"))
                    }
                }
            }

            is ResultWithDiagnostics.Failure -> {
                val diagnostics = result.reports.joinToString("\n") { it.render() }
                sendResponse(ReplResponse.Result(false, diagnostics))
            }
        }
    }

    private fun sendResponse(response: ReplResponse) {
        val line = json.encodeToString(response)
        stdout.println(line)
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
