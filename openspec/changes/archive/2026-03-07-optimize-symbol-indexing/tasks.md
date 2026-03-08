## 1. Tree-sitter Integration

- [x] 1.1 Add `io.github.bonede:tree-sitter`, `tree-sitter-java`, and `tree-sitter-kotlin` to `libs.versions.toml` and project dependencies.
- [x] 1.2 Implement a prototype `TreeSitterSymbolExtractor` that uses `.scm` queries to find classes, methods, and properties in Java and Kotlin.
- [x] 1.3 Write unit tests for `TreeSitterSymbolExtractor` using realistic, multi-line Java and Kotlin source files (including `context` receivers, nested objects, and FQN tracking).
- [x] 1.4 *(Task Removed: Groovy support dropped)*

## 2. Lucene-based Symbol Index Refactoring

- [x] 2.1 Update `SymbolSearch` to use the `TreeSitterSymbolExtractor` and Lucene indexing logic. Ignore `.groovy` files.
- [x] 2.2 Implement payload storage for `line` and `offset` metadata.
- [x] 2.3 Implement Lucene-based search with support for FQN and simple name queries.
- [x] 2.4 Implement efficient index merging using `IndexWriter.addIndexes`.
- [x] 2.5 Update `SymbolSearch.indexVersion` to trigger re-indexing.

## 3. Streaming and Parallelization

- [x] 3.1 Refactor `DefaultSourcesService.extractSources` to stream from `ZipInputStream` directly into `IndexWriter`.
- [x] 3.2 Parallelize dependency processing using `coroutineScope` and `async`.
- [x] 3.3 Ensure thread-safe index writing and cache management.

## 4. Verification and Benchmarking

- [x] 4.1 Run full integration tests on large dependency sets (e.g., Gradle sources).
- [x] 4.2 Benchmark search latency and indexing speed against the old implementation.
- [ ] 4.3 Add support for browsing package symbols via `read_dependency_sources`.
