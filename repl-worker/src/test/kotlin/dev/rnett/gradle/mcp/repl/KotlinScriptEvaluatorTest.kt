package dev.rnett.gradle.mcp.repl

import kotlin.test.Test

class KotlinScriptEvaluatorTest {

    private val stdlibPaths = System.getProperty("kotlin.stdlib.path")?.split(java.io.File.pathSeparator) ?: emptyList()

    private val mockResponder = object : Responder {
        override fun respond(value: Any?, mime: String?) {}
        override fun markdown(md: String) {}
        override fun html(fragment: String) {}
        override fun image(bytes: ByteArray, mime: String) {}
    }

    @Test
    fun `test simple evaluation`() {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)
        val result = evaluator.evaluate("1 + 1")
        if (result !is KotlinScriptEvaluator.EvalResult.Success) {
            val message = when (result) {
                is KotlinScriptEvaluator.EvalResult.CompilationError -> "Compilation error: ${result.message}"
                is KotlinScriptEvaluator.EvalResult.RuntimeError -> "Runtime error: ${result.message}\n${result.stackTrace}"
                else -> "Unknown error"
            }
            throw AssertionError(message)
        }
        assert(result.data.value == "2")
    }

    @Test
    fun `test sequential evaluation with state`() {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)

        val result1 = evaluator.evaluate("val x = 10")
        assert(result1 is KotlinScriptEvaluator.EvalResult.Success)

        val result2 = evaluator.evaluate("x + 5")
        assert(result2 is KotlinScriptEvaluator.EvalResult.Success)
        assert((result2 as KotlinScriptEvaluator.EvalResult.Success).data.value == "15")
    }

    @Test
    fun `test sequential evaluation with function`() {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)

        val result1 = evaluator.evaluate("fun greet(name: String) = \"Hello, \$name!\"")
        assert(result1 is KotlinScriptEvaluator.EvalResult.Success)

        val result2 = evaluator.evaluate("greet(\"World\")")
        assert(result2 is KotlinScriptEvaluator.EvalResult.Success)
        assert((result2 as KotlinScriptEvaluator.EvalResult.Success).data.value == "Hello, World!")
    }

    @Test
    fun `test compilation error`() {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)
        val result = evaluator.evaluate("invalid code")
        assert(result is KotlinScriptEvaluator.EvalResult.CompilationError)
        assert((result as KotlinScriptEvaluator.EvalResult.CompilationError).message == "Incomplete code: ERROR Expecting an element (repl-1.kts:1:13)\nERROR Incomplete code")
    }

    @Test
    fun `test sequential compilation error`() {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)
        evaluator.evaluate("val x = 1")
        val result = evaluator.evaluate("invalid code")
        assert(result is KotlinScriptEvaluator.EvalResult.CompilationError)
        assert((result as KotlinScriptEvaluator.EvalResult.CompilationError).message.contains("repl-2.kts"))
    }

    @Test
    fun `test runtime error`() {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)
        val result = evaluator.evaluate("throw RuntimeException(\"test error\")")
        assert(result is KotlinScriptEvaluator.EvalResult.RuntimeError)
        assert((result as KotlinScriptEvaluator.EvalResult.RuntimeError).message == "test error")
    }

    @Test
    fun `test responder access`() {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)
        val result = evaluator.evaluate("responder.markdown(\"test\")")
        assert(result is KotlinScriptEvaluator.EvalResult.Success)
    }

    @Test
    fun `test access to worker classes via host classloader`() {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)
        val result = evaluator.evaluate("dev.rnett.gradle.mcp.repl.ReplWorker::class.java.name")
        assert(result is KotlinScriptEvaluator.EvalResult.Success)
        assert((result as KotlinScriptEvaluator.EvalResult.Success).data.value == "dev.rnett.gradle.mcp.repl.ReplWorker")
    }

    @Test
    fun `test automatic stdlib inclusion`() {
        // Re-adding stdlib to verify it works when provided, as we removed automatic inclusion from evaluator
        val config = ReplConfig(classpath = stdlibPaths)
        val evaluator = KotlinScriptEvaluator(config, mockResponder)
        val result = evaluator.evaluate("1 + 1")
        assert(result is KotlinScriptEvaluator.EvalResult.Success)
        assert((result as KotlinScriptEvaluator.EvalResult.Success).data.value == "2")
    }

    @Test
    fun `test loading from classpath`() {
        // Use junit jar as a sample classpath entry
        val junitJar = org.junit.Test::class.java.protectionDomain.codeSource.location.toURI().let { java.io.File(it).absolutePath }
        val config = ReplConfig(classpath = stdlibPaths + junitJar)
        val evaluator = KotlinScriptEvaluator(config, mockResponder)
        val result = evaluator.evaluate("org.junit.Test::class.java.name")
        assert(result is KotlinScriptEvaluator.EvalResult.Success)
        val success = result as KotlinScriptEvaluator.EvalResult.Success
        assert(success.data.value == "org.junit.Test")
    }


    @Test
    fun `test with older kotlin version`() {
        val kotlin2Paths = System.getProperty("kotlin.stdlib.kotlin2.path")?.split(java.io.File.pathSeparator) ?: emptyList()
        val config = ReplConfig(classpath = kotlin2Paths)
        val evaluator = KotlinScriptEvaluator(config, mockResponder)
        val result = evaluator.evaluate("KotlinVersion.CURRENT.toString()")
        assert(result is KotlinScriptEvaluator.EvalResult.Success)
        val success = result as KotlinScriptEvaluator.EvalResult.Success
        // With the new classloading, we should be able to load classes from the provided classpath first.
        // The host may have a different version (e.g. 2.3.10), but if we provide 2.0.21 in the script classpath,
        // it should be preferred if the classloader is working correctly.
        // NOTE: KotlinVersion is often inlined or behaves specially, but for a general class it should work.
        assert(success.data.value == "2.0.21")
    }

    @Test
    fun `test custom mime type`() {
        val evaluator = KotlinScriptEvaluator(ReplConfig(classpath = stdlibPaths), mockResponder)
        val result = evaluator.evaluate("responder.respond(\"test\", \"text/custom\")")
        assert(result is KotlinScriptEvaluator.EvalResult.Success)
        // Since respond calls evaluator.renderResult, and the script's call to responder.respond 
        // will go to the mockResponder which does nothing, this test doesn't check the data.
        // But we can check ResultRenderer.renderResult directly.
        val data = ResultRenderer.renderResult("test", "text/custom")
        assert(data.mime == "text/custom")
        assert(data.value == "test")
    }

    @Test
    fun `test image rendering`() {
        val image = java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val data = ResultRenderer.renderResult(image)
        assert(data.mime == "image/png")
        assert(data.value.isNotEmpty())
    }
}
