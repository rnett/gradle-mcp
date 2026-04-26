package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.PRINTLN
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.fixtures.dependencies.search.index
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeclarationSearchTest {

    @TempDir
    lateinit var tempDir: Path


    @Test
    fun `test integration`() = runTest {
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
            DeclarationSearch.index(depDir, indexDir)
        }

        suspend fun assertFound(query: String, expectedPath: String, expectedLine: Int) {
            val response = DeclarationSearch.search(listOf(indexDir), query)
            val results = response.results
            println("Searched for: $query. Interpreted: ${response.interpretedQuery}. Error: ${response.error}. Results: $results")
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
        assertTrue(DeclarationSearch.search(listOf(indexDir), "mykotlinclass").results.isEmpty(), "Should not find lowercase MyKotlinClass")
        assertFound("MyKotlinClass", "MyKotlinClass.kt", 3)

        // FQN search
        assertFound("com.example.MyKotlinClass", "MyKotlinClass.kt", 3)
        assertFound("com.example.MyKotlinClass.myVal", "MyKotlinClass.kt", 4)

        // Partial FQN without wildcards should NOT match anymore (Case 3 eliminated)
        assertTrue(DeclarationSearch.search(listOf(indexDir), "example.MyKotlinClass").results.isEmpty(), "Partial FQN without wildcard should not match")

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
        val packageContents = DeclarationSearch.listPackageContents(listOf(indexDir), "com.example")
        assertNotNull(packageContents)
        assertTrue(packageContents.symbols.contains("MyKotlinClass"), "Package should contain MyKotlinClass")
        assertTrue(packageContents.symbols.contains("MyJavaClass"), "Package should contain MyJavaClass")

        val rootPackageContents = DeclarationSearch.listPackageContents(listOf(indexDir), "")
        assertNotNull(rootPackageContents)
        assertTrue(rootPackageContents.subPackages.contains("com"), "Root should contain 'com' subpackage")

        val comPackageContents = DeclarationSearch.listPackageContents(listOf(indexDir), "com")
        assertNotNull(comPackageContents)
        assertTrue(comPackageContents.subPackages.contains("example"), "com should contain 'example' subpackage")
    }

    @Test
    fun `test Lucene query parsing for FQN`() = runTest {
        val analyzer = org.apache.lucene.analysis.standard.StandardAnalyzer()
        val parser = org.apache.lucene.queryparser.classic.MultiFieldQueryParser(arrayOf(DeclarationSearch.Fields.FQN), analyzer)

        val query = parser.parse("kotlinx.serialization.json.Json")
        println("Parsed query: $query")
    }

    @Test
    fun `test deep package and interface extraction`() = runTest {
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
            DeclarationSearch.index(depDir, indexDir)
        }

        val response = DeclarationSearch.search(listOf(indexDir), "fqn:kotlinx.serialization.json.Json")
        val results = response.results
        println("Results for Json: $results")
        assertTrue(results.any { it.relativePath == "Json.kt" && it.line == 9 }, "Json interface not found at Json.kt:9")
    }

}
