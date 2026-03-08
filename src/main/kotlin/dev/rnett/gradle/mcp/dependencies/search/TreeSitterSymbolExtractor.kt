package dev.rnett.gradle.mcp.dependencies.search

import org.treesitter.TSNode
import org.treesitter.TSParser
import org.treesitter.TSQuery
import org.treesitter.TSQueryCursor
import org.treesitter.TSQueryMatch
import org.treesitter.TreeSitterJava
import org.treesitter.TreeSitterKotlin
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText

data class ExtractedSymbol(
    val name: String,
    val fqn: String,
    val packageName: String,
    val line: Int,
    val offset: Int
)

class TreeSitterSymbolExtractor : AutoCloseable {
    private val javaLang = TreeSitterJava()
    private val kotlinLang = TreeSitterKotlin()

    private val javaQuery = TSQuery(
        javaLang, """
        (class_declaration (identifier) @name) @class
        (interface_declaration (identifier) @name) @interface
        (annotation_type_declaration (identifier) @name) @annotation
        (enum_declaration (identifier) @name) @enum
        (enum_constant (identifier) @name) @enum_constant
        (record_declaration (identifier) @name) @record
        (method_declaration (identifier) @name) @method
        (field_declaration (variable_declarator (identifier) @name)) @field
    """.trimIndent()
    )

    private val kotlinQuery = TSQuery(
        kotlinLang, """
        (class_declaration (type_identifier) @name) @class
        (object_declaration (type_identifier) @name) @object
        (function_declaration (simple_identifier) @name) @function
        (property_declaration (variable_declaration (simple_identifier) @name)) @property
        (enum_entry (simple_identifier) @name) @enum_entry
        (type_alias (type_identifier) @name) @typealias
    """.trimIndent()
    )

    private val javaPackageQuery = TSQuery(javaLang, "(package_declaration [(identifier) (scoped_identifier)] @package)")
    private val kotlinPackageQuery = TSQuery(kotlinLang, "(package_header (identifier) @package)")

    fun extractSymbols(file: Path): List<ExtractedSymbol> {
        val ext = file.extension
        val src = file.readText()
        return extractSymbols(src, ext)
    }

    fun extractSymbols(src: String, ext: String): List<ExtractedSymbol> {
        return if (ext == "java") {
            TSParser().use { parser ->
                parser.setLanguage(javaLang)
                parser.parseString(null, src).use { tree ->
                    extract(tree.rootNode, src, javaQuery, javaPackageQuery, ext)
                }
            }
        } else if (ext == "kt") {
            TSParser().use { parser ->
                parser.setLanguage(kotlinLang)
                parser.parseString(null, src).use { tree ->
                    extract(tree.rootNode, src, kotlinQuery, kotlinPackageQuery, ext)
                }
            }
        } else {
            emptyList()
        }
    }

    private fun extract(root: TSNode, src: String, query: TSQuery, packageQuery: TSQuery, ext: String): List<ExtractedSymbol> {
        val packageName = extractPackageName(root, src, packageQuery)
        val symbols = mutableListOf<ExtractedSymbol>()

        TSQueryCursor().use { cursor ->
            cursor.exec(query, root)
            val match = TSQueryMatch()

            while (cursor.nextMatch(match)) {
                val captures = match.captures
                // Find the capture that has name "name"
                val nameCapture = captures.find { query.getCaptureNameForId(it.index) == "name" } ?: continue

                val node = nameCapture.node
                val startByte = node.startByte
                val endByte = node.endByte
                val name = src.substring(startByte, endByte)

                // Extract FQN by walking up the tree
                val fqnParts = mutableListOf<String>()

                // start at parent.parent because parent is the declaration itself
                var current: TSNode? = node.parent?.parent
                while (current != null && !current.isNull()) {
                    val nodeType = current.type
                    if (nodeType in listOf("class_declaration", "interface_declaration", "annotation_type_declaration", "enum_declaration", "record_declaration", "object_declaration")) {
                        // Try to extract the name of this parent container by finding its identifier
                        var nameNode: TSNode? = null
                        for (i in 0 until current.childCount) {
                            val child = current.getChild(i)
                            if (child != null && !child.isNull() && (child.type == "identifier" || child.type == "type_identifier")) {
                                nameNode = child
                                break
                            }
                        }

                        if (nameNode != null && !nameNode.isNull() && nameNode.endByte > nameNode.startByte) {
                            val parentName = src.substring(nameNode.startByte, nameNode.endByte)
                            fqnParts.add(parentName)
                        }
                    }
                    current = current.parent
                }

                fqnParts.reverse()
                fqnParts.add(name)
                val fqn = if (packageName.isEmpty()) fqnParts.joinToString(".") else packageName + "." + fqnParts.joinToString(".")

                symbols.add(
                    ExtractedSymbol(
                        name = name,
                        fqn = fqn,
                        packageName = packageName,
                        line = node.startPoint.row + 1,
                        offset = startByte
                    )
                )
            }
        }
        return symbols
    }

    private fun extractPackageName(root: TSNode, src: String, query: TSQuery): String {
        TSQueryCursor().use { cursor ->
            cursor.exec(query, root)
            val match = TSQueryMatch()
            if (cursor.nextMatch(match)) {
                val capture = match.captures.firstOrNull() ?: return ""
                return src.substring(capture.node.startByte, capture.node.endByte)
            }
        }
        return ""
    }

    override fun close() {
        javaQuery.close()
        kotlinQuery.close()
        javaPackageQuery.close()
        kotlinPackageQuery.close()
        javaLang.close()
        kotlinLang.close()
    }
}

