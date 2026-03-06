package dev.rnett.gradle.mcp.gradle.dependencies.search

import com.github.benmanes.caffeine.cache.Caffeine
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
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexOptions
import org.apache.lucene.index.ReaderUtil
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.ScoreMode
import org.apache.lucene.store.FSDirectory
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

object FullTextSearch : SearchProvider {
    private val LOGGER = LoggerFactory.getLogger(FullTextSearch::class.java)
    override val name: String = "full-text"
    override val indexVersion: Int = 4

    private const val CONTENTS = "contents"
    private const val CONTENTS_EXACT = "contents_exact"
    private const val PATH = "path"

    private val readerCache = Caffeine.newBuilder()
        .maximumSize(10)
        .expireAfterAccess(30, java.util.concurrent.TimeUnit.MINUTES)
        .removalListener<Path, DirectoryReader> { _, reader, _ ->
            reader?.close()
        }
        .build<Path, DirectoryReader> { path ->
            DirectoryReader.open(FSDirectory.open(path))
        }

    fun invalidateCache(path: Path) {
        readerCache.invalidate(path)
    }

    internal const val v4IndexDirName = "lucene-full-text-index-v4"

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

    private fun createAnalyzer(): Analyzer {
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
        LOGGER.info("Full-text search for \"$query\" (offset=${pagination.offset}, limit=${pagination.limit}) took $duration (${res.results.size} results)")
        return@withContext res
    }

    override suspend fun index(dependencyDir: Path, outputDir: Path) = withContext(Dispatchers.IO) {
        LOGGER.info("Starting full-text indexing for $dependencyDir")
        val (fileCount, duration) = measureTimedValue {
            val indexDir = outputDir.resolve(v4IndexDirName)
            indexDir.createDirectories()

            createAnalyzer().use { analyzer ->
                var count = 0
                LuceneUtils.writeIndex(indexDir, analyzer) { writer ->
                    dependencyDir.walk()
                        .filter { it.isRegularFile() && it.extension in SearchProvider.SOURCE_EXTENSIONS }
                        .forEach {
                            val doc = Document()
                            val path = it.relativeTo(dependencyDir).toString().replace('\\', '/')
                            val content = it.readText()
                            doc.add(Field(PATH, path, pathFieldType))
                            doc.addContents(content)

                            writer.addDocument(doc)
                            count++
                        }
                    writer.forceMerge(1)
                }
                invalidateCache(indexDir)
                count
            }
        }
        LOGGER.info("Full-text indexing for $dependencyDir took $duration ($fileCount files)")
    }

    override suspend fun mergeIndices(indexDirs: Map<Path, Path>, outputDir: Path) = withContext(Dispatchers.IO) {
        val duration = measureTime {
            val indexDir = outputDir.resolve(v4IndexDirName)
            indexDir.createDirectories()

            createAnalyzer().use { analyzer ->
                LuceneUtils.writeIndex(indexDir, analyzer) { writer ->
                    indexDirs.forEach { (idxParentDir, relativePrefix) ->
                        val srcIndexDir = idxParentDir.resolve(v4IndexDirName)
                        DirectoryReader.open(FSDirectory.open(srcIndexDir)).use { reader ->
                            val storedFields = reader.storedFields()
                            for (i in 0 until reader.maxDoc()) {
                                val oldDoc = storedFields.document(i)
                                val oldPath = oldDoc.get(PATH)
                                val newPath = relativePrefix.resolve(oldPath).toString().replace('\\', '/')
                                val content = oldDoc.get(CONTENTS)

                                val newDoc = Document()
                                newDoc.add(Field(PATH, newPath, pathFieldType))
                                newDoc.addContents(content)
                                writer.addDocument(newDoc)
                            }
                        }
                    }
                    writer.forceMerge(1)
                }
                invalidateCache(indexDir)
            }
        }
        LOGGER.info("Full-text index merging took $duration (${indexDirs.size} indices)")
    }
}
