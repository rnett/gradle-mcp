# Tasks: Optimized Indexing and Parallel Merging

- [x] Update `IndexService.indexFiles` signature and implementation to support `providersToIndex`.
- [x] Implement per-provider marker files in `IndexService`.
- [x] Parallelize index merging in `LuceneBaseSearchProvider` using `unorderedParallelForEach`.
- [x] Update `IndexService.mergeIndices` to support parallel progress reporting and provider filtering.
- [x] Refactor `SourcesService` and `SourcesProcessor` to pass `providersToIndex` through the call chain.
- [x] Update `DependencySourceTools` to only request relevant providers based on operation type.
- [x] Update MockK tests to handle new parameter signatures and matchers.
- [x] Verify parallel merging performance and progress stability.
