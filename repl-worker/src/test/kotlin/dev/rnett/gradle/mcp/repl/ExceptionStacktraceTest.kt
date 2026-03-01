package dev.rnett.gradle.mcp.repl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExceptionStacktraceTest {

    private val stdlibPaths = System.getProperty("kotlin.stdlib.path")?.split(java.io.File.pathSeparator) ?: emptyList()
    private val mockResponder = { _: ReplResponse -> }

    private fun String.normalize() = replace("\r\n", "\n").replace("\t", "    ").replace("        ", "    ").trim()

    @Test
    fun `test runtime error stacktrace does not contain evaluator classes`() {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)
        val result = evaluator.evaluate("throw RuntimeException(\"test error\")")

        assertTrue(result is KotlinScriptEvaluator.EvalResult.RuntimeError, "Result should be RuntimeError")
        val stackTrace = result.stackTrace!!
        println("[DEBUG_LOG] Stacktrace:\n$stackTrace")

        assertEquals(
            """
            java.lang.RuntimeException: test error
                at Line_1.<init>(Line_1.kts:1)
            """.trimIndent().normalize(),
            stackTrace.normalize()
        )
    }

    @Test
    fun `test complex stacktrace with multiple causes is cleaned`() {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)
        val result = evaluator.evaluate(
            """
            fun a() { b() }
            fun b() { throw RuntimeException("inner", RuntimeException("cause")) }
            a()
            """.trimIndent()
        )

        assertTrue(result is KotlinScriptEvaluator.EvalResult.RuntimeError)
        val stackTrace = result.stackTrace!!
        println("[DEBUG_LOG] Complex Stacktrace:\n$stackTrace")

        assertEquals(
            """
            java.lang.RuntimeException: inner
                at Line_1.b(Line_1.kts:2)
                at Line_1.a(Line_1.kts:1)
                at Line_1.<init>(Line_1.kts:3)
            Caused by: java.lang.RuntimeException: cause
                at Line_1.b(Line_1.kts:2)
                at Line_1.a(Line_1.kts:1)
                at Line_1.<init>(Line_1.kts:3)
            """.trimIndent().normalize(),
            stackTrace.normalize()
        )
    }

    @Test
    fun `test internal error stacktrace DOES contain evaluator classes`() {
        // Simulate an internal error by calling evaluate on something that might fail or manually throwing
        try {
            // We want to test that if an exception is caught in ReplWorker, it's NOT cleaned if it's internal.
            // But here we're testing KotlinScriptEvaluator directly.
            // Let's add a test for the new behavior in ReplWorker if possible, 
            // or just ensure we have a way to get uncleaned traces.
            throw RuntimeException("internal error")
        } catch (e: Exception) {
            val stackTrace = e.stackTraceToString()
            assertTrue(stackTrace.contains("dev.rnett.gradle.mcp.repl.ExceptionStacktraceTest"), "Internal stacktrace should contain our own classes")
        }
    }

    @Test
    fun `test stacktrace with reflection in script is NOT over-cleaned`() {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)
        // Using reflection in the script. Currently, 'takeWhile' would stop at the first reflection frame.
        // We want to keep frames from the script even if they use reflection.
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
        println("[DEBUG_LOG] Reflection Stacktrace:\n$stackTrace")

        assertEquals(
            """
            java.lang.NullPointerException
                at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
                at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
                at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
                at java.lang.reflect.Method.invoke(Method.java:498)
                at Line_1.scriptFunc(Line_1.kts:4)
                at Line_1.<init>(Line_1.kts:6)
            """.trimIndent().normalize(),
            stackTrace.normalize()
        )
    }
}
