package dev.rnett.gradle.mcp.gradle.dependencies.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText

object SymbolSearch : SearchProvider {
    override val name: String = "symbols"

    val typeRegex = Regex("(?<!\\w)(?:class|enum(?:\\s+class)?|interface|@interface|object|record|typealias|(?:sealed|non-sealed|annotation)\\s+class)\\s+([a-zA-Z_]\\w*)")
    val memberRegex =
        Regex("(?<!\\w)(?!(?:package|import|return|if|else|while|for|do|try|catch|finally|throw|new|class|interface|enum|object|record|typealias)\\b)(?:(?:val|fun|var|void|int|long|double|float|char|boolean|byte|short|public|private|protected|internal|static|final|abstract|open|override|suspend|inline|[a-zA-Z_][\\w<>\\[\\]]*)\\s+)+(?:[a-zA-Z_][\\w<>\\[\\]\\.]*\\.)?([a-zA-Z_]\\w*)\\s*(?:[(={:;,)]|by)")

    private val sourceExtensions = setOf("kt", "kts", "java")

    private const val v2FileName = "symbols-v2.txt"

    override suspend fun index(dependencyDir: Path, outputDir: Path) = withContext(Dispatchers.IO) {
        val allSymbols = dependencyDir.walk().filter { it.isRegularFile() && it.extension in sourceExtensions }.flatMap { findSymbols(it, dependencyDir) }.toList()
        outputDir.createDirectories()
        val file = outputDir.resolve(v2FileName)
        writeIndices(file, allSymbols)
    }

    data class Symbol(val name: String, val path: String, val line: Int, val offset: Int)

    private fun findSymbols(file: Path, root: Path): Sequence<Symbol> {
        val text = file.readText()
        val typeMatches = typeRegex.findAll(text)
        val memberMatches = memberRegex.findAll(text)

        return (typeMatches + memberMatches).map {
            val offset = it.range.first
            val line = text.substring(0, offset).count { it == '\n' } + 1
            Symbol(
                it.groupValues[1],
                file.relativeTo(root).toString().replace('\\', '/'),
                line,
                offset
            )
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <R> readIndices(file: Path, block: (Sequence<Symbol>) -> R): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        file.bufferedReader().use {
            return it.useLines {
                block(it.mapNotNull {
                    val parts = it.split("||")
                    if (parts.size != 4) return@mapNotNull null
                    Symbol(parts[0], parts[1], parts[2].toInt(), parts[3].toInt())
                })
            }
        }
    }

    private fun writeIndices(file: Path, symbols: Collection<Symbol>) {
        file.createParentDirectories()
        file.writeText(symbols.joinToString("\n") { "${it.name}||${it.path}||${it.line}||${it.offset}" })
    }

    override suspend fun mergeIndices(indexDirs: Map<Path, Path>, outputDir: Path) = withContext(Dispatchers.IO) {
        val symbols = mutableSetOf<Symbol>()
        indexDirs.forEach { (idxDir, relativePath) ->
            readIndices(idxDir.resolve(v2FileName)) { symbols.addAll(it.map { it.copy(path = relativePath.resolve(it.path).toString().replace('\\', '/')) }) }
        }
        writeIndices(outputDir.resolve(v2FileName), symbols)
    }

    override suspend fun search(indexDir: Path, query: String): List<RelativeSearchResult> = withContext(Dispatchers.IO) {
        val indexFile = indexDir.resolve(v2FileName)
        if (!indexFile.exists()) {
            return@withContext emptyList()
        }

        val allSymbols = readIndices(indexFile) { it.toList() }

        val queryRegex = Regex(query, RegexOption.IGNORE_CASE)

        return@withContext allSymbols.asSequence().filter { it.name.matches(queryRegex) }
            .map {
                RelativeSearchResult(it.path, offset = it.offset, line = it.line, score = null)
            }.toList()
    }

}