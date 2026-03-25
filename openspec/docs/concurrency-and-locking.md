# Concurrency and Locking Strategy

This document outlines the multi-layered locking and caching strategy used to manage Gradle source extraction, indexing, and merging.

## Overview

The system manages storage at two primary levels, each with its own locking requirements to ensure consistency across multiple Gradle projects and parallel tool calls.

1. **Global Dependency Cache**: Stores raw source files and Lucene indices for a specific dependency (e.g., `org.slf4j:slf4j-api:2.0.17`).
2. **Project Merged Index**: Stores a single, merged index containing documents from all dependencies in a specific project scope.

## Locking Hierarchy

To prevent deadlocks and ensure data integrity, the following locking order must be respected:
`Project Lock` -> `Dependency Lock`.

The system uses a **reentrant locking mechanism** in `FileLockManager`, allowing nested locks on the same file in the same coroutine to succeed without deadlocking.

### 1. Project Merged Sources Lock

* **Path**: `sources/PROJECT_ID/.lock`
* **Usage**: Orchestrated in `SourcesService.withSources` and `SourceIndexService.ensureMergeUpToDate`.
* **Shared Lock**: Acquired during cache hits to read the merged index or perform searches.
* **Exclusive Lock**: Acquired when the project-level cache is missing, stale, or requires a merge.

### 2. Dependency Lock

* **Path**: `.locks/dependencies/RELATIVE_PREFIX_HASH.lock`
* **Usage**: Orchestrated in `SourceStorageService.extractSources`, `DefaultIndexService.indexFiles`, and `DefaultIndexService.mergeIndices` (shared).
* **Relative Prefix**: A stable identifier derived from the dependency group and sources filename (e.g., `org/slf4j/slf4j-api-2.0.17-sources`).
* **Exclusive Lock**: Acquired during extraction or indexing of a specific dependency.
* **Shared Lock**: Acquired during `mergeIndices` to ensure the source index isn't deleted or modified while its documents are being copied into the project-level index.

## Cache Integrity & Invalidation

### Atomic Operations

The system relies on atomic operations to ensure cache stability without excessive locking:

* **Atomic Directory Replacement**: `FileUtils.atomicReplaceDirectory` uses a rename-to-temp, move-source-to-target, delete-temp sequence. This ensures that readers holding shared locks on the target directory don't block the update (though
  they might see an "empty" directory for a tiny fraction of a second if the rename succeeds but the next move hasn't happened yet, but `FileLock` usually prevents this on Windows anyway).
* **Metadata**: Dependency-level document counts are stored in a single `.metadata.json` within the index directory, updated atomically under the Dependency Lock.

### Marker Files & Hash Verification

* **Marker Files**: Each provider index uses a marker file (e.g., `.indexed-declarations-7`). Created only at the end of a successful run.
* **Merge Hash Verification**: Merged indices use a `.merged.hash` file. Verified under a shared lock for performance.

## Best Practices for Developers

1. **Always use `FileLockManager`**: Never manually manipulate `.lock` files.
2. **Lock Scope**: Keep lock durations as short as possible.
3. **Flow Consumption**: In `indexFiles`, always consume the `fileFlow` (even on cache hits) to prevent deadlocks in the producer-consumer pipeline.
