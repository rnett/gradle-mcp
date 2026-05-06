package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.lucene.LuceneUtils
import dev.rnett.gradle.mcp.tools.PaginationInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.lucene.document.Document
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.MultiTerms
import org.apache.lucene.index.Term
import org.apache.lucene.index.TermsEnum
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.apache.lucene.util.BytesRef
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.measureTimedValue

class DeclarationSearch(
    private val extractor: TreeSitterDeclarationExtractor
) : LuceneBaseSearchProvider() {
    companion object {
        const val NAME = "name"
        const val FQN = "fqn"
        const val PACKAGE_NAME = "packageName"
        const val PATH = "path"
        const val LINE = "line"
        const val OFFSET = "offset"
        const val SOURCE_HASH = "sourceHash"
    }

    override val logger = LoggerFactory.getLogger(DeclarationSearch::class.java)
    override val name: String = "declarations"
    override val indexVersion: Int = 14

    override fun createAnalyzer() = org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper(
        LuceneUtils.createCaseSensitiveAnalyzer(),
        mapOf(
            FQN to org.apache.lucene.analysis.core.KeywordAnalyzer(),
            PACKAGE_NAME to org.apache.lucene.analysis.core.KeywordAnalyzer(),
            PATH to org.apache.lucene.analysis.core.KeywordAnalyzer()
        )
    )

    override suspend fun newIndexer(outputDir: Path): Indexer = object : LuceneBaseIndexer(outputDir) {
        override suspend fun indexFile(entry: IndexEntry) {
            val path = entry.relativePath
            val content = entry.content
            val ext = path.substringAfterLast('.', "")
            if (ext !in listOf("java", "kt")) return

            val fileSymbols = try {
                extractor.extractSymbols(content, ext)
            } catch (e: Exception) {
                logger.error("Failed to extract symbols from $path", e)
                emptyList()
            }

            val documents = fileSymbols.map {
                Document().apply {
                    add(TextField(NAME, it.name, org.apache.lucene.document.Field.Store.YES))
                    add(StringField(FQN, it.fqn, org.apache.lucene.document.Field.Store.YES))
                    add(StringField(PACKAGE_NAME, it.packageName, org.apache.lucene.document.Field.Store.YES))
                    add(StringField(PATH, path, org.apache.lucene.document.Field.Store.YES))
                    entry.sourceHash?.let { add(StringField(SOURCE_HASH, it, org.apache.lucene.document.Field.Store.YES)) }
                    add(StoredField(LINE, it.line))
                    add(StoredField(OFFSET, it.offset))
                }
            }
            writer.addDocuments(documents)
        }

    }

    override suspend fun listPackageContents(indexDirs: List<Path>, packageName: String): PackageContents? = withContext(Dispatchers.IO) {
        val existingIndexDirs = indexDirs.filter { it.exists() }
        if (existingIndexDirs.isEmpty()) return@withContext null
        withMultiReader(existingIndexDirs) { reader ->
            val searcher = IndexSearcher(reader)

            val symbols = mutableSetOf<String>()
            val symbolsQuery = TermQuery(Term(PACKAGE_NAME, packageName))
            val topSymbols = searcher.search(symbolsQuery, 5000)
            topSymbols.scoreDocs.forEach {
                val doc = searcher.storedFields().document(it.doc, setOf(NAME))
                doc.get(NAME)?.let { symbols.add(it) }
            }

            val subPackages = mutableSetOf<String>()
            val prefix = if (packageName.isEmpty()) "" else "$packageName."

            val terms = MultiTerms.getTerms(reader, PACKAGE_NAME)
            if (terms != null) {
                val iterator = terms.iterator()
                if (iterator.seekCeil(BytesRef(prefix)) != TermsEnum.SeekStatus.END) {
                    var term = iterator.term()
                    while (term != null) {
                        val fullPkg = term.utf8ToString()
                        if (!fullPkg.startsWith(prefix)) break

                        val remainder = fullPkg.substring(prefix.length)
                        if (remainder.isNotEmpty()) {
                            subPackages.add(remainder.substringBefore('.'))
                        }
                        term = iterator.next()
                    }
                }
            }

            if (symbols.isEmpty() && subPackages.isEmpty()) return@withMultiReader null

            PackageContents(symbols.sorted(), subPackages.sorted())
        }
    }

    override suspend fun search(
        indexDirs: Map<Path, Boolean>,
        query: String,
        pagination: PaginationInput,
        filter: ((String) -> Boolean)?
    ): SearchResponse<RelativeSearchResult> = withContext(Dispatchers.IO) {
        val (res, duration) = measureTimedValue {
            val existingIndexDirs = indexDirs.keys.filter { resolveIndexDir(it).exists() }
            if (existingIndexDirs.isEmpty()) {
                return@withContext SearchResponse<RelativeSearchResult>(
                    emptyList(),
                    error = "No Lucene index directories exist among the provided paths."
                )
            }

            withMultiReader(existingIndexDirs) { reader ->
                val searcher = IndexSearcher(reader)
                createAnalyzer().use { analyzer ->
                    val baseQuery = try {
                        if (query.startsWith("/") && query.endsWith("/") && query.length > 2) {
                            val pattern = query.substring(1, query.length - 1)
                            org.apache.lucene.search.RegexpQuery(Term(FQN, pattern))
                        } else {
                            val parser = MultiFieldQueryParser(arrayOf(NAME, FQN), analyzer)
                            parser.allowLeadingWildcard = true
                            parser.parse(query)
                        }
                    } catch (e: Exception) {
                        val message = e.message ?: "Unknown error"
                        val error = LuceneUtils.formatSyntaxError(message)
                        return@withMultiReader SearchResponse<RelativeSearchResult>(
                            emptyList(),
                            error = error
                        )
                    }

                    val finalQuery = baseQuery

                    val topDocs = if (filter != null) {
                        searcher.search(finalQuery, Int.MAX_VALUE)
                    } else {
                        LuceneUtils.searchPaginated(searcher, finalQuery, pagination)
                    }
                    val hits = topDocs.scoreDocs

                    var totalFilteredHits = 0
                    val matchedResults: List<RelativeSearchResult> =
                        hits.asSequence().map {
                            val doc = searcher.storedFields().document(it.doc)
                            RelativeSearchResult(
                                relativePath = doc.get(PATH),
                                offset = doc.getField(OFFSET).numericValue().toInt(),
                                line = doc.getField(LINE).numericValue().toInt(),
                                score = it.score
                            )
                        }.filter {
                            val match = filter == null || filter(it.relativePath)
                            if (match) totalFilteredHits++
                            match
                        }
                            .drop(pagination.offset)
                            .take(pagination.limit)
                            .toList()

                    val hasMore = if (filter != null) {
                        totalFilteredHits > pagination.offset + pagination.limit
                    } else {
                        totalFilteredHits > pagination.offset + pagination.limit || topDocs.totalHits.value > pagination.offset + pagination.limit
                    }

                    SearchResponse(
                        matchedResults,
                        interpretedQuery = finalQuery.toString(),
                        hasMore = hasMore
                    )
                }
            } ?: SearchResponse(emptyList(), error = "Failed to open index readers.")
        }
        logger.info("Symbol search for \"$query\" (offset=${pagination.offset}, limit=${pagination.limit}) took $duration (${res.results.size} results)")
        return@withContext res
    }
}
