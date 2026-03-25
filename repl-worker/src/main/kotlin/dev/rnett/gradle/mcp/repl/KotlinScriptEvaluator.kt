package dev.rnett.gradle.mcp.repl

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.K2ReplCompiler
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.K2ReplEvaluator
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptDiagnosticsMessageCollector
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.Enumeration
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.script.experimental.api.CompiledSnippet
import kotlin.script.experimental.api.ReplCompiler
import kotlin.script.experimental.api.ReplEvaluator
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies
import kotlin.script.experimental.jvm.updateClasspath

class KotlinScriptEvaluator(val config: ReplConfig, val responseSender: (ReplResponse) -> Unit) {
    companion object {
        private val excludedPluginArtifacts = setOf("kotlin-scripting-compiler", "kotlin-scripting-compiler-impl", "kotlin-compiler")
    }

    val hostClassLoader = this@KotlinScriptEvaluator::class.java.classLoader

    val executionClassLoader = HostLastClassLoader(
        config.classpath.map { File(it).toURI().toURL() },
        hostClassLoader
    )

    val responderClass = executionClassLoader.loadClass(Responder::class.qualifiedName!!)
    val resultRenderer = executionClassLoader.loadClass(ResultRenderer::class.qualifiedName!!).constructors.single().newInstance(executionClassLoader) as ResultRenderer
    val responder = responderClass.constructors.single().newInstance(responseSender, executionClassLoader)

    init {
        // Workaround for lack of providedProperties in K2 REPL
        // Define a global 'responder' variable that can be accessed from scripts
        val globalClass = executionClassLoader.loadClass("dev.rnett.gradle.mcp.repl.GlobalResponder")
        val method = globalClass.getDeclaredMethod("setInstance", Any::class.java)
        method.invoke(null, responder)
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

        defaultImports("dev.rnett.gradle.mcp.repl.GlobalResponder.responder")
        implicitReceivers(responderClass.kotlin)
    }

    private val evaluationConfiguration = ScriptEvaluationConfiguration {
        jvm {
            baseClassLoader(executionClassLoader)
            loadDependencies(true)
        }
        implicitReceivers(responder)
    }

    private val rootDisposable = Disposer.newDisposable("KotlinScriptEvaluator")
    private val messageCollector = ScriptDiagnosticsMessageCollector(MessageCollector.NONE)
    private val compiler: ReplCompiler<CompiledSnippet> = K2ReplCompiler(
        K2ReplCompiler.createCompilationState(
            messageCollector,
            rootDisposable,
            compilationConfiguration
        )
    )
    private val evaluator: ReplEvaluator<CompiledSnippet, *> = K2ReplEvaluator()

    @OptIn(ExperimentalAtomicApi::class)
    private val lastSnippetId = AtomicInt(0)

    @OptIn(ExperimentalAtomicApi::class)
    suspend fun evaluate(code: String): EvalResult {
        val snippetId = lastSnippetId.addAndFetch(1)

        val snippet = object : SourceCode {
            override val text: String = code
            override val name: String = "_$snippetId"
            override val locationId: String? = null
        }

        val compileResult = compiler.compile(snippet, compilationConfiguration)

        return when (compileResult) {
            is ResultWithDiagnostics.Success -> {
                val compiledSnippet = compileResult.value
                when (val evalResult = evaluator.eval(compiledSnippet, evaluationConfiguration)) {
                    is ResultWithDiagnostics.Success -> {
                        val evaluatedSnippet = evalResult.value.get()
                        when (val resultValue = evaluatedSnippet.result) {
                            is ResultValue.Value -> {
                                EvalResult.Success(resultRenderer.renderResult(resultValue.value))
                            }

                            is ResultValue.Unit -> {
                                EvalResult.Success(resultRenderer.renderResult(Unit))
                            }

                            is ResultValue.Error -> {
                                val originalCause = resultValue.error
                                val cleanedStackTrace = cleanStackTrace(originalCause)
                                EvalResult.RuntimeError(originalCause.message ?: originalCause.toString(), cleanedStackTrace)
                            }

                            is ResultValue.NotEvaluated -> {
                                EvalResult.InternalError("Not evaluated", null)
                            }
                        }
                    }

                    is ResultWithDiagnostics.Failure -> {
                        val message = evalResult.reports.joinToString("\n") {
                            it.message + (it.exception?.let { "\nCaused by: $it" } ?: "")
                        }
                        val location = evalResult.reports.firstOrNull { it.location != null }?.location?.let {
                            CompilerMessageLocation.create(snippet.name, it.start.line, it.start.col, null)
                        }
                        EvalResult.CompilationError("Eval error: $message", location)
                    }
                }
            }

            is ResultWithDiagnostics.Failure -> {
                val message = compileResult.reports.joinToString("\n") { it.message }
                val location = compileResult.reports.firstOrNull { it.location != null }?.location?.let {
                    CompilerMessageLocation.create(snippet.name, it.start.line, it.start.col, null)
                }
                EvalResult.CompilationError(message, location)
            }
        }
    }

    private fun cleanStackTrace(throwable: Throwable): String {
        val stackTrace = throwable.stackTrace

        // In K2, script frames have names like _$ID.$$eval
        val lastScriptIndex = stackTrace.indexOfLast { it.methodName == "\$\$eval" }
        val relevantFrames = if (lastScriptIndex != -1) {
            stackTrace.take(lastScriptIndex + 1)
        } else {
            stackTrace.toList()
        }

        val sb = StringBuilder()
        sb.append(throwable.toString()).append("\n")
        for (element in relevantFrames) {
            sb.append("\tat ").append(element).append("\n")
        }

        val cause = throwable.cause
        if (cause != null) {
            sb.append("Caused by: ").append(cleanStackTrace(cause))
        }

        return sb.toString().trim()
    }

    fun close() {
        Disposer.dispose(rootDisposable)
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

    private val hostFirstPackages = listOf(
        "dev.rnett.gradle.mcp.repl."
    )

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (hostFirstPackages.any { name.startsWith(it) }) {
            return hostClassLoader.loadClass(name)
        }
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
