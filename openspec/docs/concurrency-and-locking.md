# Concurrency and Locking Strategy

This document outlines the multi-layered locking and caching strategy used to manage Gradle source extraction, indexing, and merging.

## Overview

The system manages three distinct levels of storage, each with its own locking requirements to ensure consistency across multiple Gradle projects and parallel tool calls.

1. **Global Extraction Cache** (`extracted-sources/`): Stores raw source files for a specific dependency (e.g., `org.slf4j:slf4j-api:2.0.17`).
2. **Global Index Cache** (`source-indices/`): Stores Lucene indices per provider for each extracted dependency.
3. **Project Merged Index** (`sources/PROJECT_HASH/.../metadata/index`): A single, merged index containing documents from all dependencies in a specific project scope.

## Locking Hierarchy

To prevent deadlocks and ensure data integrity, the following locking order must be respected:
`Project Lock` -> `Global Extraction Lock` -> `Global Index Lock` -> `Metadata Lock`.

### 1. Project Merged Sources Lock

* **Path**: `sources/PROJECT_ID/.lock`
* **Usage**: Orchestrated in `SourcesService.withSources`.
* **Shared Lock**: Acquired during cache hits to read the merged index or perform searches.
* **Exclusive Lock**: Acquired when the project-level cache is missing, stale, or requires a merge of a new provider.

### 2. Global Extraction Lock

* **Path**: `.locks/extracted-sources/GROUP-ARTIFACT-VERSION.lock`
* **Usage**: Orchestrated in `SourcesService.processSingleDependencyTask`.
* **Exclusive Lock**: Acquired during the extraction of a JAR into the global cache. This ensures only one process extracts a specific dependency at a time.

### 3. Global Index Lock

* **Path**: `.locks/source-indices/GROUP/NAME-PROVIDER.lock`
* **Usage**: Orchestrated in `DefaultIndexService.indexFiles` and `DefaultIndexService.mergeIndices`.
* **Exclusive Lock**: Acquired when indexing a specific provider for a specific dependency.
* **Shared Lock**: Acquired during `mergeIndices` to ensure the source index isn't deleted or modified while its documents are being copied into the project-level index.

### 4. Metadata Lock

* **Path**: `.locks/source-indices/GROUP/NAME-metadata.lock`
* **Usage**: Orchestrated in `DefaultIndexService.indexFiles` and `DefaultIndexService.loadDependencyMetadata`.
* **Exclusive Lock**: Acquired when updating the `.metadata.json` file (which stores document counts per provider).
* **Shared Lock**: Acquired when reading document counts during the "PREPARING" phase of a merge.

## Cache Integrity & Invalidation

### Marker Files

Each provider index uses a marker file (e.g., `.indexed-declarations-7`).

* **Creation**: Created only at the *end* of a successful `indexFiles` run.
* **Invalidation**: When a re-index is triggered (e.g., `forceDownload=true`), the marker file is **immediately deleted** after acquiring the exclusive lock. This prevents "poisoned cache" scenarios where an interrupted run leaves an empty
  directory but a valid-looking marker.

### Merge Hash Verification

Merged indices use a `.merged.hash` file to verify they are up-to-date.

* **Content**: `provider.indexVersion + "\n" + dependencyHash`.
* **Verification**: `isMergeUpToDate` performs a two-stage check:
    1. **Shared Lock Check**: Fast check to see if the hash matches.
    2. **Exclusive Lock Check**: If shared check passes, briefly acquire an exclusive lock to ensure no `ATOMIC_MOVE` is currently replacing the directory.

## Best Practices for Developers

1. **Always use `FileLockManager`**: Never manually manipulate `.lock` files.
2. **Lock Scope**: Keep lock durations as short as possible, but ensure they cover the entire duration of I/O operations (especially Lucene merges).
3. **Atomic Moves**: Always write to temporary directories/files and use `StandardCopyOption.ATOMIC_MOVE` to update the final cache location.
4. **Flow Consumption**: In `indexFiles`, always consume the `fileFlow` (even on cache hits) to prevent deadlocks in the producer-consumer pipeline.
