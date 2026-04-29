# Capability: multi-reader-search

## Purpose

TBD

## ADDED Requirements

### Requirement: Virtual Multi-Reader Searching

The system SHALL support virtual searching across multiple dependency indices using Lucene `MultiReader`.

- It MUST load the project manifest and open separate `IndexReader`s for each CAS directory.
- It SHALL wrap the readers in a single `MultiReader` for a unified search result set.
- It MUST eliminate the need for physical merging of Lucene indices into a project-level directory.
- It SHALL support dynamic result filtering based on the artifact's role in the current project (e.g., `isDiff` filtering for KMP).

### Requirement: Search Result Filtering

Search providers SHALL use project-level metadata to filter results from individual dependency indices.

- Each dependency index document SHALL contain a `sourceHash` field.
- The searcher SHALL accept a map of index directories to "diff-only" boolean flags.
- **IF** an index is marked as "diff-only", the searcher MUST add a Lucene `BooleanQuery` that restricts results from that artifact's hash to documents where `isDiff = true`.
- This mechanism SHALL be used to prevent duplicate results in projects with overlapping source sets (e.g., KMP).

#### Scenario: Searching multiple dependencies

- **WHEN** a search query is executed
- **THEN** the system combines individual results from all indexed CAS dependencies

### Requirement: Extraction & Indexing Standards

The system SHALL ensure performant and reliable extraction of sources and indices.

- **Object Pooling**: For heavy, non-thread-safe objects (e.g., `TreeSitterDeclarationExtractor`), the system SHALL use a queue-based object pool (e.g., `ConcurrentLinkedQueue`) to ensure safe reuse across coroutines.
- **Fail Fast**: Indexing and extraction operations MUST NOT swallow errors. Exceptions SHALL be propagated to allow for authoritative failure detection and repair.
- **Search Error Handling**: Search services MUST return a structured `SearchResponse` containing an error message instead of throwing exceptions when indices are missing or corrupt.
- **Index Versioning**: When upgrading Lucene index formats, developers MUST ensure all hardcoded directory references (including those in tests) are updated to the new version constant.

### Requirement: Search Implementation Standards

Lucene-based search providers SHALL follow project-standard implementation patterns.

- **Field Constants**: Field names MUST be extracted to a nested `Fields` object within the search provider to ensure consistency.
- **FQN Literal Search**: The `fqn` field (Fully Qualified Name) MUST be indexed as a `StringField` (non-analyzed) and queried using a `KeywordAnalyzer` to preserve dots and case.
- **Regex Support**: Providers SHOULD support regex queries on the FQN field when the query is delimited by `/`.
- **Match Expansion & Deduplication**: Providers MUST use the Lucene `Matches` API to iterate through hits within a document and SHALL deduplicate match offsets when searching across multiple fields (e.g., `CONTENTS` and `CODE`) using a
  `Set`.
- **Search Metadata**: Providers SHOULD cache expensive metadata (like document counts) in lightweight files (e.g., `.count`) within the index directory for efficient progress reporting.

Search tools and file readers SHALL access CAS directories without acquiring any shared or exclusive filesystem locks.

- CAS directories MUST be treated as immutable once they are moved into the cache.

#### Scenario: High-concurrency search

- **WHEN** multiple tool calls search the same project simultaneously
- **THEN** they all read the manifest and perform searching with zero lock contention
