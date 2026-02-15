package dev.rnett.gradle.mcp.repl

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult
import org.jetbrains.kotlin.cli.common.repl.ReplEvalResult
import org.slf4j.LoggerFactory
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

class KotlinScriptEvaluator(val config: ReplConfig, val responseSender: (ReplResponse) -> Unit) {
    companion object {
        private val LOGGER by lazy { LoggerFactory.getLogger(KotlinScriptEvaluator::class.java) }
        private val excludedPluginArtifacts = setOf("kotlin-scripting-compiler", "kotlin-scripting-compiler-impl", "kotlin-compiler")
    }

    private val compilationConfiguration = ScriptCompilationConfiguration {
        jvm {
            updateClasspath(config.classpath.map { File(it).absoluteFile })
            // not sure this will always work, may need to pass the jar location as an arg
            val responderLocation = Responder::class.java.protectionDomain.codeSource.location.toURI().let { File(it) }
            updateClasspath(listOf(responderLocation))
        }
        compilerOptions.append(
            config.pluginsClasspath.distinct()
                .map { File(it).absoluteFile }
                .filterNot {
                    val artifactName = it.nameWithoutExtension.substringBeforeLast('-')
                    artifactName in excludedPluginArtifacts || artifactName.removeSuffix("-embeddable") in excludedPluginArtifacts
                }
                .map { "-Xplugin=${it.absolutePath}" })
        compilerOptions.append(config.compilerPluginOptions.distinct().map { "plugin:${it.pluginId}:${it.optionName}=${it.value}" }.flatMap { listOf("-P", it) })
        compilerOptions.append(config.compilerArgs)
        LOGGER.error("Compiler options: ${this[compilerOptions]}")
        providedProperties("responder" to Responder::class)
    }

    val hostClassLoader = this@KotlinScriptEvaluator::class.java.classLoader

    val executionClassLoader = HostLastClassLoader(
        config.classpath.map { File(it).toURI().toURL() },
        hostClassLoader
    )

    val resultRenderer = executionClassLoader.loadClass(ResultRenderer::class.qualifiedName!!).constructors.single().newInstance(executionClassLoader) as ResultRenderer
    val responder = executionClassLoader.loadClass(Responder::class.qualifiedName!!).constructors.single().newInstance(responseSender, executionClassLoader) as Responder

    private val evaluationConfiguration = ScriptEvaluationConfiguration {
        // Use a custom classloader that prefers the script's classpath but falls back to the host classloader.
        // This allows the script to use its own version of libraries if provided, but still access
        // the host classes (like Responder) if not found in the script classpath.


        jvm {
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
                when (val evalResult = evaluator.eval(evaluatorState, compileResult)) {
                    is ReplEvalResult.ValueResult -> {
                        EvalResult.Success(resultRenderer.renderResult(evalResult.value))
                    }

                    is ReplEvalResult.UnitResult -> {
                        EvalResult.Success(resultRenderer.renderResult(Unit))
                    }

                    is ReplEvalResult.Error.Runtime -> {
                        EvalResult.RuntimeError(evalResult.message, evalResult.cause?.stackTraceToString())
                    }

                    is ReplEvalResult.Error.CompileTime -> {
                        EvalResult.CompilationError("Eval compile-time error: ${evalResult.message}", evalResult.location)
                    }

                    is ReplEvalResult.HistoryMismatch -> {
                        EvalResult.InternalError("History mismatch", null)
                    }

                    is ReplEvalResult.Incomplete -> {
                        EvalResult.CompilationError("Incomplete code (eval stage)", null)
                    }
                }
            }

            is ReplCompileResult.Error -> {
                val message = compileResult.message.replace(Regex("Line_\\d+\\.kts"), "repl-$snippetId.kts")
                EvalResult.CompilationError(message, compileResult.location)
            }

            is ReplCompileResult.Incomplete -> {
                val message = compileResult.message.replace(Regex("Line_\\d+\\.kts"), "repl-$snippetId.kts")
                EvalResult.CompilationError("Incomplete code: $message", null)
            }
        }
    }

    sealed class EvalResult {
        data class Success(val data: ReplResponse.Data) : EvalResult()
        data class CompilationError(val message: String, val location: CompilerMessageLocation?) : EvalResult()
        data class RuntimeError(val message: String, val stackTrace: String?) : EvalResult()
        data class InternalError(val message: String, val stackTrace: String?) : EvalResult()
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
