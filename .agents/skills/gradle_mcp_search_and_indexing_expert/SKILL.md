---
name: gradle_mcp_search_and_indexing_expert
description: >-
  Deep technical guidance on Lucene-based search, Tree-sitter extraction, and performance optimization within the Gradle MCP project.
metadata:
  author: rnett
  version: "1.0"
---

# Skill: Gradle MCP Search and Indexing Expert

This skill provides deep technical guidance on the search and indexing infrastructure of the Gradle MCP project, specifically focusing on Lucene 10+ usage, Tree-sitter extraction, and performance optimizations.

## Search Conventions

### Virtual Multi-Index Search

- **MultiReader Composition**: Cross-dependency search is performed by composing per-dependency Lucene indices via `MultiReader` instead of physically merging them. Each dependency keeps an immutable index in CAS; a session view assembles
  the relevant subset on demand. Avoid introducing flows that rely on a single monolithic index.
- **Provider Boundaries**: Search providers (`FullTextSearch`, `DeclarationSearch`, `GlobSearch`) extend `LuceneBaseSearchProvider` and must remain independently indexable so they can participate in MultiReader-style aggregation.

### Lucene Field Management

- **Field Constants**: For Lucene-based search providers (e.g., `DeclarationSearch`), field names MUST be extracted to a nested `Fields` object. This ensures consistency and prevents typos across indexing and searching logic.
- **Lucene 10+ API**: Use `writer.docStats.numDocs` to retrieve the current document count from an `IndexWriter`.
- **Metadata Caching**: Always cache expensive metadata (like document counts) in a lightweight file (e.g., `.count`) within the index directory. This enables instantaneous preparation phases and provides immediate progress reporting
  feedback.

### Search Implementation

- **FQN Literal Search**: The `fqn` field (Fully Qualified Name) MUST be indexed as a `StringField` (non-analyzed) and queried using a `KeywordAnalyzer`. This ensures that dots and case are preserved, allowing for precise symbol discovery.
  Use wildcards (e.g., `fqn:*.MyClass`) for partial matching.
- **Regex Search**: `DeclarationSearch` supports full string regex queries on the FQN field when the query is wrapped in `/` (e.g., `/.*MyClass/`). This should be preferred for complex, precise symbol discovery.
- **Search Error Handling**: `SourcesService.search` and `IndexService.search` MUST return a `SearchResponse` with an `error` string instead of throwing an `IllegalStateException` when an index is missing. This prevents unexpected crashes
  in tool handlers and allows for graceful error reporting.
- **Targeted Indexing**: `SourcesService.resolveAndProcessAllSources` (and related methods) require an explicit `providerToIndex: SearchProvider` to be passed when `index = true`. This ensures that indexing is targeted and efficient.
  Callers must specify which provider's index they intend to use.
- **Index Versioning**: When updating Lucene index versions and directory constants in search providers (like `FullTextSearch`), ALWAYS grep the codebase for the old directory constant name (e.g., `v6IndexDirName`) to ensure hardcoded
  references in unit tests are updated.

### Advanced Search Features

- **Boilerplate Scoring Penalties**: When implementing boilerplate/structural scoring penalties in Lucene where multi-line phrase search support requires exact offset preservation, use parallel fields (e.g., `contents` and `contents_code`).
  Replace the penalized lines in the specialized field with exact-length whitespace, and search across both using `parseBoostedQuery`.
- **Match Expansion**: When using file-level indexing in Lucene, always use the `Matches` API to iterate through multiple hits within a single document to ensure granular match reporting (offsets and line numbers).
- **Match Deduplication**: Always deduplicate match offsets within a single document when searching across multiple Lucene fields (e.g., `CONTENTS` and `CODE`) using a `Set`. This prevents duplicate results for the same term matching in
  different analyzer fields.

## Extraction and Indexing

- **Object Pooling**: For heavy, non-thread-safe objects like `TreeSitterDeclarationExtractor`, prefer a `ConcurrentLinkedQueue`-based pool over `ThreadLocal` when using Kotlin Coroutines. This ensures better resource management and
  predictability across diverse threading environments.
- **Fail Fast**: ALWAYS propagate exceptions in indexing, extraction, and search operations. NEVER swallow errors silently. "Well-documented failures" are preferred over silent partial successes.
- **Extraction Tests**: When writing tests for source extraction, avoid using empty or invalid ZIP files. Since extraction failures are no longer swallowed, these will now cause `ZipException` and fail the test.

## Examples

### Implementing a new Lucene field

1. Add the field name to the `Fields` object in the search provider.
2. Update the indexing logic to populate the new field.
3. Update the search logic to query the new field if necessary.
4. If the field needs scoring penalties, use the parallel field strategy.

### Debugging a search crash

1. Check if the index exists using `IndexService.search`.
2. Verify that the tool handler catches the `SearchResponse` error.
3. Ensure no `IllegalStateException` is thrown from the service layer.
