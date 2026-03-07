package dev.rnett.gradle.mcp.gradle.dependencies.search

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.withPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

object SymbolSearch : SearchProvider {
    private val LOGGER = LoggerFactory.getLogger(SymbolSearch::class.java)
    override val name: String = "symbols"
    override val indexVersion: Int = 1

    // needs to support Java, Kotlin, and Groovy
    // some false positives are ok for the regexes - they need to be simple and fast
    val kotlinTypeRegex =
        Regex("(?:^|\\s)(?:class|enum\\s+class|interface|object|typealias|sealed\\s+class|annotation\\s+class)\\s+([a-zA-Z_]\\w*)")
    val kotlinMemberRegex =
        Regex("(?:^|\\s)(?:(?:private|public|protected|internal|expect|actual|inline|external|tailrec|operator|infix|suspend|abstract|override|open|final|lateinit|sealed)\\s+)*(?:val|fun|var)\\s+(?:<[\\w\\s,<>]+>\\s+)?(?:[\\w<>.]+?\\.)?([a-zA-Z_]\\w*)")

    val javaTypeRegex =
        Regex("(?:^|\\s)(?:class|enum|interface|@interface|record|non-sealed\\s+class|sealed\\s+class)\\s+([a-zA-Z_]\\w*)")
    val javaMemberRegex =
        Regex("(?:^|\\s)(?:(?:public|private|protected|static|final|native|synchronized|abstract|transient|volatile)\\s+)*(?!class|interface|enum|record)(?:void|int|long|double|float|char|boolean|byte|short|[a-zA-Z_][\\w\\[\\]<>]*)\\s+([a-zA-Z_]\\w*)")

    val groovyTypeRegex = Regex("(?:^|\\s)(?:class|enum|interface|trait)\\s+([a-zA-Z_]\\w*)")
    val groovyMemberRegex =
        Regex("(?:^|\\s)(?:(?:public|private|protected|static|final|abstract|native|synchronized|transient|volatile)\\s+)*(?!class|interface|enum|trait|record)(?:def|void|int|long|double|float|char|boolean|byte|short|[a-zA-Z_][\\w\\[\\]<>]*)\\s+([a-zA-Z_]\\w*)")

    private const val v1FileName = "symbols-v1.txt"
    val indexDispatcher = Dispatchers.Default.limitedParallelism(maxOf(1, Runtime.getRuntime().availableProcessors() / 2), "indexing")

    @OptIn(ExperimentalAtomicApi::class)
    context(progress: ProgressReporter)
    override suspend fun index(dependencyDir: Path, outputDir: Path) {
        LOGGER.info("Starting symbol indexing for $dependencyDir")
        val (allSymbols, duration) = measureTimedValue {
            val sourceFiles = withContext(Dispatchers.IO) {
                dependencyDir.walk().filter { it.isRegularFile() && it.extension in SearchProvider.SOURCE_EXTENSIONS }.toList()
            }
            val fileCount = sourceFiles.size
            val symbols = coroutineScope {
                val cores = Runtime.getRuntime().availableProcessors()
                val targetParallelism = maxOf(1, cores / 2)
                val chunkSize = maxOf(1, fileCount / targetParallelism)

                val indexingProgress = progress.withPhase("INDEXING")
                val total = sourceFiles.size.toDouble()
                val done = AtomicInt(0)

                sourceFiles.chunked(chunkSize)
                    .map { chunk ->
                        async(indexDispatcher) {
                            chunk.flatMap {
                                val current = done.incrementAndFetch()
                                indexingProgress(current.toDouble(), total, "Indexing symbols for $dependencyDir")
                                findSymbols(it, dependencyDir)
                            }
                        }
                    }.awaitAll().flatten()
            }
            fileCount to symbols
        }
        val (fileCount, symbols) = allSymbols
        LOGGER.info("Symbol indexing for $dependencyDir took $duration ($fileCount files, ${symbols.size} symbols)")

        outputDir.createDirectories()
        val file = outputDir.resolve(v1FileName)
        writeIndices(file, symbols)
    }

