/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.rnett.gradle.mcp.dependencies.search

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
            
            record MyJavaRecord(int x) {}
        """.trimIndent()
        depDir.resolve("MyJavaClass.java").writeText(javaFile)

        val indexDir = tempDir.resolve("index")
        with(dev.rnett.gradle.mcp.ProgressReporter.NONE) {
            DeclarationSearch.index(depDir, indexDir)
        }

        suspend fun assertFound(query: String, expectedPath: String, expectedLine: Int) {
            val response = DeclarationSearch.search(indexDir, query)
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
        assertFound("MyJavaRecord", "MyJavaClass.java", 17)

        // Case-sensitive search
        assertTrue(DeclarationSearch.search(indexDir, "mykotlinclass").results.isEmpty(), "Should not find lowercase MyKotlinClass")
        assertFound("MyKotlinClass", "MyKotlinClass.kt", 3)

        // FQN search
        assertFound("com.example.MyKotlinClass", "MyKotlinClass.kt", 3)
        assertFound("com.example.MyKotlinClass.myVal", "MyKotlinClass.kt", 4)

        // Partial FQN without wildcards should NOT match anymore (Case 3 eliminated)
        assertTrue(DeclarationSearch.search(indexDir, "example.MyKotlinClass").results.isEmpty(), "Partial FQN without wildcard should not match")

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
        val packageContents = DeclarationSearch.listPackageContents(indexDir, "com.example")
        assertNotNull(packageContents)
        assertTrue(packageContents.symbols.contains("MyKotlinClass"), "Package should contain MyKotlinClass")
        assertTrue(packageContents.symbols.contains("MyJavaClass"), "Package should contain MyJavaClass")

        val rootPackageContents = DeclarationSearch.listPackageContents(indexDir, "")
        assertNotNull(rootPackageContents)
        assertTrue(rootPackageContents.subPackages.contains("com"), "Root should contain 'com' subpackage")

        val comPackageContents = DeclarationSearch.listPackageContents(indexDir, "com")
        assertNotNull(comPackageContents)
        assertTrue(comPackageContents.subPackages.contains("example"), "com should contain 'example' subpackage")
    }
}
