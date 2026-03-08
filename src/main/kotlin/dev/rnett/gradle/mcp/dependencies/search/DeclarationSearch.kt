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
import org.apache.lucene.search.RegexpQuery
import org.apache.lucene.search.TermQuery
import org.apache.lucene.util.BytesRef
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.measureTimedValue

object DeclarationSearch : LuceneBaseSearchProvider() {
    override val logger = LoggerFactory.getLogger(DeclarationSearch::class.java)
    override val name: String = "declarations"
    override val indexVersion: Int = 6

    override fun createAnalyzer() = LuceneUtils.createCaseSensitiveAnalyzer()

    override fun copyDocument(oldDoc: Document, newPath: String): Document {
        return Document().apply {
            add(TextField("name", oldDoc.get("name"), org.apache.lucene.document.Field.Store.YES))
            add(TextField("fqn", oldDoc.get("fqn"), org.apache.lucene.document.Field.Store.YES))
            add(StringField("fqn_raw", oldDoc.get("fqn"), org.apache.lucene.document.Field.Store.NO))
            add(StringField("packageName", oldDoc.get("packageName"), org.apache.lucene.document.Field.Store.YES))
            add(StringField("path", newPath, org.apache.lucene.document.Field.Store.YES))
            add(StoredField("line", oldDoc.getField("line").numericValue().toInt()))
            add(StoredField("offset", oldDoc.getField("offset").numericValue().toInt()))
        }
    }

    override suspend fun newIndexer(outputDir: Path): Indexer = object : LuceneBaseIndexer(outputDir) {
        private val localExtractor = TreeSitterDeclarationExtractor()

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
                    add(TextField("name", sym.name, org.apache.lucene.document.Field.Store.YES))
                    add(TextField("fqn", sym.fqn, org.apache.lucene.document.Field.Store.YES))
                    add(StringField("fqn_raw", sym.fqn, org.apache.lucene.document.Field.Store.NO))
                    add(StringField("packageName", sym.packageName, org.apache.lucene.document.Field.Store.YES))
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

    override suspend fun listPackageContents(indexDir: Path, packageName: String): PackageContents? = withContext(Dispatchers.IO) {
        if (!indexDir.exists()) return@withContext null
        val reader = try {
            readerCache.get(indexDir)
        } catch (e: Exception) {
            return@withContext null
        }
        val searcher = IndexSearcher(reader)

        // 1. Direct symbols in this package
        val symbols = mutableSetOf<String>()
        val symbolsQuery = TermQuery(Term("packageName", packageName))
        val topSymbols = searcher.search(symbolsQuery, 5000)
        topSymbols.scoreDocs.forEach { hit ->
            val doc = searcher.storedFields().document(hit.doc, setOf("name"))
            doc.get("name")?.let { symbols.add(it) }
        }

        // 2. Sub-packages
        val subPackages = mutableSetOf<String>()
        val prefix = if (packageName.isEmpty()) "" else "$packageName."

        val terms = MultiTerms.getTerms(reader, "packageName")
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

        if (symbols.isEmpty() && subPackages.isEmpty()) return@withContext null

        PackageContents(symbols.sorted(), subPackages.sorted())
    }

    private fun transformGlobToRegex(glob: String): String {
        // First escape dots for regex (literal dot)
        val escaped = glob.replace(".", "\\.")

        // Use placeholders to avoid issues with subsequent replacements
        // ** matches zero or more segments (including dots)
        // * matches one or more characters within a segment (excluding dots)

        return escaped
            .replace("**", "DOUBLE_ASTERISK")
            .replace("*", "SINGLE_ASTERISK")
            .replace("\\.DOUBLE_ASTERISK\\.", "\\.(.*\\.)?")
            .replace("DOUBLE_ASTERISK\\.", "(.*\\.)?")
            .replace("\\.DOUBLE_ASTERISK", "(\\..*)?")
            .replace("DOUBLE_ASTERISK", ".*")
            .replace("SINGLE_ASTERISK", "[^.]+")
    }

    override suspend fun search(indexDir: Path, query: String, pagination: PaginationInput): SearchResponse<RelativeSearchResult> = withContext(Dispatchers.IO) {
        val (res, duration) = measureTimedValue {
            if (!indexDir.exists()) {
                throw IllegalStateException("Symbol index dir does not exist: $indexDir")
            }

            val reader = readerCache.get(indexDir)
            val searcher = IndexSearcher(reader)
            createAnalyzer().use { analyzer ->
                val q = try {
                    if ((query.contains("*") || query.contains("**")) && !query.contains(":") && !query.contains("\"")) {
                        // Simple glob-like query for FQN
                        val regex = transformGlobToRegex(query)
                        RegexpQuery(Term("fqn_raw", regex))
                    } else {
                        val parser = object : MultiFieldQueryParser(arrayOf("name", "fqn"), analyzer) {
                            init {
                                allowLeadingWildcard = true
                            }

                            override fun getWildcardQuery(field: String?, termStr: String?): org.apache.lucene.search.Query {
                                if (field == "fqn" && termStr != null && (termStr.contains(".") || termStr.contains("*"))) {
                                    return RegexpQuery(Term("fqn_raw", transformGlobToRegex(termStr)))
                                }
                                return super.getWildcardQuery(field, termStr)
                            }

                            override fun getFieldQuery(field: String?, queryText: String?, quoted: Boolean): org.apache.lucene.search.Query {
                                if (field == "fqn" && queryText != null && queryText.contains(".") && !quoted) {
                                    return RegexpQuery(Term("fqn_raw", transformGlobToRegex(queryText)))
                                }
                                return super.getFieldQuery(field, queryText, quoted)
                            }
                        }
                        parser.parse(query)
                    }
                } catch (e: Exception) {
                    return@measureTimedValue SearchResponse<RelativeSearchResult>(
                        emptyList(),
                        error = LuceneUtils.formatSyntaxError(e.message)
                    )
                }

                val topDocs = LuceneUtils.searchPaginated(searcher, q, pagination)
                val hits = topDocs.scoreDocs

                val matchedResults: List<RelativeSearchResult> = hits.drop(pagination.offset).take(pagination.limit).map { hit ->
                    val doc = searcher.storedFields().document(hit.doc)
                    RelativeSearchResult(
                        relativePath = doc.get("path"),
                        offset = doc.getField("offset").numericValue().toInt(),
                        line = doc.getField("line").numericValue().toInt(),
                        score = hit.score
                    )
                }.toList()

                SearchResponse<RelativeSearchResult>(matchedResults, interpretedQuery = q.toString())
            }
        }
        logger.info("Symbol search for \"$query\" (offset=${pagination.offset}, limit=${pagination.limit}) took $duration (${res.results.size} results)")
        return@withContext res
    }
}
