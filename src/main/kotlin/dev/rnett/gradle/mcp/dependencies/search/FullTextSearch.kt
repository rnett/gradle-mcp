package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.lucene.LuceneUtils
import dev.rnett.gradle.mcp.tools.PaginationInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.core.KeywordTokenizer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.FieldType
import org.apache.lucene.index.IndexOptions
import org.apache.lucene.index.ReaderUtil
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.ScoreMode
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.measureTimedValue

object FullTextSearch : LuceneBaseSearchProvider() {
    override val logger = LoggerFactory.getLogger(FullTextSearch::class.java)
    override val name: String = "full-text"
    override val indexVersion: Int = 4

    private const val CONTENTS = "contents"
    private const val CONTENTS_EXACT = "contents_exact"
    private const val PATH = "path"

    internal const val v4IndexDirName = "lucene-full-text-index-v4"

    override fun resolveIndexDir(baseDir: Path): Path = baseDir.resolve(v4IndexDirName)

    private val contentFieldType = FieldType().apply {
        setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        setTokenized(true)
        setStored(true)
        freeze()
    }

    private val pathFieldType = FieldType().apply {
        setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
        setTokenized(true)
        setStored(true)
        setOmitNorms(true)
        freeze()
    }

    override fun createAnalyzer(): Analyzer {
        val standardWithWordDelimiter = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source = StandardTokenizer()
                var filter: TokenStream = WordDelimiterGraphFilter(
                    source,
                    WordDelimiterGraphFilter.GENERATE_WORD_PARTS or
                            WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS or
                            WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE or
                            WordDelimiterGraphFilter.SPLIT_ON_NUMERICS or
                            WordDelimiterGraphFilter.STEM_ENGLISH_POSSESSIVE or
                            WordDelimiterGraphFilter.PRESERVE_ORIGINAL,
                    null
                )
                filter = LowerCaseFilter(filter)
                return TokenStreamComponents(source, filter)
            }
        }

        val keywordLowercaseAnalyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source = KeywordTokenizer()
                val filter = LowerCaseFilter(source)
                return TokenStreamComponents(source, filter)
            }
        }

        return PerFieldAnalyzerWrapper(
            standardWithWordDelimiter, mapOf(
                PATH to keywordLowercaseAnalyzer,
                CONTENTS_EXACT to LuceneUtils.exactAnalyzer
            )
        )
    }

    override fun copyDocument(oldDoc: Document, newPath: String): Document {
        val newDoc = Document()
        newDoc.add(Field(PATH, newPath, pathFieldType))
        newDoc.addContents(oldDoc.get(CONTENTS))
        return newDoc
    }

    private fun Document.addContents(content: String) {
        add(Field(CONTENTS, content, contentFieldType))
        add(Field(CONTENTS_EXACT, content, contentFieldType))
    }

    override suspend fun search(indexDir: Path, query: String, pagination: PaginationInput): SearchResponse<RelativeSearchResult> = withContext(Dispatchers.IO) {
        val (response, duration) = measureTimedValue {
            val idxDir = indexDir.resolve(v4IndexDirName)
            if (!idxDir.exists()) {
                throw IllegalStateException("Lucene index directory does not exist: $idxDir")
            }

            val reader = try {
                readerCache.get(idxDir)
            } catch (e: Exception) {
                val cause = e.cause
                if (cause is org.apache.lucene.index.IndexNotFoundException) {
                    throw IllegalStateException("Lucene index not found in $idxDir", cause)
                }
                throw e
            }
            val indexSearcher = IndexSearcher(reader)
            createAnalyzer().use { analyzer ->
                val q = try {
                    LuceneUtils.parseBoostedQuery(query, arrayOf(CONTENTS), arrayOf(CONTENTS_EXACT), analyzer)
                } catch (e: Exception) {
                    return@measureTimedValue SearchResponse(
                        emptyList(),
                        error = LuceneUtils.formatSyntaxError(e.message)
                    )
                }

                val topDocs = LuceneUtils.searchPaginated(indexSearcher, q, pagination)
                val weight = indexSearcher.createWeight(indexSearcher.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1.0f)
                val stored = indexSearcher.storedFields()

                val leaves = reader.leaves()

                val offset = pagination.offset
                val limit = pagination.limit

                val pagedScoreDocs = topDocs.scoreDocs.drop(offset).take(limit)

                val results = pagedScoreDocs.flatMap { r ->
                    val leafContext = leaves[ReaderUtil.subIndex(r.doc, leaves)]
                    val localDocId = r.doc - leafContext.docBase
                    val matches = weight.matches(leafContext, localDocId) ?: return@flatMap emptyList()

                    val doc = stored.document(r.doc)
                    val path = doc.get(PATH)

                    val contentsMatches = matches.getMatches(CONTENTS)
                    val exactMatches = matches.getMatches(CONTENTS_EXACT)

                    val matchResults = mutableListOf<RelativeSearchResult>()

                    if (contentsMatches != null) {
                        while (contentsMatches.next()) {
                            matchResults.add(RelativeSearchResult(path, offset = contentsMatches.startOffset(), score = r.score))
                        }
                    }

                    if (exactMatches != null) {
                        while (exactMatches.next()) {
                            // Avoid duplicates if both fields match at the same offset
                            if (matchResults.none { it.offset == exactMatches.startOffset() }) {
                                matchResults.add(RelativeSearchResult(path, offset = exactMatches.startOffset(), score = r.score))
                            }
                        }
                    }

                    if (matchResults.isEmpty()) {
                        listOf(RelativeSearchResult(path, offset = 0, line = null, score = r.score, skipBoilerplate = true))
                    } else {
                        matchResults
                    }
                }
                SearchResponse(results, interpretedQuery = q.toString())
            }
        }
        val res = response
        logger.info("Full-text search for \"$query\" (offset=${pagination.offset}, limit=${pagination.limit}) took $duration (${res.results.size} results)")
        return@withContext res
    }

    override suspend fun newIndexer(outputDir: Path): Indexer = object : LuceneBaseIndexer(outputDir) {
        override val doForceMerge = true

        override suspend fun indexFile(path: String, content: String) {
            val ext = path.substringAfterLast('.', "")
            if (ext !in SearchProvider.SOURCE_EXTENSIONS) return

            val doc = Document()
            doc.add(Field(PATH, path, pathFieldType))
            doc.addContents(content)
            writer.addDocument(doc)
        }
    }


}
