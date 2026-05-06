package dev.rnett.gradle.mcp.dependencies.search

import io.github.treesitter.ktreesitter.Language
import io.github.treesitter.ktreesitter.Node
import io.github.treesitter.ktreesitter.Parser
import io.github.treesitter.ktreesitter.Query
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.extension
import kotlin.io.path.readText

data class ExtractedSymbol(
    val name: String,
    val fqn: String,
    val packageName: String,
    val line: Int,
    val offset: Int
)

private val PACKAGE_DECLARATION_REGEX = Regex("""\bpackage\s+([A-Za-z_][A-Za-z0-9_]*(?:\s*\.\s*[A-Za-z_][A-Za-z0-9_]*)*)""")

private class ExtractionContext(
    val parser: Parser,
    val javaQuery: Query,
    val kotlinQuery: Query,
    val javaPackageQuery: Query,
    val kotlinPackageQuery: Query
)

private data class ExtractorConfig(
    val javaLang: Language,
    val kotlinLang: Language,
    val javaQueryStr: String,
    val kotlinQueryStr: String,
    val javaPkgQueryStr: String,
    val kotlinPkgQueryStr: String
)

class TreeSitterDeclarationExtractor(private val languageProvider: TreeSitterLanguageProvider) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(TreeSitterDeclarationExtractor::class.java)

    private val contextPool = ConcurrentLinkedQueue<ExtractionContext>()
    private val initMutex = Mutex()

    @Volatile
    private var config: ExtractorConfig? = null

    private suspend fun getConfig(): ExtractorConfig {
        config?.let { return it }
        return initMutex.withLock {
            config ?: createConfig().also { config = it }
        }
    }

    private suspend fun createConfig(): ExtractorConfig {
        val javaLang = languageProvider.getLanguage("java")
        val javaQueryStr = """
            (class_declaration (identifier) @name) @class
            (interface_declaration (identifier) @name) @interface
            (annotation_type_declaration (identifier) @name) @annotation
            (enum_declaration (identifier) @name) @enum
            (enum_constant (identifier) @name) @enum_constant
            (record_declaration (identifier) @name) @record
            (method_declaration (identifier) @name) @method
            (constructor_declaration (identifier) @name) @constructor

            (field_declaration (variable_declarator (identifier) @name)) @field
        """.trimIndent()
        val javaPkgQueryStr = "(package_declaration [(identifier) (scoped_identifier)] @name) @package"

        val kotlinLang = languageProvider.getLanguage("kotlin")
        val kotlinQueryStr = """
            (class_declaration (type_identifier) @name) @class
            (object_declaration (type_identifier) @name) @object
            (function_declaration (simple_identifier) @name) @function
            (property_declaration (variable_declaration (simple_identifier) @name)) @property
            (enum_entry (simple_identifier) @name) @enum_entry
            (type_alias (type_identifier) @name) @typealias
        """.trimIndent()
        val kotlinPkgQueryStr = "(package_header (_) @name) @package"

        return ExtractorConfig(javaLang, kotlinLang, javaQueryStr, kotlinQueryStr, javaPkgQueryStr, kotlinPkgQueryStr)
    }

    private suspend fun createContext(): ExtractionContext {
        val cfg = getConfig()
        val parser = Parser()
        return ExtractionContext(
            parser,
            Query(cfg.javaLang, cfg.javaQueryStr),
            Query(cfg.kotlinLang, cfg.kotlinQueryStr),
            Query(cfg.javaLang, cfg.javaPkgQueryStr),
            Query(cfg.kotlinLang, cfg.kotlinPkgQueryStr)
        )
    }

    override fun close() {
        contextPool.clear()
        config = null
    }

    suspend fun extractSymbols(file: Path): List<ExtractedSymbol> {
        val ext = file.extension
        val src = try {
            file.readText()
        } catch (e: Exception) {
            return emptyList()
        }
        return extractSymbols(src, ext)
    }

    suspend fun extractSymbols(src: String, ext: String): List<ExtractedSymbol> {
        if (ext != "java" && ext != "kt") return emptyList()
        val cfg = getConfig()

        val context = contextPool.poll() ?: createContext()
        return try {
            val lang = if (ext == "java") cfg.javaLang else cfg.kotlinLang
            val query = if (ext == "java") context.javaQuery else context.kotlinQuery
            val packageQuery = if (ext == "java") context.javaPackageQuery else context.kotlinPackageQuery

            context.parser.language = lang
            val tree = context.parser.parse(src)
            extract(
                tree.rootNode,
                src,
                query,
                packageQuery
            )
        } finally {
            contextPool.add(context)
        }
    }

    private fun extract(root: Node, src: String, query: Query, packageQuery: Query): List<ExtractedSymbol> {
        val srcBytes = src.toByteArray(Charsets.UTF_8)
        val packageName = extractPackageName(root, srcBytes, packageQuery)
        val matches = query.matches(root).toList()

        val byteOffsets = matches.mapNotNull { match ->
            match.captures.find { it.name == "name" }?.node?.startByte?.toInt()
        }.distinct()

        val charOffsets = getCharOffsets(srcBytes, byteOffsets)
        val byteToChar = byteOffsets.zip(charOffsets).toMap()

        val symbols = mutableListOf<ExtractedSymbol>()

        matches.forEach { match ->
            val nameCapture = match.captures.find { it.name == "name" } ?: return@forEach
            val node = nameCapture.node

            val name = srcBytes.decodeToString(node.startByte.toInt(), node.endByte.toInt())

            val fqnParts = mutableListOf<String>()
            var current: Node? = node.parent?.parent
            while (current != null) {
                if (current.type in listOf(
                        "class_declaration",
                        "interface_declaration",
                        "annotation_type_declaration",
                        "enum_declaration",
                        "record_declaration",
                        "object_declaration",
                        "enum_entry",
                        "enum_constant"
                    )
                ) {
                    current.children.find { it.type in listOf("identifier", "type_identifier", "simple_identifier") }?.let {
                        val partName = srcBytes.decodeToString(it.startByte.toInt(), it.endByte.toInt())
                        fqnParts.add(partName)
                    }
                }
                current = current.parent
            }

            val fqnPartsWithPkg = if (packageName.isNotEmpty()) {
                listOf(packageName) + fqnParts.reversed() + name
            } else {
                fqnParts.reversed() + name
            }

            symbols.add(
                ExtractedSymbol(
                    name = name,
                    fqn = fqnPartsWithPkg.joinToString("."),
                    packageName = packageName,
                    line = node.startPoint.row.toInt() + 1,
                    offset = byteToChar[node.startByte.toInt()] ?: 0
                )
            )
        }

        return symbols
    }

    private fun getCharOffsets(srcBytes: ByteArray, byteOffsets: List<Int>): List<Int> {
        if (byteOffsets.isEmpty()) return emptyList()
        val sorted = byteOffsets.withIndex().sortedBy { it.value }
        val result = IntArray(byteOffsets.size)

        var currentByte = 0
        var currentChar = 0

        for (item in sorted) {
            val targetByte = item.value
            while (currentByte < targetByte && currentByte < srcBytes.size) {
                val b = srcBytes[currentByte].toInt() and 0xFF
                val len = when {
                    b shr 7 == 0 -> 1
                    b shr 5 == 0x06 -> 2
                    b shr 4 == 0x0E -> 3
                    b shr 3 == 0x1E -> 4
                    else -> 1
                }
                currentByte += len
                currentChar += if (len == 4) 2 else 1
            }
            result[item.index] = currentChar
        }
        return result.toList()
    }

    private fun extractPackageName(root: Node, srcBytes: ByteArray, packageQuery: Query): String {
        extractPackageDeclarationText(root, srcBytes)?.let { return it }

        packageQuery.matches(root).forEach { match ->
            val captures = match.captures.filter { it.name == "name" }
            if (captures.isNotEmpty()) {
                val node = captures.map { it.node }.lastOrNull {
                    srcBytes.decodeToString(it.startByte.toInt(), it.endByte.toInt()).trim() != "package"
                }
                if (node != null) {
                    return srcBytes.decodeToString(node.startByte.toInt(), node.endByte.toInt()).trim().removeSuffix(";")
                }
            }
        }
        return ""
    }

    private fun extractPackageDeclarationText(root: Node, srcBytes: ByteArray): String? {
        val packageNode = root.findFirstNode("package_header")
            ?: root.findFirstNode("package_declaration")
            ?: return null
        val declaration = srcBytes.decodeToString(packageNode.startByte.toInt(), packageNode.endByte.toInt())
        return PACKAGE_DECLARATION_REGEX.find(declaration)
            ?.groupValues
            ?.get(1)
            ?.replace(Regex("""\s+"""), "")
            ?.takeIf { it.isNotEmpty() }
    }

    private fun Node.findFirstNode(type: String): Node? {
        if (this.type == type) return this
        children.forEach { child ->
            child.findFirstNode(type)?.let { return it }
        }
        return null
    }
}
