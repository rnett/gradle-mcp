## 1. Symbol Search Indexing Enhancements

- [x] 1.1 Update `SymbolSearch.kt` to use the new case-sensitive field names: `name`, `fqn`, `packageName`.
- [x] 1.2 Implement a non-lowercasing analyzer for the `name` and `fqn` fields.
- [x] 1.3 Ensure `TreeSitterSymbolExtractor.kt` only extracts declarations as symbols.
- [x] 1.4 Update `copyDocument` in `SymbolSearch.kt` to handle the new case-sensitive fields and the `packageName` field.
- [x] 1.5 Increment `indexVersion` in `SymbolSearch.kt` to force re-indexing.

## 2. Symbol Search Query Enhancements

- [x] 2.1 Update `search` function in `SymbolSearch.kt` to support full Lucene query syntax.
- [x] 2.2 Implement glob-style wildcard transformation for FQN queries (`*` for one segment, `**` for multiple).
- [x] 2.3 Add unit tests for partial FQN search, universal case-sensitive matching, glob wildcards, and Lucene syntax in `SymbolSearchTest.kt`.

## 3. Package Exploration Implementation

- [x] 3.1 Implement the logic in `SymbolSearch.kt` to list sub-packages and symbols for a dot-separated package name by querying the index.
- [x] 3.2 Enhance `READ_DEPENDENCY_SOURCES` tool in `DependencySourceTools.kt` to detect dot-separated package paths and return their contents via the index-backed lookup.
- [x] 3.3 Add unit tests for index-backed package exploration in `SymbolSearchTest.kt`.

## 4. Tool and Skill Integration

- [x] 4.1 Update `DependencySourceTools.kt` metadata for `SEARCH_DEPENDENCY_SOURCES` and `READ_DEPENDENCY_SOURCES` with the new capabilities and field descriptions.
- [x] 4.2 Update `researching_gradle_internals/SKILL.md` with examples of partial FQN search, universal case-sensitive matching, and glob wildcards.
- [x] 4.3 Update `searching_dependency_sources/SKILL.md` with examples of index-backed package exploration via `READ_DEPENDENCY_SOURCES`.
- [x] 4.4 Run `./gradlew :updateToolsList` to update tool descriptions.

## 5. Verification and Cleanup

- [x] 5.1 Run all search-related tests to ensure no regressions.
- [x] 5.2 Perform manual verification of the new features.
- [x] 5.3 Consider if "symbol search" should be renamed to "declaration search" or similar.
- [x] 5.4 Update `roadmap.md` to mark the item as completed.
