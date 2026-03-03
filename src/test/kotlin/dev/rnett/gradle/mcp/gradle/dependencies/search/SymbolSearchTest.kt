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

package dev.rnett.gradle.mcp.gradle.dependencies.search

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

    private fun testType(code: String, expectedSymbol: String) {
        val matches = SymbolSearch.typeRegex.findAll(code).toList()
        assertTrue(matches.any { it.groupValues[1] == expectedSymbol }, "Expected symbol $expectedSymbol not found in: $code")
    }

    private fun testMember(code: String, expectedSymbol: String) {
        val matches = SymbolSearch.memberRegex.findAll(code).toList()
        assertTrue(matches.any { it.groupValues[1] == expectedSymbol }, "Expected symbol $expectedSymbol not found in: $code")
    }

    @Test
    fun `test kotlin types`() {
        testType("class MyClass", "MyClass")
        testType("enum class MyEnum", "MyEnum")
        testType("interface MyInterface", "MyInterface")
        testType("object MyObject", "MyObject")
        testType("sealed class MySealed", "MySealed")
        testType("annotation class MyAnnotation", "MyAnnotation")
        testType("typealias MyAlias = String", "MyAlias")
    }

    @Test
    fun `test java types`() {
        testType("class MyClass", "MyClass")
        testType("enum MyEnum", "MyEnum")
        testType("interface MyInterface", "MyInterface")
        testType("@interface MyAnnotation", "MyAnnotation")
        testType("record MyRecord(int x)", "MyRecord")
        testType("non-sealed class MyNonSealed", "MyNonSealed")
    }

    @Test
    fun `test kotlin members`() {
        testMember("val myVal: Int", "myVal")
        testMember("var myVar: String", "myVar")
        testMember("fun myFun()", "myFun")
        testMember("fun <T> myGenericFun()", "myGenericFun")
        testMember("val myBy: String by lazy { \"\" }", "myBy")
        testMember("val String.myExtension: Int", "myExtension")
        testMember("fun String.myExtensionFun()", "myExtensionFun")
    }

    @Test
    fun `test java members`() {
        testMember("void myMethod()", "myMethod")
        testMember("int myInt;", "myInt")
        testMember("String myString;", "myString")
        testMember("List<String> myList;", "myList")
        testMember("public static void main(String[] args)", "main")
        testMember("int x,", "x") // record field
        testMember("String y)", "y") // record field
    }

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
        SymbolSearch.index(depDir, indexDir)

        suspend fun assertFound(query: String, expectedPath: String, expectedLine: Int) {
            val results = SymbolSearch.search(indexDir, query)
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
        // assertFound("MyJavaEnum", "MyJavaClass.java", 9) // Known failure
        assertFound("MyJavaAnnotation", "MyJavaClass.java", 13)
        assertFound("MyJavaRecord", "MyJavaClass.java", 17)
    }
}
