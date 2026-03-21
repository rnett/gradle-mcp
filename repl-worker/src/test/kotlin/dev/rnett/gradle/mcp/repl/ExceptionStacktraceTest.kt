package dev.rnett.gradle.mcp.repl

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExceptionStacktraceTest {

    private val stdlibPaths = System.getProperty("kotlin.stdlib.path")?.split(java.io.File.pathSeparator) ?: emptyList()
    private val mockResponder = { _: ReplResponse -> }

    private fun String.normalize() = replace("\r\n", "\n").replace("\t", "    ").replace("        ", "    ").trim()

    @Test
    fun `test runtime error stacktrace does not contain evaluator classes`() = runTest {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)
        val result = evaluator.evaluate("throw java.lang.RuntimeException(\"test error\")")

        assertTrue(result is KotlinScriptEvaluator.EvalResult.RuntimeError, "Result should be RuntimeError")
        val stackTrace = result.stackTrace!!

        assertEquals(
            """
            java.lang.RuntimeException: test error
                at _1.${'$'}${'$'}eval(_1.kts:1)
            """.trimIndent().normalize(),
            stackTrace.normalize()
        )
    }

    @Test
    fun `test complex stacktrace with multiple causes is cleaned`() = runTest {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)
        val result = evaluator.evaluate(
            """
            fun a() { b() }
            fun b() { throw java.lang.RuntimeException("inner", java.lang.RuntimeException("cause")) }
            a()
            """.trimIndent()
        )

        assertTrue(result is KotlinScriptEvaluator.EvalResult.RuntimeError)
        val stackTrace = result.stackTrace!!

        assertEquals(
            """
            java.lang.RuntimeException: inner
                at _1.b(_1.kts:2)
                at _1.a(_1.kts:1)
                at _1.${'$'}${'$'}eval(_1.kts:3)
            Caused by: java.lang.RuntimeException: cause
                at _1.b(_1.kts:2)
                at _1.a(_1.kts:1)
                at _1.${'$'}${'$'}eval(_1.kts:3)
            """.trimIndent().normalize(),
            stackTrace.normalize()
        )
    }

    @Test
    fun `test internal error stacktrace DOES contain evaluator classes`() {
        try {
            throw RuntimeException("internal error")
        } catch (e: Exception) {
            val stackTrace = e.stackTraceToString()
            assertTrue(stackTrace.contains("dev.rnett.gradle.mcp.repl.ExceptionStacktraceTest"), "Internal stacktrace should contain our own classes")
        }
    }

    @Test
    fun `test stacktrace with reflection in script is NOT over-cleaned`() = runTest {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)
        val result = evaluator.evaluate(
            """
            import java.lang.reflect.Method
            fun scriptFunc() {
                val method = Any::class.java.getMethod("toString")
                method.invoke(null) // This will throw NullPointerException from reflection
            }
            scriptFunc()
            """.trimIndent()
        )

        assertTrue(result is KotlinScriptEvaluator.EvalResult.RuntimeError)
        val stackTrace = result.stackTrace!!

        // Note: reflection frames are NOT cleaned because they are BEFORE the last script frame ($$eval)
        // Wait, if it's NPE from invoke(null) on instance method, it happens inside Method.invoke
        // which is after $$eval? No, Method.invoke is called BY $$eval.
        // But the exception is thrown FROM Method.invoke.
        // So the stack trace will be:
        // java.lang.NullPointerException
        //   at java.lang.reflect.Method.invoke
        //   ...
        //   at _1.scriptFunc(_1.kts:4)
        //   at _1.$$eval(_1.kts:6)

        assertTrue(stackTrace.contains("java.lang.reflect.Method.invoke"))
        assertTrue(stackTrace.contains("_1.scriptFunc(_1.kts:4)"))
        assertTrue(stackTrace.contains("_1.\$\$eval(_1.kts:6)"))
    }
}
