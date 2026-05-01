# Architectural Redesign: Pure CAS Concurrency Model (v2)

## Executive Summary

This document outlines a systemic redesign of the Gradle MCP file loading and indexing concurrency model. The primary goal is to completely eliminate the need for re-entrant file locks, resolve 60-second deadlock hangs, and provide a
stable, high-performance user experience.

We achieve this through **"Ultimate Isolation"**: using a **Content-Addressable Storage (CAS)** model for global dependencies and **Ephemeral Session Views** for project-level coordination.

---

## Rationale & Constraints

### 1. The Death of In-Place Mutation

The previous architecture attempted to update shared directories and indices in-place. This required complex Shared/Exclusive lock hierarchies which, when combined with high-level orchestration, inevitably led to **Re-entrancy Deadlocks**.
By moving to immutable, hash-identified directories (CAS), we eliminate the need for write-locks on existing data.

### 2. Windows File-Handle Contention

Windows OS prevents the deletion or renaming of directories if a process (like Lucene's `MMapDirectory`) has a handle open to a file within it. Our "merge-and-swap" model frequently failed with `AccessDeniedException`. **Ephemeral Session
Views** resolve this by ensuring each tool call uses a private, unique directory that is never modified once created.

### 3. Merging as a Bottleneck

Physical merging of Lucene indices is expensive and high-contention. Shifting to **Virtual MultiReader** searching allows searchers to start immediately after dependencies are processed, without waiting for a project-wide write lock.

### 4. Clean Workspace Invariant

Users and agents must have a clean absolute path containing *only* dependency sources. By storing `manifest.json` and lock files in an **External Metadata Store**, we ensure the user-facing "view" remains uncluttered and specialized for
reading.

---

## Core Architectural Shifts

### 1. Content-Addressable Storage (CAS)

Dependencies are identified and stored entirely by the cryptographic hash of their content.

* **Immutability:** Once written to the CAS cache (`.cache/cas/v3/<hash>`), a directory is **never modified**.
* **Zero-Lock Readers:** Because CAS directories are immutable, searchers and readers access them with **no filesystem locks**.

#### CAS Versioning

The CAS path includes a version segment (`v3`) controlled by the `CAS_VERSION` constant in `SourceStorageService.kt`. This version is the mechanism for invalidating all processed artifacts:

* **`CAS_VERSION` bump** — increment when processed artifact compatibility breaks (e.g., extraction format changes, schema migrations). All existing CAS entries under the old version path are abandoned transparently; the new version path
  starts empty and is repopulated on demand.
* **Lucene `indexVersion` bump** (in `FullTextSearch`, `DeclarationSearch`, `GlobSearch`) — increment when only search index logic changes. This triggers re-indexing of already-extracted sources without requiring re-extraction.

Current version: **`v3`**.

### 2. Ephemeral Session Views

Instead of a single "merged" directory for a project, every resolution tool call creates a unique, immutable snapshot.

* **Path:** `~/.mcp/projects/<id>/views/<timestamp>-<uuid>/`
* **Structure:**
    * `manifest.json`: Metadata mapping for `MultiReader` tools.
    * `sources/`: The "Unified Source Root." A directory containing **only symlinks/junctions** to CAS. This is the path returned to the user/agent.
* **Isolation:** Every tool invocation works in its own unique directory. There is **zero contention** between parallel tool calls.

### 3. Virtual Indexing (Lucene MultiReader)

We no longer physically merge Lucene files into a monolithic project index.

* **MultiReader:** Search tools read the session's `manifest.json`, open `IndexReader`s for each referenced CAS directory, and wrap them in a Lucene `MultiReader`.

---

## Detailed Resource Management

### The Write Path (Dependency Processing)

To prevent redundant work for the same hash across processes:

1. **Advisory Lock:** Attempt a non-blocking `tryLock()` on `.locks/cas/<hash>.lock`.
2. **Worker:** If acquired, extract and index into a temp directory, then atomically move to `cas/<hash>`.
3. **Watcher:** If denied, suspend (`delay`) and poll until `cas/<hash>` exists.

### The Project Sync Path (View Creation)

1. **Unique Identity:** Create a new directory: `~/.mcp/projects/<id>/views/<session-id>/`.
2. **Populate Sources:** Create symlinks (Unix) or junctions (Windows) in the `sources/` subdirectory pointing to the correct CAS hashes.
3. **Finalize Manifest:** Write `manifest.json` containing the mapping.
4. **No Locking:** Because the path is unique to this session, **no project-level locks are required**.

### Lifecycle & Cleanup

- **Session Stability:** A tool call uses its specific session directory for its entire duration.
- **Background Garbage Collection:** An asynchronous task (triggered at startup and periodically) is responsible for pruning unreferenced or stale resources.

---

## Storage Lifecycle & Garbage Collection

### 1. Session View Pruning

Because every tool call creates a unique directory, disk space could accumulate over time.

* **Threshold:** Views older than 24 hours (based on creation time or last access) are candidates for deletion.
* **Safety:** The GC attempts a non-recursive delete of the session directory.
* **Windows Resilience:** If a process (IDE, search tool) still has a file open in a specific view, the OS will block the deletion. The GC simply skips this directory and tries again on the next pass.

### 2. Global CAS Pruning (Reference Counting)

Pruning the CAS cache is more delicate, as multiple sessions might point to the same hash.

* **Mark-and-Sweep:**
    1. **Mark:** Scan all active `manifest.json` files in the `views/` directory and collect all referenced CAS hashes.
    2. **Sweep:** Scan the `cas/` directory. Any hash not found in the "Mark" set that hasn't been accessed for a long period (e.g., 7 days) is deleted.
* **Transient Protection:** A "grace period" (e.g., 1 hour) ensures that a newly extracted CAS directory isn't deleted before a manifest has been written to point to it.

### 3. Advisory Lock Cleanup

Advisory locks are transient performance optimizations. To handle stale locks from crashed instances without interfering with other active processes:

* **Time-Based Pruning:** A background task (or startup task) SHALL prune advisory lock files that are older than a specific threshold (e.g., 1 hour).
* **Safety:** This ensures that if a process crashes while holding a lock, the hash is eventually released for other workers, while active processes are protected.

---

## Why this solves our problems

| Layer                  | Path Example                           | Content                                      | Mutability    |
|:-----------------------|:---------------------------------------|:---------------------------------------------|:--------------|
| **Global CAS Cache**   | `~/.mcp/cas/v3/sha256-a1b2c3d4/`       | Extracted sources & Lucene indices.          | **Immutable** |
| **Session View Store** | `~/.mcp/views/<project>/<session-id>/` | `manifest.json` + `sources/` (symlink root). | **Immutable** |
| **Advisory Locks**     | `~/.mcp/.locks/cas/<hash>.lock`        | Coordination for first-time extraction.      | **Transient** |

---

## Why this solves our problems

1. **Eliminates Re-entrant Locks:** A process never needs to "nested-lock" a path it is already modifying because it is always creating new, unique paths.
2. **Zero-Deadlock Architecture:** By removing the "Shared/Exclusive" lock hierarchy for projects and dependencies, we eliminate the conditions for circular waiting.
3. **Windows Reliability:** We never attempt to delete or update a directory that a reader has open. "Access Denied" errors are effectively banished from the primary tool workflows.
