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

    private fun testKotlinType(code: String, expectedSymbol: String) {
        val matches = SymbolSearch.kotlinTypeRegex.findAll(code).toList()
        assertTrue(matches.any { it.groupValues[1] == expectedSymbol }, "Expected symbol $expectedSymbol not found in: $code")
    }

    private fun testKotlinMember(code: String, expectedSymbol: String) {
        val matches = SymbolSearch.kotlinMemberRegex.findAll(code).toList()
        assertTrue(matches.any { it.groupValues[1] == expectedSymbol }, "Expected symbol $expectedSymbol not found in: $code")
    }

    private fun testJavaType(code: String, expectedSymbol: String) {
        val matches = SymbolSearch.javaTypeRegex.findAll(code).toList()
        assertTrue(matches.any { it.groupValues[1] == expectedSymbol }, "Expected symbol $expectedSymbol not found in: $code")
    }

    private fun testJavaMember(code: String, expectedSymbol: String) {
        val matches = SymbolSearch.javaMemberRegex.findAll(code).toList()
        assertTrue(matches.any { it.groupValues[1] == expectedSymbol }, "Expected symbol $expectedSymbol not found in: $code")
    }

    private fun testGroovyType(code: String, expectedSymbol: String) {
        val matches = SymbolSearch.groovyTypeRegex.findAll(code).toList()
        assertTrue(matches.any { it.groupValues[1] == expectedSymbol }, "Expected symbol $expectedSymbol not found in: $code")
    }

    private fun testGroovyMember(code: String, expectedSymbol: String) {
        val matches = SymbolSearch.groovyMemberRegex.findAll(code).toList()
        assertTrue(matches.any { it.groupValues[1] == expectedSymbol }, "Expected symbol $expectedSymbol not found in: $code")
    }

    @Test
    fun `test kotlin types`() {
        testKotlinType("class MyClass", "MyClass")
        testKotlinType("enum class MyEnum", "MyEnum")
        testKotlinType("interface MyInterface", "MyInterface")
        testKotlinType("object MyObject", "MyObject")
        testKotlinType("sealed class MySealed", "MySealed")
        testKotlinType("annotation class MyAnnotation", "MyAnnotation")
        testKotlinType("typealias MyAlias = String", "MyAlias")
    }

    @Test
    fun `test java types`() {
        testJavaType("class MyClass", "MyClass")
        testJavaType("enum MyEnum", "MyEnum")
        testJavaType("interface MyInterface", "MyInterface")
        testJavaType("@interface MyAnnotation", "MyAnnotation")
        testJavaType("record MyRecord(int x)", "MyRecord")
        testJavaType("non-sealed class MyNonSealed", "MyNonSealed")
    }

    @Test
    fun `test kotlin members`() {
        testKotlinMember("val myVal: Int", "myVal")
        testKotlinMember("var myVar: String", "myVar")
        testKotlinMember("private val myPrivateVal: Int", "myPrivateVal")
        testKotlinMember("internal var myInternalVar: String", "myInternalVar")
        testKotlinMember("fun myFun()", "myFun")
        testKotlinMember("protected fun myProtectedFun()", "myProtectedFun")
        testKotlinMember("fun <T> myGenericFun()", "myGenericFun")
        testKotlinMember("val myBy: String by lazy { \"\" }", "myBy")
        testKotlinMember("val String.myExtension: Int", "myExtension")
        testKotlinMember("fun String.myExtensionFun()", "myExtensionFun")
    }

    @Test
    fun `test java members`() {
        testJavaMember("void myMethod()", "myMethod")
        testJavaMember("int myInt;", "myInt")
        testJavaMember("String myString;", "myString")
        testJavaMember("List<String> myList;", "myList")
        testJavaMember("public static void main(String[] args)", "main")
        testJavaMember("private int myInt2 = 1;", "myInt2")
        testJavaMember("public void myJavaMethod()", "myJavaMethod")
        testJavaMember("int x,", "x") // record field
        testJavaMember("String y)", "y") // record field
        testJavaMember("final List<String> myList2 = new ArrayList<>();", "myList2")
    }

    @Test
    fun `test groovy types`() {
        testGroovyType("class MyClass", "MyClass")
        testGroovyType("enum MyEnum", "MyEnum")
        testGroovyType("interface MyInterface", "MyInterface")
        testGroovyType("trait MyTrait", "MyTrait")
    }

    @Test
    fun `test groovy members`() {
        testGroovyMember("def myDef", "myDef")
        testGroovyMember("def myDefMethod()", "myDefMethod")
        testGroovyMember("String myString", "myString")
        testGroovyMember("int myInt", "myInt")
        testGroovyMember("public void myGroovyMethod()", "myGroovyMethod")
        testGroovyMember("private static String myStaticGroovyField", "myStaticGroovyField")
    }

    @Test
    fun `test bare name should not match`() {
        assertTrue(SymbolSearch.kotlinMemberRegex.findAll("myBareName").toList().isEmpty(), "Should not match bare name in Kotlin: myBareName")
        assertTrue(SymbolSearch.javaMemberRegex.findAll("myBareName").toList().isEmpty(), "Should not match bare name in Java: myBareName")
        assertTrue(SymbolSearch.groovyMemberRegex.findAll("myBareName").toList().isEmpty(), "Should not match bare name in Groovy: myBareName")
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
        assertFound("MyJavaEnum", "MyJavaClass.java", 9)
        assertFound("MyJavaAnnotation", "MyJavaClass.java", 13)
        assertFound("MyJavaRecord", "MyJavaClass.java", 17)
    }
}
