package dev.rnett.gradle.mcp.lucene

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
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
}
