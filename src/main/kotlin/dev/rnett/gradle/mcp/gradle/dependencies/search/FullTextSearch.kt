package dev.rnett.gradle.mcp.gradle.dependencies.search

import com.github.benmanes.caffeine.cache.Caffeine
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
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.ReaderUtil
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser
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
    override val indexVersion: Int = 3

    private const val CONTENTS = "contents"
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

    internal const val v3IndexDirName = "lucene-full-text-index-v3"

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
        val standard = object : Analyzer() {
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

        return PerFieldAnalyzerWrapper(standard, mapOf(PATH to keywordLowercaseAnalyzer))
    }

    override suspend fun search(indexDir: Path, query: String): List<RelativeSearchResult> = withContext(Dispatchers.IO) {
        val (results, duration) = measureTimedValue {
            val idxDir = indexDir.resolve(v3IndexDirName)
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
                val parser = StandardQueryParser(analyzer)
                parser.allowLeadingWildcard = true
                val q = parser.parse(query, CONTENTS)

                //TODO handle this somehow
                val results = indexSearcher.search(q, 1000)
                val weight = indexSearcher.createWeight(indexSearcher.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1.0f)
                val stored = indexSearcher.storedFields()

                val leaves = reader.leaves()

                results.scoreDocs.flatMap { r ->
                    val leafContext = leaves[ReaderUtil.subIndex(r.doc, leaves)]
                    val localDocId = r.doc - leafContext.docBase
                    val matches = weight.matches(leafContext, localDocId) ?: return@flatMap emptyList()

                    val doc = stored.document(r.doc)
                    val path = doc.get(PATH)

                    val contentsMatches = matches.getMatches(CONTENTS)

                    if (contentsMatches != null) {
                        val results = mutableListOf<RelativeSearchResult>()
                        while (contentsMatches.next()) {
                            results.add(RelativeSearchResult(path, offset = contentsMatches.startOffset(), score = r.score))
                        }
                        results
                    } else {
                        listOf(RelativeSearchResult(path, offset = 0, line = null, score = r.score, skipBoilerplate = true))
                    }
                }
            }
        }
        LOGGER.info("Full-text search for \"$query\" took $duration (${results.size} results)")
        return@withContext results
    }

    override suspend fun index(dependencyDir: Path, outputDir: Path) = withContext(Dispatchers.IO) {
        LOGGER.info("Starting full-text indexing for $dependencyDir")
        val (fileCount, duration) = measureTimedValue {
            val indexDir = outputDir.resolve(v3IndexDirName)
            indexDir.createDirectories()

            val dir = FSDirectory.open(indexDir)
            createAnalyzer().use { analyzer ->
                val iwc = IndexWriterConfig(analyzer)
                iwc.openMode = IndexWriterConfig.OpenMode.CREATE

                iwc.ramBufferSizeMB = 100.0
                var count = 0
                IndexWriter(dir, iwc).use { writer ->
                    dependencyDir.walk()
                        .filter { it.isRegularFile() && it.extension in SearchProvider.SOURCE_EXTENSIONS }
                        .forEach {
                            val doc = Document()
                            // KeywordField is indexed as a single token, but NOT lowercased unless we do it here.
                            // However, we want to store the original case for file retrieval.
                            // Our PerFieldAnalyzerWrapper with keywordLowercaseAnalyzer will handle case-insensitivity during search.
                            val path = it.relativeTo(dependencyDir).toString().replace('\\', '/')
                            doc.add(Field(PATH, path, pathFieldType))
                            doc.add(Field(CONTENTS, it.readText(), contentFieldType))

                            writer.addDocument(
                                doc
                            )
                            count++
                        }
                    writer.forceMerge(1)
                    writer.flush()
                    writer.commit()
                }
                invalidateCache(indexDir)
                count
            }
        }
        LOGGER.info("Full-text indexing for $dependencyDir took $duration ($fileCount files)")
    }

    /**
     * Note: for [FullTextSearch], the relative path prefix in [indexDirs] is used to correct the paths in the merged index.
     * Merged results will have paths relative to the combined set.
     */
    override suspend fun mergeIndices(indexDirs: Map<Path, Path>, outputDir: Path) = withContext(Dispatchers.IO) {
        val duration = measureTime {
            val indexDir = outputDir.resolve(v3IndexDirName)
            indexDir.createDirectories()

            val dir = FSDirectory.open(indexDir)
            createAnalyzer().use { analyzer ->
                val iwc = IndexWriterConfig(analyzer)
                iwc.openMode = IndexWriterConfig.OpenMode.CREATE

                IndexWriter(dir, iwc).use { writer ->
                    indexDirs.forEach { (idxParentDir, relativePrefix) ->
                        val srcIndexDir = idxParentDir.resolve(v3IndexDirName)
                        DirectoryReader.open(FSDirectory.open(srcIndexDir)).use { reader ->
                            val storedFields = reader.storedFields()
                            for (i in 0 until reader.maxDoc()) {
                                val oldDoc = storedFields.document(i)
                                val oldPath = oldDoc.get(PATH)
                                val newPath = relativePrefix.resolve(oldPath).toString().replace('\\', '/')
                                val content = oldDoc.get(CONTENTS)

                                val newDoc = Document()
                                newDoc.add(Field(PATH, newPath, pathFieldType))
                                newDoc.add(Field(CONTENTS, content, contentFieldType))
                                writer.addDocument(newDoc)
                            }
                        }
                    }
                    writer.forceMerge(1)
                    writer.flush()
                    writer.commit()
                }
                invalidateCache(indexDir)
            }
        }
        LOGGER.info("Full-text index merging took $duration (${indexDirs.size} indices)")
    }
}