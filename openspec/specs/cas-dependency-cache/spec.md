# Capability: cas-dependency-cache

## Purpose

Defines the content-addressable dependency source cache, session-view lifecycle, locking, and garbage-collection behavior for dependency-source tools.

## Requirements

### Requirement: Global CAS Dependency Cache

The system SHALL store extracted sources and indices in a global, immutable cache identified by the content hash of the dependency.

- Extraction and indexing MUST be performed in an isolated temporary directory.
- The finalized directory MUST be moved atomically into its final CAS location: `cache/cas/v3/<first-two-hash-chars>/<hash>/`.
- If the CAS location already exists **and is complete** (has completion marker), the temporary directory MUST be discarded.

#### Scenario: Reusing a completed CAS entry

- **WHEN** a dependency source artifact has already been extracted into a completed CAS entry
- **THEN** later requests SHALL reuse the existing CAS entry instead of mutating it in place

### Requirement: Two-Level CAS Versioning

The system SHALL use two independent versioning mechanisms to invalidate stale cached artifacts:

1. **`CAS_VERSION`** (in `SourceStorageService.kt`) — a path segment embedded in the CAS root (e.g., `cache/cas/v3/`). Bumping this value abandons **all** existing processed entries transparently, as they reside under the old path. Use when
   processed artifact compatibility breaks (e.g., extraction format changes, schema migrations). Current value: **`v3`**.

2. **Lucene `indexVersion`** constants (in `FullTextSearch`, `DeclarationSearch`, `GlobSearch`) — embedded in each index's directory name within a CAS entry. Bumping these triggers re-indexing of already-extracted sources without requiring
   re-extraction of the underlying artifact. Use when only search index logic changes.

#### Scenario: Invalidating all processed artifacts

- **WHEN** `CAS_VERSION` is bumped from `v2` to `v3`
- **THEN** all existing `cache/cas/v2/` entries are effectively abandoned (no deletion required)
- **AND** new requests populate `cache/cas/v3/` on demand

#### Scenario: Invalidating search indices only

- **WHEN** a Lucene `indexVersion` constant is bumped
- **THEN** only the index sub-directory within each CAS entry is considered stale and is re-built
- **AND** previously extracted source files are reused without re-downloading or re-extracting

### Requirement: Self-Repairing Cache

The system SHALL automatically detect and repair broken CAS entries during processing.

- **IF** a CAS directory exists but lacks the `.base-completed` marker
- **THEN** the system SHALL clear the directory and attempt a fresh extraction/normalization.
- Finalization MUST use an atomic replace operation to ensure consistency even if a broken directory existed.

#### Scenario: Repairing interrupted extraction

- **WHEN** a previous process was killed while extracting sources
- **THEN** a subsequent process detects the missing marker, clears the partial data, and completes a fresh extraction.

#### Scenario: First-time processing

- **WHEN** a dependency is not in the CAS cache
- **THEN** the system extracts, indexes, and moves it to the hash-based path

#### Scenario: Concurrent processing

- **WHEN** two parallel processes calculate the same hash
- **THEN** both attempt to process it, and only one successfully completes the atomic move while the other is discarded

### Requirement: Caching & Path Standards

The caching infrastructure SHALL use flexible abstractions to support diverse storage layouts.

- **Path Abstractions**: Storage models SHALL use flexible interfaces (e.g., `MergedSourcesDir`) to allow for different underlying physical layouts (single dependency vs. merged views).
- **Cache Invalidation**: Explicit invalidation requests (e.g., `forceDownload`) MUST be propagated to all dependent layers, including indexing services.
- **Atomic Initialization**: Expensive external operations (e.g., Gradle `resolve()`) MUST be performed exactly once under an exclusive lock after verifying a stale cache status under a shared lock.
- **Lock Naming**: Global lock file names SHALL include a unique identifier (e.g., group name or path hash) to prevent collisions across different organizations or dependency groups.

#### Scenario: Propagating explicit invalidation

- **WHEN** a caller requests forced source refresh
- **THEN** source extraction and indexing layers SHALL observe the invalidation request for the selected dependencies

