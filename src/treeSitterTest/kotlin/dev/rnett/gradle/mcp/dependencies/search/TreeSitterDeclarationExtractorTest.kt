package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

class TreeSitterDeclarationExtractorTest : KoinTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: Path

    private lateinit var extractor: TreeSitterDeclarationExtractor

    private lateinit var koinApp: KoinApplication
    override fun getKoin(): Koin = koinApp.koin

    @BeforeEach
    fun setup() {
        val env = GradleMcpEnvironment(tempDir.resolve("mcp"))
        koinApp = koinApplication {
            modules(module {
                single { env }
                single { HttpClient(CIO) }
                single { ParserDownloader(get()) }
                single { TreeSitterLanguageProvider(get()) }
                single { TreeSitterDeclarationExtractor(get()) }
            })
        }
        extractor = getKoin().get()
    }

    @AfterEach
    fun tearDown() {
        getKoin().getOrNull<HttpClient>()?.close()
        koinApp.close()
    }

    @ParameterizedTest
    @MethodSource("packageTestCases")
    fun `test package extraction`(src: String, expected: String) = runTest(timeout = 10.minutes) {
        val fileName = "PkgTest_${src.hashCode().let { if (it < 0) -it else it }}.kt"
        val ktFile = tempDir.resolve(fileName)
        ktFile.writeText(src + "\nclass X")
        val symbols = extractor.extractSymbols(ktFile)
        val x = symbols.find { it.name == "X" }
        assertNotNull(x, "Should find class X in '$src'")
        assertEquals(expected, x.packageName, "Wrong package for '$src'")
    }

    @Test
    fun `test Java extraction`() = runTest(timeout = 10.minutes) {
        val src = """
            package com.example;
            public class MyJavaClass {
                public void myMethod() {}
            }
        """.trimIndent()
        val javaFile = tempDir.resolve("MyJavaClass.java")
        javaFile.writeText(src)

        val symbols = extractor.extractSymbols(javaFile)
        val clazz = symbols.find { it.name == "MyJavaClass" }
        assertNotNull(clazz)
        assertEquals("com.example.MyJavaClass", clazz.fqn)

        val method = symbols.find { it.name == "myMethod" }
        assertNotNull(method)
        assertEquals("com.example.MyJavaClass.myMethod", method.fqn)
    }

    companion object {
        @JvmStatic
        fun packageTestCases(): Stream<Arguments> = Stream.of(
            Arguments.of("package com.example", "com.example"),
            Arguments.of("package com.example.foo", "com.example.foo"),
            Arguments.of("package com.example.internal", "com.example.internal"),
            Arguments.of("package single", "single"),
            Arguments.of("/* comment */ package com.example // comment", "com.example"),
            Arguments.of("", "")
        )
    }

    @Test
    fun `test Kotlin file annotation before package extraction`() = runTest(timeout = 10.minutes) {
        val src = """
            @file:OptIn(ExperimentalSerializationApi::class)
            package kotlinx.serialization.json

            import kotlinx.serialization.ExperimentalSerializationApi

            public interface Json {
                fun encode()
            }
        """.trimIndent()

        val ktFile = tempDir.resolve("Json.kt")
        ktFile.writeText(src)

        val symbols = extractor.extractSymbols(ktFile)
        val json = symbols.find { it.name == "Json" }
        assertNotNull(json, "Should find Json interface in annotated file")
        assertEquals("kotlinx.serialization.json", json.packageName)
        assertEquals("kotlinx.serialization.json.Json", json.fqn)
    }

    @Test
    fun `test deep FQN calculation`() = runTest(timeout = 10.minutes) {
        val src = """
            package com.example
            
            class Outer {
                class Inner {
                    class Outer {
                        class Leaf
                    }
                    enum class Type {
                        VALUE_A,
                        VALUE_B
                    }
                    
                    fun myFunction() {}
                }
            }
        """.trimIndent()

        val ktFile = tempDir.resolve("DeepFqnTest.kt")
        ktFile.writeText(src)

        val symbols = extractor.extractSymbols(ktFile)

        val expected = listOf(
            "Outer" to "com.example.Outer",
            "Inner" to "com.example.Outer.Inner",
            "Outer" to "com.example.Outer.Inner.Outer",
            "Leaf" to "com.example.Outer.Inner.Outer.Leaf",
            "Type" to "com.example.Outer.Inner.Type",
            "VALUE_A" to "com.example.Outer.Inner.Type.VALUE_A",
            "VALUE_B" to "com.example.Outer.Inner.Type.VALUE_B",
            "myFunction" to "com.example.Outer.Inner.myFunction"
        )

        expected.forEach { (name, fqn) ->
            val symbol = symbols.find { it.fqn == fqn }
            assertNotNull(symbol, "Should find symbol with FQN '$fqn'")
            assertEquals(name, symbol.name, "Wrong name for symbol '$fqn'")
        }
    }
}
