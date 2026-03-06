package dev.rnett.gradle.mcp.lucene

import dev.rnett.gradle.mcp.tools.PaginationInput
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path

object LuceneUtils {

    const val SYNTAX_ERROR_HELP = "Special characters like ':', '=', '+', '-', '*', '/' must be escaped with a backslash (e.g., '\\:') or enclosed in quotes for literal searches."

    fun formatSyntaxError(message: String?) = "Invalid Lucene query syntax: $message. $SYNTAX_ERROR_HELP"

    val exactAnalyzer = object : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val source = StandardTokenizer()
            val filter = LowerCaseFilter(source)
            return TokenStreamComponents(source, filter)
        }
    }

    fun createBoostedAnalyzer(exactFields: Set<String>, otherAnalyzers: Map<String, Analyzer> = emptyMap()): Analyzer {
        val standard = StandardAnalyzer()
        val map = otherAnalyzers.toMutableMap()
        exactFields.forEach { map[it] = exactAnalyzer }
        return PerFieldAnalyzerWrapper(standard, map)
    }

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
        return indexSearcher.search(query, offset + limit)
    }

    /**
     * Parses a query against both standard and exact fields, combining them with boosting.
     */
    fun parseBoostedQuery(
        query: String,
        fields: Array<String>,
        exactFields: Array<String>,
        analyzer: Analyzer,
        exactBoost: Float = 5.0f,
        extraBoosts: Map<Array<String>, Float> = emptyMap()
    ): Query {
        val standardParser = MultiFieldQueryParser(fields, analyzer)
        standardParser.allowLeadingWildcard = true
        val standardQuery = standardParser.parse(query)

        val exactParser = MultiFieldQueryParser(exactFields, analyzer)
        exactParser.allowLeadingWildcard = true
        val exactQuery = exactParser.parse(query)

        return BooleanQuery.Builder().apply {
            add(standardQuery, BooleanClause.Occur.SHOULD)
            add(BoostQuery(exactQuery, exactBoost), BooleanClause.Occur.SHOULD)
            extraBoosts.forEach { (extraFields, boost) ->
                val extraParser = MultiFieldQueryParser(extraFields, analyzer)
                extraParser.allowLeadingWildcard = true
                add(BoostQuery(extraParser.parse(query), boost), BooleanClause.Occur.SHOULD)
            }
        }.build()
    }
}

fun Document.addTextAndExact(name: String, value: String, store: Field.Store = Field.Store.YES) {
    add(TextField(name, value, store))
    add(TextField("${name}_exact", value, store))
}
