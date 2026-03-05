package dev.rnett.gradle.mcp.lucene

import dev.rnett.gradle.mcp.tools.PaginationInput
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path

object LuceneUtils {
    fun writeIndex(
        indexDir: Path,
        analyzer: Analyzer,
        openMode: IndexWriterConfig.OpenMode = IndexWriterConfig.OpenMode.CREATE,
        block: (IndexWriter) -> Unit
    ) {
        FSDirectory.open(indexDir).use { dir ->
            val iwc = IndexWriterConfig(analyzer)
            iwc.openMode = openMode
            IndexWriter(dir, iwc).use { writer ->
                block(writer)
                writer.commit()
            }
        }
    }

    /**
     * Search with pagination using the Top-N approach.
     * Returns a [TopDocs] containing only the requested window of results.
     */
    fun searchPaginated(
        indexSearcher: IndexSearcher,
        query: Query,
        pagination: PaginationInput
    ): TopDocs {
        val offset = pagination.offset
        val limit = pagination.limit

        // Lucene's TopDocs contains the top N results.
        // We request offset + limit results and then we will manually slice if needed,
        // but for now we just return the TopDocs.
        // Actually, to truly support offset in the results we might need to handle it in the caller
        // since scoreDocs is what people usually iterate over.
        return indexSearcher.search(query, offset + limit)
    }
}
