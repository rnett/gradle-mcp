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
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.exists
import kotlin.time.measureTimedValue

object DeclarationSearch : LuceneBaseSearchProvider() {
    object Fields {
        const val NAME = "name"
        const val FQN = "fqn"
        const val PACKAGE_NAME = "packageName"
        const val PATH = "path"
        const val LINE = "line"
        const val OFFSET = "offset"
    }

    override val logger = LoggerFactory.getLogger(DeclarationSearch::class.java)
    override val name: String = "declarations"
    override val indexVersion: Int = 8

    override fun createAnalyzer() = org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper(
        LuceneUtils.createCaseSensitiveAnalyzer(),
        mapOf(
            Fields.FQN to org.apache.lucene.analysis.core.KeywordAnalyzer(),
            Fields.PACKAGE_NAME to org.apache.lucene.analysis.core.KeywordAnalyzer(),
            Fields.PATH to org.apache.lucene.analysis.core.KeywordAnalyzer()
        )
    )

    override suspend fun newIndexer(outputDir: Path): Indexer = object : LuceneBaseIndexer(outputDir) {
        private val pool = ConcurrentLinkedQueue<TreeSitterDeclarationExtractor>()

        override suspend fun indexFile(path: String, content: String) {
            val ext = path.substringAfterLast('.', "")
            if (ext !in listOf("java", "kt")) return

            val extractor = pool.poll() ?: TreeSitterDeclarationExtractor()
            val fileSymbols = try {
                extractor.extractSymbols(content, ext)
            } catch (e: Exception) {
                logger.error("Failed to extract symbols from $path", e)
                emptyList()
            } finally {
                pool.add(extractor)
            }

            val documents = fileSymbols.map { sym ->
                Document().apply {
                    add(TextField(Fields.NAME, sym.name, org.apache.lucene.document.Field.Store.YES))
                    add(StringField(Fields.FQN, sym.fqn, org.apache.lucene.document.Field.Store.YES))
                    add(StringField(Fields.PACKAGE_NAME, sym.packageName, org.apache.lucene.document.Field.Store.YES))
                    add(StringField(Fields.PATH, path, org.apache.lucene.document.Field.Store.YES))
                    add(StoredField(Fields.LINE, sym.line))
                    add(StoredField(Fields.OFFSET, sym.offset))
                }
            }
            writer.addDocuments(documents)
        }

        override fun close() {
            var extractor = pool.poll()
            while (extractor != null) {
                extractor.close()
                extractor = pool.poll()
            }
            super.close()
        }
    }

    override suspend fun listPackageContents(indexDir: Path, packageName: String): PackageContents? = withContext(Dispatchers.IO) {
        if (!indexDir.exists()) return@withContext null
        withReader(indexDir) { reader ->
            val searcher = IndexSearcher(reader)

            // 1. Direct symbols in this package
            val symbols = mutableSetOf<String>()
            val symbolsQuery = TermQuery(Term(Fields.PACKAGE_NAME, packageName))
            val topSymbols = searcher.search(symbolsQuery, 5000)
            topSymbols.scoreDocs.forEach { hit ->
                val doc = searcher.storedFields().document(hit.doc, setOf(Fields.NAME))
                doc.get(Fields.NAME)?.let { symbols.add(it) }
            }

            // 2. Sub-packages
            val subPackages = mutableSetOf<String>()
            val prefix = if (packageName.isEmpty()) "" else "$packageName."

            val terms = MultiTerms.getTerms(reader, Fields.PACKAGE_NAME)
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

            if (symbols.isEmpty() && subPackages.isEmpty()) return@withReader null

            PackageContents(symbols.sorted(), subPackages.sorted())
        }
    }

    override suspend fun search(indexDir: Path, query: String, pagination: PaginationInput): SearchResponse<RelativeSearchResult> = withContext(Dispatchers.IO) {
        val (res, duration) = measureTimedValue {
            if (!indexDir.exists()) {
                return@withContext SearchResponse(emptyList(), error = "Symbol index dir does not exist: $indexDir")
            }

            withReader(indexDir) { reader ->
                val searcher = IndexSearcher(reader)
                createAnalyzer().use { analyzer ->
                    val q = try {
                        val parser = MultiFieldQueryParser(arrayOf(Fields.NAME, Fields.FQN), analyzer)
                        parser.allowLeadingWildcard = true
                        parser.parse(query)
                    } catch (e: Exception) {
                        val message = e.message ?: "Unknown error"
                        val error = LuceneUtils.formatSyntaxError(message)
                        return@withReader SearchResponse<RelativeSearchResult>(
                            emptyList(),
                            error = error
                        )
                    }

                    val topDocs = LuceneUtils.searchPaginated(searcher, q, pagination)
                    val hits = topDocs.scoreDocs

                    val matchedResults: List<RelativeSearchResult> = hits.drop(pagination.offset).take(pagination.limit).map { hit ->
                        val doc = searcher.storedFields().document(hit.doc)
                        RelativeSearchResult(
                            relativePath = doc.get(Fields.PATH),
                            offset = doc.getField(Fields.OFFSET).numericValue().toInt(),
                            line = doc.getField(Fields.LINE).numericValue().toInt(),
                            score = hit.score
                        )
                    }.toList()

                    SearchResponse(matchedResults, interpretedQuery = q.toString(), totalResults = topDocs.totalHits.value.toInt())
                }
            }
        }
        logger.info("Symbol search for \"$query\" (offset=${pagination.offset}, limit=${pagination.limit}) took $duration (${res.results.size} results)")
        return@withContext res
    }
}
