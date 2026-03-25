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
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexOptions
import org.apache.lucene.index.ReaderUtil
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.ScoreMode
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.measureTimedValue

/**
 * A full-text search provider using file-level indexing.
 * This enables multi-line phrase searches and reduces index bloat.
 */
object FullTextSearch : LuceneBaseSearchProvider() {
    override val logger = LoggerFactory.getLogger(FullTextSearch::class.java)
    override val name: String = "full-text"
    override val indexVersion: Int = 14

    private const val CONTENTS = "contents"
    private const val CONTENTS_EXACT = "contents_exact"
    private const val CODE = "contents_code"
    private const val PATH = "path"

    private const val BOOST_EXACT = 5.0f
    private const val BOOST_CODE = 10.0f

    internal const val v14IndexDirName = "lucene-full-text-index-v14"
    internal const val v12IndexDirName = v14IndexDirName // For compatibility with existing test references

    override fun resolveIndexDir(baseDir: Path): Path = baseDir.resolve(v14IndexDirName)

    private val reusableAnalyzer by lazy { createAnalyzer() }

    private val contentsFieldType by lazy {
        val type = FieldType(TextField.TYPE_STORED)
        type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        type.freeze()
        type
    }

    override fun createAnalyzer(): Analyzer {
        val splitAnalyzer = object : Analyzer() {
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

        return PerFieldAnalyzerWrapper(
            splitAnalyzer, mapOf(
                PATH to object : Analyzer() {
                    override fun createComponents(fieldName: String): TokenStreamComponents {
                        val source = KeywordTokenizer()
                        return TokenStreamComponents(source, LowerCaseFilter(source))
                    }
                },
                CONTENTS_EXACT to LuceneUtils.exactAnalyzer
            )
        )
    }

    override suspend fun search(indexDirs: List<Path>, query: String, pagination: PaginationInput): SearchResponse<RelativeSearchResult> = withContext(Dispatchers.IO) {
        val (response, duration) = measureTimedValue {
            val existingIndexDirs = indexDirs.filter { resolveIndexDir(it).exists() }
            if (existingIndexDirs.isEmpty()) {
                return@withContext SearchResponse<RelativeSearchResult>(emptyList(), error = "No Lucene index directories exist among the provided paths.")
            }

            withMultiReader(existingIndexDirs) { reader ->
                val indexSearcher = IndexSearcher(reader)
                val q = try {
                    LuceneUtils.parseBoostedQuery(
                        query = query,
                        fields = arrayOf(CONTENTS),
                        exactFields = arrayOf(CONTENTS_EXACT),
                        analyzer = reusableAnalyzer,
                        exactBoost = BOOST_EXACT,
                        extraBoosts = mapOf(arrayOf(CODE) to BOOST_CODE)
                    )
                } catch (e: Exception) {
                    return@withMultiReader SearchResponse<RelativeSearchResult>(emptyList(), error = LuceneUtils.formatSyntaxError(e.message))
                }

                // Request enough files to likely satisfy the limit after expanding matches.
                val topDocs = indexSearcher.search(q, pagination.offset + pagination.limit)
                val hits = topDocs.scoreDocs
                val stored = indexSearcher.storedFields()

                val results = mutableListOf<RelativeSearchResult>()
                var totalMatchesInRequestedFiles = 0

                val weight = indexSearcher.createWeight(indexSearcher.rewrite(q), ScoreMode.COMPLETE, 1.0f)
                val leaves = reader.leaves()

                for (hit in hits) {
                    val docId = hit.doc
                    val leafIndex = ReaderUtil.subIndex(docId, leaves)
                    val leafContext = leaves[leafIndex]
                    val localDocId = docId - leafContext.docBase

                    val matches = weight.matches(leafContext, localDocId)
                    if (matches != null) {
                        val doc = stored.document(docId)
                        val path = doc.get(PATH)
                        val content = doc.get(CONTENTS) ?: ""
                        val seenOffsets = mutableSetOf<Int>()

                        // Check both fields because some terms might be stripped from CODE
                        val contentsMatches = matches.getMatches(CONTENTS)
                        val codeMatches = matches.getMatches(CODE)

                        fun collectMatches(it: org.apache.lucene.search.MatchesIterator?, matchBoost: Float) {
                            if (it == null) return
                            while (it.next()) {
                                val start = it.startOffset()
                                if (seenOffsets.add(start)) {
                                    if (totalMatchesInRequestedFiles >= pagination.offset && results.size < pagination.limit) {
                                        if (start in 0..content.length) {
                                            // Calculate line number (1-based)
                                            val lineNum = content.substring(0, start).count { c -> c == '\n' } + 1
                                            results.add(
                                                RelativeSearchResult(
                                                    relativePath = path,
                                                    offset = start,
                                                    line = lineNum,
                                                    score = hit.score + matchBoost
                                                )
                                            )
                                        }
                                    }
                                    if (start in 0..content.length || start == -1) {
                                        // Still count invalid/unprocessed matches for total results consistency if needed, 
                                        // but for this task we just want to avoid the -1 result.
                                        if (start != -1) {
                                            totalMatchesInRequestedFiles++
                                        }
                                    }
                                }
                            }
                        }

                        collectMatches(codeMatches, 1000f)
                        collectMatches(contentsMatches, 0f)
                    }
                }

                results.sortByDescending { it.score }

                SearchResponse(results, interpretedQuery = q.toString(), hasMore = topDocs.totalHits.value > pagination.offset + pagination.limit)
            } ?: SearchResponse(emptyList(), error = "Failed to open index readers.")
        }
        logger.info("Full-text search for \"$query\" (offset=${pagination.offset}, limit=${pagination.limit}) took $duration (${response.results.size} results)")
        return@withContext response
    }

    override suspend fun newIndexer(outputDir: Path): Indexer = object : LuceneBaseIndexer(outputDir) {

        override suspend fun indexFile(path: String, content: String) {
            val ext = path.substringAfterLast('.', "")
            if (ext !in SearchProvider.SOURCE_EXTENSIONS) return
            if (content.isBlank()) return

            val doc = Document()
            doc.add(StringField(PATH, path, Field.Store.YES))
            doc.add(Field(CONTENTS, content, contentsFieldType))
            doc.add(Field(CONTENTS_EXACT, content, contentsFieldType))

            val codeContent = stripBoilerplate(content)
            doc.add(Field(CODE, codeContent, contentsFieldType))

            writer.addDocument(doc)
        }
    }

    private fun stripBoilerplate(content: String): String {
        val chars = content.toCharArray()
        var lineStart = 0
        while (lineStart < chars.size) {
            var lineEnd = -1
            for (i in lineStart until chars.size) {
                if (chars[i] == '\n') {
                    lineEnd = i
                    break
                }
            }
            if (lineEnd == -1) lineEnd = chars.size

            var firstNonSpace = lineStart
            while (firstNonSpace < lineEnd && chars[firstNonSpace].isWhitespace()) {
                firstNonSpace++
            }

            val isBoilerplate = if (firstNonSpace < lineEnd) {
                val remaining = lineEnd - firstNonSpace
                val isImport = remaining >= 7 && String(chars, firstNonSpace, 7) == "import "
                val isPackage = remaining >= 8 && String(chars, firstNonSpace, 8) == "package "
                isImport || isPackage
            } else false

            if (isBoilerplate) {
                for (i in lineStart until lineEnd) {
                    if (chars[i] != '\r' && chars[i] != '\n') {
                        chars[i] = ' '
                    }
                }
            }
            lineStart = lineEnd + 1
        }
        return String(chars)
    }
}
