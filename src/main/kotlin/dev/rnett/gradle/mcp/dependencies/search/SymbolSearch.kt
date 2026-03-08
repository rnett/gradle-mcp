package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.lucene.LuceneUtils
import dev.rnett.gradle.mcp.tools.PaginationInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.measureTimedValue

object SymbolSearch : LuceneBaseSearchProvider() {
    override val logger = LoggerFactory.getLogger(SymbolSearch::class.java)
    override val name: String = "symbols"
    override val indexVersion: Int = 3

    override fun createAnalyzer() = StandardAnalyzer()

    override fun copyDocument(oldDoc: Document, newPath: String): Document {
        return Document().apply {
            add(StringField("name", oldDoc.get("name"), org.apache.lucene.document.Field.Store.YES))
            add(TextField("name_analyzed", oldDoc.get("name"), org.apache.lucene.document.Field.Store.NO))
            add(StringField("fqn", oldDoc.get("fqn"), org.apache.lucene.document.Field.Store.YES))
            add(TextField("fqn_analyzed", oldDoc.get("fqn"), org.apache.lucene.document.Field.Store.NO))
            add(StringField("path", newPath, org.apache.lucene.document.Field.Store.YES))
            add(StoredField("line", oldDoc.getField("line").numericValue().toInt()))
            add(StoredField("offset", oldDoc.getField("offset").numericValue().toInt()))
        }
    }

    override suspend fun newIndexer(outputDir: Path): Indexer = object : LuceneBaseIndexer(outputDir) {
        private val localExtractor = TreeSitterSymbolExtractor()

        override suspend fun indexFile(path: String, content: String) {
            val ext = path.substringAfterLast('.', "")
            if (ext !in listOf("java", "kt")) return

            val fileSymbols = try {
                localExtractor.extractSymbols(content, ext)
            } catch (e: Exception) {
                logger.error("Failed to extract symbols from $path", e)
                emptyList()
            }

            val documents = fileSymbols.map { sym ->
                Document().apply {
                    add(StringField("name", sym.name, org.apache.lucene.document.Field.Store.YES))
                    add(TextField("name_analyzed", sym.name, org.apache.lucene.document.Field.Store.NO))
                    add(StringField("fqn", sym.fqn, org.apache.lucene.document.Field.Store.YES))
                    add(TextField("fqn_analyzed", sym.fqn, org.apache.lucene.document.Field.Store.NO))
                    add(StringField("path", path, org.apache.lucene.document.Field.Store.YES))
                    add(StoredField("line", sym.line))
                    add(StoredField("offset", sym.offset))
                }
            }
            writer.addDocuments(documents)
        }

        override fun close() {
            localExtractor.close()
            super.close()
        }
    }


    override suspend fun search(indexDir: Path, query: String, pagination: PaginationInput): SearchResponse<RelativeSearchResult> = withContext(Dispatchers.IO) {
        val (results, duration) = measureTimedValue {
            if (!indexDir.exists()) {
                throw IllegalStateException("Symbol index dir does not exist: $indexDir")
            }

            val reader = readerCache.get(indexDir)
            val searcher = IndexSearcher(reader)
            StandardAnalyzer().use { analyzer ->
                val booleanQuery = BooleanQuery.Builder()

                val nameQuery = QueryParser("name_analyzed", analyzer).parse(query)
                val exactNameQuery = TermQuery(Term("name", query))
                val exactFqnQuery = TermQuery(Term("fqn", query))

                booleanQuery.add(nameQuery, BooleanClause.Occur.SHOULD)
                booleanQuery.add(exactNameQuery, BooleanClause.Occur.SHOULD)
                booleanQuery.add(exactFqnQuery, BooleanClause.Occur.SHOULD)

                val q = booleanQuery.build()
                val topDocs = LuceneUtils.searchPaginated(searcher, q, pagination)
                val hits = topDocs.scoreDocs

                val matchedResults = hits.drop(pagination.offset).take(pagination.limit).map { hit ->
                    val doc = searcher.storedFields().document(hit.doc)
                    RelativeSearchResult(
                        relativePath = doc.get("path"),
                        offset = doc.getField("offset").numericValue().toInt(),
                        line = doc.getField("line").numericValue().toInt(),
                        score = hit.score
                    )
                }.toList()

                SearchResponse(matchedResults, interpretedQuery = q.toString())
            }
        }
        logger.info("Symbol search for \"$query\" (offset=${pagination.offset}, limit=${pagination.limit}) took $duration (${results.results.size} results)")
        return@withContext results
    }
}
