package dev.rnett.gradle.mcp.gradle.dependencies.search

import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.FieldType
import org.apache.lucene.document.KeywordField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexOptions
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.ReaderUtil
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.ScoreMode
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

object FullTextSearch : SearchProvider {
    override val name: String = "full-text"

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

    internal const val v2IndexDirName = "lucene-full-text-index-v2"

    private val contentFieldType = FieldType().apply {
        setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        setTokenized(true)
        setStored(true)
        freeze()
    }

    private fun createAnalyzer(): Analyzer {
        return object : Analyzer() {
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
    }

    override suspend fun search(indexDir: Path, query: String): List<RelativeSearchResult> = withContext(Dispatchers.IO) {
        val idxDir = indexDir.resolve(v2IndexDirName)
        val reader = readerCache.get(idxDir)
        val indexSearcher = IndexSearcher(reader)
        createAnalyzer().use { analyzer ->
            val q = StandardQueryParser(analyzer).parse(query, CONTENTS)

            //TODO handle this somehow
            val results = indexSearcher.search(q, 1000)
            val weight = indexSearcher.createWeight(indexSearcher.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1.0f)
            val stored = indexSearcher.storedFields()

            val leaves = reader.leaves()

            return@withContext results.scoreDocs.flatMap { r ->
                val leafContext = leaves[ReaderUtil.subIndex(r.doc, leaves)]
                val localDocId = r.doc - leafContext.docBase
                val matches = weight.matches(leafContext, localDocId) ?: return@flatMap emptyList()
                val matchesIterator = matches.getMatches(CONTENTS) ?: return@flatMap emptyList()

                val doc = stored.document(r.doc)
                val path = doc.get(PATH)

                val results = mutableListOf<RelativeSearchResult>()
                while (matchesIterator.next()) {
                    results.add(RelativeSearchResult(path, offset = matchesIterator.startOffset(), score = r.score))
                }
                results
            }
        }
    }

    override suspend fun index(dependencyDir: Path, outputDir: Path) = withContext(Dispatchers.IO) {
        val indexDir = outputDir.resolve(v2IndexDirName)
        indexDir.createDirectories()

        val dir = FSDirectory.open(indexDir)
        createAnalyzer().use { analyzer ->
            val iwc = IndexWriterConfig(analyzer)
            iwc.openMode = IndexWriterConfig.OpenMode.CREATE

            iwc.ramBufferSizeMB = 100.0
            IndexWriter(dir, iwc).use { writer ->
                dependencyDir.walk().filter { !it.toFile().isDirectory }.forEach {
                    val doc = Document()
                    doc.add(KeywordField(PATH, it.relativeTo(dependencyDir).toString().replace('\\', '/'), Field.Store.YES))
                    doc.add(Field(CONTENTS, it.readText(), contentFieldType))

                    writer.addDocument(
                        doc
                    )
                }
                writer.forceMerge(1)
                writer.flush()
                writer.commit()
            }
            invalidateCache(indexDir)
        }
    }

    /**
     * Note: for [FullTextSearch], the relative path prefix in [indexDirs] is used to correct the paths in the merged index.
     * Merged results will have paths relative to the combined set.
     */
    override suspend fun mergeIndices(indexDirs: Map<Path, Path>, outputDir: Path) = withContext(Dispatchers.IO) {
        val indexDir = outputDir.resolve(v2IndexDirName)
        indexDir.createDirectories()

        val dir = FSDirectory.open(indexDir)
        createAnalyzer().use { analyzer ->
            val iwc = IndexWriterConfig(analyzer)
            iwc.openMode = IndexWriterConfig.OpenMode.CREATE

            IndexWriter(dir, iwc).use { writer ->
                indexDirs.forEach { (idxParentDir, relativePrefix) ->
                    val srcIndexDir = idxParentDir.resolve(v2IndexDirName)
                    DirectoryReader.open(FSDirectory.open(srcIndexDir)).use { reader ->
                        val storedFields = reader.storedFields()
                        for (i in 0 until reader.maxDoc()) {
                            val oldDoc = storedFields.document(i)
                            val oldPath = oldDoc.get(PATH)
                            val newPath = relativePrefix.resolve(oldPath).toString().replace('\\', '/')
                            val content = oldDoc.get(CONTENTS)

                            val newDoc = Document()
                            newDoc.add(KeywordField(PATH, newPath, Field.Store.YES))
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
}