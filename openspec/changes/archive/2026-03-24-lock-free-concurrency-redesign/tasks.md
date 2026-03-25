## 1. Concurrency Primitives & Data Models

- [x] 1.1 Refactor `FileLockManager` to simplify `withLock` and remove re-entrancy/HeldLocks logic
- [x] 1.2 Implement `FileUtils.atomicMoveIfAbsent` and junction/symlink utilities
- [x] 1.3 Create `AdvisoryLock` optimization in `FileLockManager`
- [x] 1.4 Update `SourcesDir` and `MergedSourcesDir` models to reflect the new layout

## 2. Content-Addressable Storage (CAS) Implementation

- [x] 2.1 Implement `calculateHash` for GAV dependencies in `SourceStorageService`
- [x] 2.2 Refactor `extractSources` to use temporary directories and atomic CAS moves
- [x] 2.3 Refactor `DefaultIndexService.indexFiles` to use CAS paths and immutable markers
- [x] 2.4 Implement `waitForCAS` polling for parallel extraction coordination

## 3. Virtual Multi-Reader Searching

- [x] 3.1 Abolish physical index merging in `SearchProvider` and `IndexService`
- [x] 3.2 Implement `ProjectManifest` model and `writeManifest` in `SourceStorageService`
- [x] 3.3 Implement `MultiReader` search in `LuceneBaseSearchProvider` and `DefaultIndexService`
- [x] 3.4 Update `GlobSearch` to support multi-index searching via path concatenation

## 4. Service Orchestration & Tool Handlers

- [x] 4.1 Refactor `DefaultSourcesService` to use the new resolution -> view creation pipeline
- [x] 4.2 Update tool handlers to use the new `SessionView` and `MultiReader` search
- [x] 4.3 Ensure `GradleSourceService` (for Gradle's own sources) uses the new indexing pipeline

## 5. Garbage Collection & Final Verification

- [x] 5.1 Implement session view pruning in `DefaultSourceStorageService`
- [x] 5.2 Add a new stress test for parallel tool calls to verify zero deadlocks
- [x] 5.3 Remove legacy project-level merging code and old `sources` directory structure
- [x] 5.4 Delete old concurrency documentation (`openspec/docs/concurrency-and-locking.md`)
