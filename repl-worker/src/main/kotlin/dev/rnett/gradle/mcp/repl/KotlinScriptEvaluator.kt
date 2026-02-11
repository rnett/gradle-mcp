package dev.rnett.gradle.mcp.repl

import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult
import org.jetbrains.kotlin.cli.common.repl.ReplEvalResult
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.Enumeration
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator

class KotlinScriptEvaluator(val config: ReplConfig, val responder: Responder) {

    private val compilationConfiguration = ScriptCompilationConfiguration {
        jvm {
            updateClasspath(config.classpath.map { File(it) })
            // Add only the Responder interface's location to the script's compilation classpath
            val responderLocation = Responder::class.java.protectionDomain.codeSource.location.toURI().let { File(it) }
            updateClasspath(listOf(responderLocation))
        }
        compilerOptions.append(config.compilerArgs)
        providedProperties("responder" to Responder::class)
    }

    private val evaluationConfiguration = ScriptEvaluationConfiguration {
        jvm {
            // Use a custom classloader that prefers the script's classpath but falls back to the host classloader.
            // This allows the script to use its own version of libraries if provided, but still access
            // the host classes (like Responder) if not found in the script classpath.

            val hostClassLoader = this@KotlinScriptEvaluator::class.java.classLoader

            val executionClassLoader = HostLastClassLoader(
                config.classpath.map { File(it).toURI().toURL() },
                hostClassLoader
            )

            baseClassLoader(executionClassLoader)
            loadDependencies(false)
        }
        providedProperties("responder" to responder)
    }

    private val compiler = JvmReplCompiler(compilationConfiguration)
    private val evaluator = JvmReplEvaluator(evaluationConfiguration)

    private val compilerState = compiler.createState()
    private val evaluatorState = evaluator.createState()

    private var lastSnippetId = 0

    fun evaluate(code: String): EvalResult {
        val snippetId = ++lastSnippetId
        val codeLine = ReplCodeLine(snippetId, 0, code)
        val compileResult = compiler.compile(compilerState, codeLine)

        return when (compileResult) {
            is ReplCompileResult.CompiledClasses -> {
                val evalResult = evaluator.eval(evaluatorState, compileResult)
                when (evalResult) {
                    is ReplEvalResult.ValueResult -> {
                        EvalResult.Success(ResultRenderer.renderResult(evalResult.value))
                    }

                    is ReplEvalResult.UnitResult -> {
                        EvalResult.Success(ResultRenderer.renderResult(Unit))
                    }

                    is ReplEvalResult.Error.Runtime -> {
                        EvalResult.RuntimeError(evalResult.message, evalResult.cause?.stackTraceToString())
                    }

                    is ReplEvalResult.Error.CompileTime -> {
                        EvalResult.RuntimeError("Eval compile-time error: ${evalResult.message}", null)
                    }

                    is ReplEvalResult.HistoryMismatch -> {
                        EvalResult.RuntimeError("History mismatch", null)
                    }

                    is ReplEvalResult.Incomplete -> {
                        EvalResult.CompilationError("Incomplete code (eval stage)")
                    }
                }
            }

            is ReplCompileResult.Error -> {
                val message = compileResult.message.replace(Regex("Line_\\d+\\.kts"), "repl-$snippetId.kts")
                EvalResult.CompilationError(message)
            }

            is ReplCompileResult.Incomplete -> {
                val message = compileResult.message.replace(Regex("Line_\\d+\\.kts"), "repl-$snippetId.kts")
                EvalResult.CompilationError("Incomplete code: $message")
            }
        }
    }

    sealed class EvalResult {
        data class Success(val data: ReplResponse.Data) : EvalResult()
        data class CompilationError(val message: String) : EvalResult()
        data class RuntimeError(val message: String, val stackTrace: String?) : EvalResult()
    }
}

class HostLastClassLoader(classpath: List<URL>, private val hostClassLoader: ClassLoader) :
    URLClassLoader(classpath.toTypedArray(), null) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        return try {
            super.loadClass(name, resolve)
        } catch (e: ClassNotFoundException) {
            hostClassLoader.loadClass(name)
        }
    }

    override fun getResource(name: String): URL? {
        return super.getResource(name) ?: hostClassLoader.getResource(name)
    }

    override fun getResources(name: String): Enumeration<URL> {
        val urls = mutableListOf<URL>()
        urls.addAll(super.getResources(name).asSequence())
        urls.addAll(hostClassLoader.getResources(name).asSequence())
        return java.util.Collections.enumeration(urls.distinct())
    }
}
