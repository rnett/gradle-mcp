package dev.rnett.gradle.mcp.dependencies.search

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue

class TreeSitterDeclarationExtractorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `test comprehensive kotlin declarations`() = runTest {
        val ktFile = tempDir.resolve("Test.kt")
        ktFile.writeText(
            """
            package com.example.model
            
            class User(val id: String) {
                fun getName(): String = "test"
                
                object CompanionObj {
                    val CONST = "val"
                }
            }
            
            data class Product(val price: Double)
            
            @JvmInline
            value class UserId(val id: String)
            
            sealed interface Node {
                class Leaf(val v: Int) : Node
                object Empty : Node
            }
            
            typealias NodeList = List<Node>
            
            enum class Status {
                ACTIVE, INACTIVE
            }
            
            fun String.extFunc() {}
            val String.extProp: Int get() = 0
            
            class Outer {
                inner class Inner {
                    fun innerMethod() {}
                }
                class Nested
            }
        """.trimIndent()
        )

        val extractor = TreeSitterDeclarationExtractor()
        val symbols = extractor.extractSymbols(ktFile)
        val symbolInfo = symbols.map { "${it.fqn} (${it.line})" }.toSet()

        val expected = setOf(
            "com.example.model.User (3)",
            "com.example.model.User.getName (4)",
            "com.example.model.User.CompanionObj (6)",
            "com.example.model.User.CompanionObj.CONST (7)",
            "com.example.model.Product (11)",
            "com.example.model.UserId (14)",
            "com.example.model.Node (16)",
            "com.example.model.Node.Leaf (17)",
            "com.example.model.Node.Empty (18)",
            "com.example.model.NodeList (21)",
            "com.example.model.Status (23)",
            "com.example.model.Status.ACTIVE (24)",
            "com.example.model.Status.INACTIVE (24)",
            "com.example.model.extFunc (27)",
            "com.example.model.extProp (28)",
            "com.example.model.Outer (30)",
            "com.example.model.Outer.Inner (31)",
            "com.example.model.Outer.Inner.innerMethod (32)",
            "com.example.model.Outer.Nested (34)"
        )

        val missing = expected - symbolInfo
        val extra = symbolInfo - expected
        println("All Kotlin symbols extracted:\n" + symbolInfo.joinToString("\n"))
        assertTrue(missing.isEmpty(), "Missing Kotlin symbols: ${missing.joinToString()}\nExtra symbols: ${extra.joinToString()}")
    }

    @Test
    fun `test comprehensive java declarations`() = runTest {
        val javaFile = tempDir.resolve("MyClass.java")
        javaFile.writeText(
            """
            package com.example.java;
            
            public class MyClass {
                private String field1;
                
                public void method1() {}
                
                public static class Nested {
                    public void nestedMethod() {}
                }
            }
            
            public record UserRecord(String id, String name) {
                public void print() {}
                public static final String CONST = "const";
            }
            
            public sealed interface Shape permits Circle, Square {
                default void draw() {}
                
                enum Mode {
                    FAST, SLOW
                }
            }
            
            public final class Circle implements Shape {}
            public final class Square implements Shape {}
        """.trimIndent()
        )

        val extractor = TreeSitterDeclarationExtractor()
        val symbols = extractor.extractSymbols(javaFile)
        val symbolInfo = symbols.map { "${it.fqn} (${it.line})" }.toSet()

        val expected = setOf(
            "com.example.java.MyClass (3)",
            "com.example.java.MyClass.field1 (4)",
            "com.example.java.MyClass.method1 (6)",
            "com.example.java.MyClass.Nested (8)",
            "com.example.java.MyClass.Nested.nestedMethod (9)",
            "com.example.java.UserRecord (13)",
            "com.example.java.UserRecord.print (14)",
            "com.example.java.UserRecord.CONST (15)",
            "com.example.java.Shape (18)",
            "com.example.java.Shape.draw (19)",
            "com.example.java.Shape.Mode (21)",
            "com.example.java.Shape.Mode.FAST (22)",
            "com.example.java.Shape.Mode.SLOW (22)",
            "com.example.java.Circle (26)",
            "com.example.java.Square (27)"
        )

        val missing = expected - symbolInfo
        val extra = symbolInfo - expected
        println("All Java symbols extracted:\n" + symbolInfo.joinToString("\n"))
        assertTrue(missing.isEmpty(), "Missing Java symbols: ${missing.joinToString()}\nExtra symbols: ${extra.joinToString()}")
    }

    @Test
    fun `print java tree`() = runTest {
        val javaFile = tempDir.resolve("Temp.java")
        javaFile.writeText("package com.example; class X { int y; void z() {} } interface I {} enum E { A } record R() {}")
        val parser = org.treesitter.TSParser().apply { setLanguage(org.treesitter.TreeSitterJava()) }
        val tree = parser.parseString(null, javaFile.readText())
        printTree(tree.rootNode, "")
    }

    @Test
    fun `print kotlin tree`() = runTest {
        val ktFile = tempDir.resolve("Temp.kt")
        ktFile.writeText("package com.example; class X { val y = 1; fun z() {} } object O {} enum class E { A } typealias T = String")
        val parser = org.treesitter.TSParser().apply { setLanguage(org.treesitter.TreeSitterKotlin()) }
        val tree = parser.parseString(null, ktFile.readText())
        printTree(tree.rootNode, "")
    }

    private fun printTree(node: org.treesitter.TSNode, indent: String) {
        var str = indent + node.type
        if (node.childCount == 0 && node.startByte != node.endByte) {
            // we could read the text here if we passed the source 
        }
        println(str)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                // To get field names, we would do getting by field id or name, but TSNode API might have `getFieldNameForChild(i)`
                // Let's just print type
                printTree(child, indent + "  ")
            }
        }
    }
}
