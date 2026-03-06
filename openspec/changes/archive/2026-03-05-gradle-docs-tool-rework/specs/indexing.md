# Spec: Unified Lucene Indexing

## Dependencies

```toml
# gradle/libs.versions.toml additions
lucene = "9.12.0"

[libraries]
lucene-core = { module = "org.apache.lucene:lucene-core", version.ref = "lucene" }
lucene-analysis-common = { module = "org.apache.lucene:lucene-analysis-common", version.ref = "lucene" }
lucene-queryparser = { module = "org.apache.lucene:lucene-queryparser", version.ref = "lucene" }
```

## LuceneIndexService

### Interface

```kotlin
interface LuceneIndexService {
    /** Ensures the index for a version is built. */
    suspend fun ensureIndexed(version: String)

    /**
     * Searches the index. The query is passed directly to Lucene's QueryParser.
     * Callers embed tag:X in the query string to filter by section; no separate kind param.
     */
    suspend fun search(query: String, version: String, maxResults: Int = 20): List<DocsSearchResult>
}
```

### Index Location

`{cacheDir}/reading_gradle_docs/{version}/index/`

---

## Document Schema

Each indexed document represents one file.

| Field name | Type                      | Stored | Indexed      | Notes                                                                                                                                                                                |
|------------|---------------------------|--------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`tag`**  | StringField (multi-value) | yes    | not analyzed | Primary: `userguide`, `dsl`, `javadoc`, `samples`, `release-notes`. Sub-tags added as additional values (e.g. `best-practices`). Queryable as `tag:userguide`, `tag:best-practices`. |
| `path`     | StringField               | yes    | not analyzed | Path relative to `converted/` root (e.g. `userguide/foo.md`) — usable directly as the `path` tool argument                                                                           |
| `title`    | TextField                 | yes    | analyzed     | First `# Heading` in the Markdown content, or filename                                                                                                                               |
| `body`     | TextField                 | no     | analyzed     | Full text content                                                                                                                                                                    |
| `snippet`  | StoredField               | yes    | not analyzed | (Not used for display; see Search Algorithm for dynamic snippets)                                                                                                                    |

**Analyzer**: `StandardAnalyzer`.

The `tag` field uses multiple `StringField` values per document (Lucene supports this).
A document can have both `tag=userguide` and `tag=best-practices`. `QueryParser` handles
`tag:best-practices` as an exact term match across all `tag` field values.
Both Groovy and Kotlin DSL content are tagged `dsl`.

---

## Index Build Algorithm

```
ensureIndexed(version):
  1. ensureProcessed(version)   // download + extract+convert in one pass (see distribution.md)

  2. if indexDir/.done exists: return   // called inside version-level lock, but check anyway
  3. (no separate lock — within the version-level pipeline lock; see distribution.md)
  4. open FSDirectory at indexDir
  5. create IndexWriter(dir, IndexWriterConfig(StandardAnalyzer()))
  6. walk converted/ recursively:
       for each file:
         a. relPath = convertedRoot.relativize(file)
         b. primaryTag = first path component (e.g. "userguide"; "kotlin-dsl" → "dsl")
         c. subTags = additional tags based on filename patterns (see Sub-Tag Rules below)
         d. path = relPath.toString()
         e. read file content as text
         f. title = first `# Heading` line, or filename without extension
         g. body = full content
         h. addDocument with one StringField("tag", primaryTag) per tag value:
            addField("tag", primaryTag)
            for each subTag: addField("tag", subTag)
  7. indexWriter.commit(); indexWriter.close()
  8. write index/.done marker
```

Note: `kotlin-dsl` content is stored with `tag=dsl` so it is included in `tag:dsl` queries.

### Sub-Tag Rules

Sub-tags are additional `tag` values applied based on filename patterns.
A document with a sub-tag also always has its primary section tag.

| Sub-tag          | Applies to  | Pattern                                 |
|------------------|-------------|-----------------------------------------|
| `best-practices` | `userguide` | filename matches `best_practices*.html` |

Additional sub-tags should be added here as new groupings are identified.

---

## Search Algorithm

```
search(query, version, maxResults):
  1. ensureIndexed(version)
  2. open DirectoryReader on indexDir
  3. create IndexSearcher
  4. parse: QueryParser("body", StandardAnalyzer()).parse(query)
     — tag:X in the query is handled natively by QueryParser as a field filter
  5. searcher.search(parsedQuery, maxResults) → TopDocs
  6. **Generate Dynamic Snippets**:
     - Use `UnifiedHighlighter` on the `body` field with the `parsedQuery`.
     - Request up to 2 fragments of 100 characters each.
     - Fallback to the first 500 characters of the document if no fragments are found.
  7. convert hits to DocsSearchResult list
```

### Query Syntax Fallback

If `QueryParser` throws (e.g. unmatched brackets), fall back to a `TermQuery` on `body`
with the raw query string. Do not propagate the parse error to the caller.

---

## Concurrency

- **Index build**: coordinated by the version-level file lock in `distribution.md`.
- **Searches**: thread-safe; `DirectoryReader` opened read-only. Lucene `FSDirectory`
  supports concurrent readers from multiple processes. Close `IndexSearcher` after use
  (or manage via `SearcherManager`).
