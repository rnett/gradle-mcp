# Design: Optimized Indexing and Parallel Merging

## Architecture Changes

### Indexing

- **`IndexService.indexFiles`**: Modified to accept a list of providers to index (`providersToIndex`).
- **Marker Files**: Instead of a single `.indexed` file, use `.indexed-{providerName}-{indexVersion}`.
- **Metadata**: Cache document counts per provider in `.metadata.json` to avoid expensive count operations during merging.

### Merging

- **`LuceneBaseSearchProvider.mergeIndices`**: Refactored to use `unorderedParallelForEach` for processing multiple dependency indices concurrently.
- **`IndexService.mergeIndices`**: Updated to support merging only requested providers and managing parallel progress reporting.

## Implementation Details

### Parallelism

Use `kotlinx.coroutines.flow.flatMapMerge` (via `unorderedParallelForEach` utility) to process indices in parallel on `Dispatchers.IO`. Use `AtomicInteger` for thread-safe document count tracking during merging.

### Progress Reporting

To avoid flickering, the merging phase reports a single stable message "Merging indices" while updating the completion percentage based on the total number of documents across all indices.

### Caching

- Marker files per provider enable incremental indexing.
- `IndexService` checks for all requested markers before initiating a full indexing pass.
- Hash-based merge validation (`.merged.hash`) includes the list of providers and their versions to ensure the merged index is compatible with the request.

## Verification Plan

### Automated Tests

- `DefaultSourcesServiceTest`: Verify that `indexFiles` is called with the correct provider list.
- `SourceLockingTest`: Ensure that concurrent indexing requests handle locking correctly with per-provider markers.
- `DependencySourceToolsTest`: Verify that search operations only trigger indexing for the requested search type.

### Manual Verification

- Run a large dependency search and observe progress messages for smoothness and lack of flickering.
- Verify that only the relevant provider directories are created/updated in the cache.
