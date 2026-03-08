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
import kotlin.test.assertTrue

class SymbolSearchTest {

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
            SymbolSearch.index(depDir, indexDir)
        }

        suspend fun assertFound(query: String, expectedPath: String, expectedLine: Int) {
            val response = SymbolSearch.search(indexDir, query)
            val results = response.results
            println("Searched for: $query. Results: $results")
            assertTrue(
                results.any { it.relativePath == expectedPath && it.line == expectedLine },
                "Symbol $query not found at $expectedPath:$expectedLine. Found: $results"
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
    }
}