    data class Symbol(val name: String, val path: String, val line: Int, val offset: Int)

    private suspend fun findSymbols(file: Path, root: Path): List<Symbol> = withContext(Dispatchers.IO) {
        val extension = file.extension
        val (typeRegex, memberRegex) = when (extension) {
            "kt" -> kotlinTypeRegex to kotlinMemberRegex
            "java" -> javaTypeRegex to javaMemberRegex
            "groovy" -> groovyTypeRegex to groovyMemberRegex
            else -> return@withContext emptyList()
        }

        val relativePath = file.relativeTo(root).toString().replace('\\', '/')
        val symbols = mutableListOf<Symbol>()
        var currentOffset = 0
        var currentLine = 1

        file.bufferedReader().use { reader ->
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    typeRegex.findAll(line).forEach { match ->
                        symbols.add(
                            Symbol(
                                match.groupValues[1],
                                relativePath,
                                currentLine,
                                currentOffset + match.range.first
                            )
                        )
                    }
                    memberRegex.findAll(line).forEach { match ->
                        symbols.add(
                            Symbol(
                                match.groupValues[1],
                                relativePath,
                                currentLine,
                                currentOffset + match.range.first
                            )
                        )
                    }
                }
                currentOffset += line.length + 1 // +1 for the newline
                currentLine++
            }
        }
        return@withContext symbols
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <R> readIndices(file: Path, block: (Sequence<Symbol>) -> R): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        return file.bufferedReader().use { reader ->
            reader.useLines { lines ->
                block(lines.mapNotNull {
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

    context(progress: ProgressReporter)
    override suspend fun mergeIndices(indexDirs: Map<Path, Path>, outputDir: Path) = withContext(Dispatchers.IO) {
        val duration = measureTime {
            val symbols = mutableSetOf<Symbol>()
            val mergingProgress = progress.withPhase("INDEXING")
            val total = indexDirs.size.toDouble()
            var current = 0.0

            indexDirs.forEach { (idxDir, relativePath) ->
                current++
                mergingProgress(current, total, "Merging symbol index for $relativePath")
                readIndices(idxDir.resolve(v1FileName)) { symbols.addAll(it.map { it.copy(path = relativePath.resolve(it.path).toString().replace('\\', '/')) }) }
            }
            writeIndices(outputDir.resolve(v1FileName), symbols)
        }
        LOGGER.info("Symbol index merging took $duration (${indexDirs.size} indices)")
    }

    override suspend fun search(indexDir: Path, query: String, pagination: PaginationInput): SearchResponse<RelativeSearchResult> = withContext(Dispatchers.IO) {
        val (results, duration) = measureTimedValue {
            val indexFile = indexDir.resolve(v1FileName)
            if (!indexFile.exists()) {
                throw IllegalStateException("Symbol index file does not exist: $indexFile")
            }

            val allSymbols = readIndices(indexFile) { it.toList() }

            val cleanedQuery = query.replace(Regex("^(?:class|interface|object|enum|fun|val|var|def|typealias|@interface|record)\\s+"), "")
            val queryRegex = try {
                Regex(cleanedQuery, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                return@measureTimedValue SearchResponse(emptyList(), error = "Invalid regex: ${e.message}")
            }

            val matches = allSymbols.asSequence()
                .filter { it.name.matches(queryRegex) }
                .toList()

            SearchResponse(
                matches.asSequence()
                    .drop(pagination.offset)
                    .take(pagination.limit)
                    .map {
                        RelativeSearchResult(it.path, offset = it.offset, line = it.line, score = null)
                    }.toList(),
                interpretedQuery = queryRegex.toString()
            )
        }
        val response = results
        LOGGER.info("Symbol search for \"$query\" (offset=${pagination.offset}, limit=${pagination.limit}) took $duration (${response.results.size} results)")
        return@withContext response
    }

}