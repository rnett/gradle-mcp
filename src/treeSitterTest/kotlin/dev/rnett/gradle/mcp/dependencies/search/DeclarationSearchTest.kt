package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.fixtures.dependencies.search.index
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class DeclarationSearchTest : KoinTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: Path

    private lateinit var koinApp: KoinApplication
    override fun getKoin(): Koin = koinApp.koin

    private lateinit var declarationSearch: DeclarationSearch

    @BeforeEach
    fun setup() {
        val env = GradleMcpEnvironment(tempDir.resolve("mcp"))
        koinApp = koinApplication {
            modules(module {
                single { env }
                single { HttpClient(io.ktor.client.engine.cio.CIO) }
                single { ParserDownloader(get(), TestFixturesBuildConfig.TREE_SITTER_LANGUAGE_PACK_VERSION) }
                single { TreeSitterLanguageProvider(get()) }
                single { TreeSitterDeclarationExtractor(get()) }
                single { DeclarationSearch(get()) }
            })
        }
        declarationSearch = getKoin().get()
    }

    @AfterEach
    fun tearDown() {
        getKoin().getOrNull<HttpClient>()?.close()
        koinApp.close()
    }

    @Test
    fun `test integration`() = runTest(timeout = 10.minutes) {
        val depDir = tempDir.resolve("dep").createDirectories()
        val kotlinFile = """
            package com.example
            
            class MyKotlinClass {
                val myVal = 1
                fun myFun() {}
            }
            
            interface MyInterface {
                fun myInterfaceFun()
            }
            
enum class MyEnum {
                A, B
            }
        """.trimIndent()
        depDir.resolve("MyKotlinClass.kt").writeText(kotlinFile)

        val javaFile = """
            package com.example;
            
            public class MyJavaClass {
                private int myInt = 1;
                public void myJavaMethod() {}
                public static String myStaticField = "test";
            }
            
enum MyJavaEnum {
                X, Y
            }
            
            @interface MyJavaAnnotation {
                String value();
            }
            
            @Deprecated
            record MyJavaRecord(int x) {}
        """.trimIndent()
        depDir.resolve("MyJavaClass.java").writeText(javaFile)

        val indexDir = tempDir.resolve("index")
        with(ProgressReporter.PRINTLN) {
            declarationSearch.index(depDir, indexDir)
        }

        suspend fun assertFound(query: String, expectedPath: String, expectedLine: Int) {
            val response = declarationSearch.search(listOf(indexDir), query)
            val results = response.results
            // println removed (Finding 48)
            assertTrue(
                results.any { it.relativePath == expectedPath && it.line == expectedLine },
                "Declaration $query not found at $expectedPath:$expectedLine. Found: $results"
            )
        }

        assertFound("MyKotlinClass", "MyKotlinClass.kt", 3)
        assertFound("myVal", "MyKotlinClass.kt", 4)
        assertFound("myFun", "MyKotlinClass.kt", 5)
        assertFound("MyInterface", "MyKotlinClass.kt", 8)
        assertFound("myInterfaceFun", "MyKotlinClass.kt", 9)
        assertFound("MyEnum", "MyKotlinClass.kt", 12)

        assertFound("MyJavaClass", "MyJavaClass.java", 3)
        assertFound("myInt", "MyJavaClass.java", 4)
        assertFound("myJavaMethod", "MyJavaClass.java", 5)
        assertFound("myStaticField", "MyJavaClass.java", 6)
        assertFound("MyJavaEnum", "MyJavaClass.java", 9)
        assertFound("MyJavaAnnotation", "MyJavaClass.java", 13)
        assertFound("MyJavaRecord", "MyJavaClass.java", 18)

        // Case-sensitive search
        assertTrue(declarationSearch.search(listOf(indexDir), "mykotlinclass").results.isEmpty(), "Should not find lowercase MyKotlinClass")
        assertFound("MyKotlinClass", "MyKotlinClass.kt", 3)

        // FQN search
        assertFound("com.example.MyKotlinClass", "MyKotlinClass.kt", 3)
        assertFound("com.example.MyKotlinClass.myVal", "MyKotlinClass.kt", 4)

        // Partial FQN without wildcards should NOT match anymore (Case 3 eliminated)
        assertTrue(declarationSearch.search(listOf(indexDir), "example.MyKotlinClass").results.isEmpty(), "Partial FQN without wildcard should not match")

        // Unqualified wildcard matching name
        assertFound("MyKotlin*", "MyKotlinClass.kt", 3)
        assertFound("*Class", "MyKotlinClass.kt", 3)

        // CamelCase token wildcard matching
        assertFound("Kotlin*", "MyKotlinClass.kt", 3)
        assertFound("*Method", "MyJavaClass.java", 5)

        // Partial FQN with wildcards
        assertFound("*.example.MyKotlinClass", "MyKotlinClass.kt", 3)
        assertFound("com.*.MyKotlinClass", "MyKotlinClass.kt", 3)

        // Glob wildcards
        assertFound("com.example.*.myVal", "MyKotlinClass.kt", 4)
        assertFound("com.*.myVal", "MyKotlinClass.kt", 4)
        assertFound("com.*.MyKotlinClass.myVal", "MyKotlinClass.kt", 4)
        assertFound("*.example.MyKotlinClass", "MyKotlinClass.kt", 3)
        assertFound("*.MyKotlinClass", "MyKotlinClass.kt", 3)
        assertFound("com.example.*", "MyKotlinClass.kt", 3)

        // Lucene syntax (field prefixes)
        assertFound("name:MyKotlinClass", "MyKotlinClass.kt", 3)
        assertFound("name:MyKotlin*", "MyKotlinClass.kt", 3)
        assertFound("name:Kotlin*", "MyKotlinClass.kt", 3)
        assertFound("fqn:com.example.MyKotlinClass", "MyKotlinClass.kt", 3)
        assertFound("fqn:com.*.MyKotlinClass", "MyKotlinClass.kt", 3)
        assertFound("fqn:*.MyKotlinClass", "MyKotlinClass.kt", 3)

        // Explicit Regex
        assertFound("name:/MyKotlin.*/", "MyKotlinClass.kt", 3)
        assertFound("fqn:/com\\.example\\.MyKotlin.*/", "MyKotlinClass.kt", 3)
        assertFound("fqn:/.*/", "MyKotlinClass.kt", 3) // match all FQNs

        // FQN search (MyJavaEnum is top-level in com.example)
        assertFound("com.example.MyJavaEnum", "MyJavaClass.java", 9)
        assertFound("*.MyJavaEnum", "MyJavaClass.java", 9)

        // Regex search (unqualified)
        assertFound("/.*MyKotlin.*/", "MyKotlinClass.kt", 3)
        assertFound("/.*myVal/", "MyKotlinClass.kt", 4)
        assertFound("/com\\.example\\.MyKotlin.*/", "MyKotlinClass.kt", 3)
        assertFound("/.*(MyKotlin|MyJava).*/", "MyKotlinClass.kt", 3)
        assertFound("/.*[Mm]yKotlin.*/", "MyKotlinClass.kt", 3)

        // Package exploration
        val packageContents = declarationSearch.listPackageContents(listOf(indexDir), "com.example")
        assertNotNull(packageContents)
        assertTrue(packageContents.symbols.contains("MyKotlinClass"), "Package should contain MyKotlinClass")
        assertTrue(packageContents.symbols.contains("MyJavaClass"), "Package should contain MyJavaClass")

        val rootPackageContents = declarationSearch.listPackageContents(listOf(indexDir), "")
        assertNotNull(rootPackageContents)
        assertTrue(rootPackageContents.subPackages.contains("com"), "Root should contain 'com' subpackage")

        val comPackageContents = declarationSearch.listPackageContents(listOf(indexDir), "com")
        assertNotNull(comPackageContents)
        assertTrue(comPackageContents.subPackages.contains("example"), "com should contain 'example' subpackage")
    }

    @Test
    fun `test Lucene query parsing for FQN`() = runTest(timeout = 10.minutes) {
        val analyzer = org.apache.lucene.analysis.standard.StandardAnalyzer()
        val parser = org.apache.lucene.queryparser.classic.MultiFieldQueryParser(arrayOf(DeclarationSearch.FQN), analyzer)

        val query = parser.parse("kotlinx.serialization.json.Json")
        val expected = "fqn:kotlinx.serialization.json.json"
        assertEquals(expected, query.toString(), "Parsed query should match expected Lucene structural format")
    }

    @Test
    fun `test deep package and interface extraction`() = runTest(timeout = 10.minutes) {
        val depDir = tempDir.resolve("dep-deep").createDirectories()
        val jsonFile = """
            @file:OptIn(ExperimentalSerializationApi::class)
            package kotlinx.serialization.json

            import kotlinx.serialization.ExperimentalSerializationApi

            /**
             * Json interface.
             */
            public interface Json {
                fun encode()
            }
        """.trimIndent()
        depDir.resolve("Json.kt").writeText(jsonFile)

        val indexDir = tempDir.resolve("index-deep")
        with(ProgressReporter.PRINTLN) {
            declarationSearch.index(depDir, indexDir)
        }

        val response = declarationSearch.search(listOf(indexDir), "fqn:kotlinx.serialization.json.Json")
        val results = response.results
        // println removed (Finding 48)
        assertTrue(results.any { it.relativePath == "Json.kt" && it.line == 9 }, "Json interface not found at Json.kt:9")
    }
}