### Requirement: Granular Advisory Locking

The system SHALL use a two-level locking strategy to maximize concurrent throughput.

- **Base Lock**: A shared 'Base' lock SHALL be acquired for initial checking of the CAS entry.
- **Exclusive Provider Lock**: Facet-specific tasks (e.g., source extraction, different types of indexing) SHALL use independent, exclusive locks. This allows parallel indexing of the same source artifact by different providers once
  extraction is complete.
- **Lock Release Polling**: When polling for a lock release via `tryLockAdvisory`, the system MUST immediately close any successfully acquired lock to avoid blocking other processes.

#### Scenario: Parallel provider indexing

- **WHEN** two different index providers process the same completed CAS entry
- **THEN** each provider SHALL use its own exclusive lock without blocking unrelated provider indexes

### Requirement: Storage Garbage Collection

The system SHALL periodically prune stale session views and unreferenced CAS entries.

- **Session View Pruning**: Views older than 24 hours SHALL be deleted.
- **In-Memory Session-View Cache**: Reusable session-view entries SHALL use Caffeine, SHALL be bounded to 128 cache keys, and SHALL expire after 30 minutes of idle time.
- **Session-View Locking**: Session-view materialization SHALL use bounded in-memory locks, such as lock striping, so failed or high-cardinality filter requests cannot grow an unbounded lock map.
- **Mark-and-Sweep CAS Pruning**:
    1. **Mark**: The system SHALL collect all hashes referenced in all active `manifest.json` files.
    2. **Sweep**: Hashes not in the mark set AND older than 7 days SHALL be deleted.
- **Grace Period**: New CAS extractions MUST have a 1-hour grace period before being eligible for sweeping.
- **Advisory Lock Cleanup**: Stale lock files older than 1 hour SHALL be pruned.

#### Scenario: Pruning unreferenced CAS entries

- **WHEN** a CAS hash is not referenced by any active session manifest
- **AND** the CAS entry is older than the grace period
- **THEN** garbage collection SHALL be allowed to delete the CAS entry

## Design & Rationale

### The Death of In-Place Mutation

Previous architectures that updated shared directories and indices in-place suffered from **Re-entrancy Deadlocks** due to complex Shared/Exclusive lock hierarchies. By moving to immutable, hash-identified directories (CAS), we eliminate
the need for write-locks on existing data.

### Windows File-Handle Contention

Windows OS prevents deletion or renaming of directories if a process has an open handle (e.g., Lucene's `MMapDirectory`). **Ephemeral Session Views** resolve this by ensuring each materialized session-view directory is immutable after creation, even when later tool calls safely reuse that cached view.

### Core Architectural Components

- **Content-Addressable Storage (CAS)**: Dependencies are stored in immutable directories keyed by content hash (`cache/cas/v3/<first-two-hash-chars>/<hash>/`). Readers access these with **zero filesystem locks**.
- **Ephemeral Session Views**: Materialized session views are unique, immutable snapshots (junctions/symlinks to CAS) with a `manifest.json`. Later tool calls may safely reuse a cached session view for the same scope/filter cache key while preserving immutable on-disk contents and zero CAS contention.
- **Virtual Indexing**: We use Lucene `MultiReader` to search across individual CAS indices referenced in the session's `manifest.json`, avoiding expensive physical merging.

### Storage Lifecycle & Garbage Collection

- **Session View Pruning**: Views older than 24 hours are deleted. If a process still has a file open, the OS blocks deletion and the GC simply skips it. The in-memory reusable-session-view cache is separately managed by Caffeine, bounded to 128 keys with a 30-minute idle TTL, and session-view locks are bounded independently from cache-key cardinality.
- **Global CAS Pruning (Mark-and-Sweep)**:
    1. **Mark**: Scan all active `manifest.json` files and collect referenced CAS hashes.
    2. **Sweep**: Delete unreferenced hashes older than 7 days, with a 1-hour grace period for new extractions.
- **Advisory Lock Cleanup**: Prune lock files older than 1 hour to handle stale locks from crashed instances.
