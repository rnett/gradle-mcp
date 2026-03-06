## 1. Setup and Reproducer Verification

- [x] 1.1 Verify that `FullTextSearchReproducerTest.kt` fails as expected with current code.

## 2. Core Implementation (Lucene Indexing and Searching)

- [x] 2.1 Update `FullTextSearch.kt` to define a new `CONTENTS_EXACT` field and its corresponding analyzer (Standard + LowerCase only).
- [x] 2.2 Update `FullTextSearch.index` to index file contents into both `CONTENTS` and `CONTENTS_EXACT` fields.
- [x] 2.3 Increment `FullTextSearch.indexVersion` to trigger re-indexing of cached dependency sources.
- [x] 2.4 Update `FullTextSearch.search` to use a `MultiFieldQueryParser` (or similar logic) to search both fields, with a significant boost (e.g., 5.0) for `CONTENTS_EXACT`.
- [x] 2.5 Implement a try-catch block for `QueryNodeException` in `FullTextSearch.search` and return a descriptive, actionable error message about Lucene syntax.
- [x] 2.6 Update `FullTextSearch.search` to return the Lucene `Query.toString()` representation along with search results.
- [x] 2.7 Update `formatSearchResults` in `DependencySourceTools.kt` to display the interpreted query at the top of the output.

## 3. Tool and Documentation Updates

- [x] 3.1 Update the tool description for `search_dependency_sources` in `DependencySourceTools.kt` to mention Lucene syntax and escaping for special characters.
- [x] 3.2 Update `skills/researching_gradle_internals/SKILL.md` to include a tip about escaping for literal searches with colons.
- [x] 3.3 Update `docs/tools/PROJECT_DEPENDENCY_SOURCE_TOOLS.md` with explicit instructions on Lucene query syntax.

## 4. Verification and Finalization

- [x] 4.1 Run `FullTextSearchReproducerTest.kt` and ensure all tests pass (including prioritization).
- [x] 4.2 Run existing tests in `FullTextSearchTest.kt` to ensure no regressions in fuzzy matching.
- [x] 4.3 Run `gradle check` to verify overall project health.
- [x] 4.4 Run `./gradlew :updateToolsList` to sync metadata changes.
