## Context

The current in-place index mutation model causes frequent deadlocks on Windows and 60-second timeouts due to re-entrant locking patterns. This design replaces it with an immutable, generational CAS model and virtual indexing.

## Goals / Non-Goals

**Goals:**

- Eliminate re-entrant locks in `FileLockManager`.
- Implement Content-Addressable Storage (CAS) for dependency extraction and indexing.
- Implement Ephemeral Session Views for isolated project snapshots.
- Use Lucene `MultiReader` to avoid physical index merging.
- Move metadata to an external store outside the project root.

**Non-Goals:**

- Changing the underlying search provider implementations (e.g., Lucene query logic).
- Implementing a remote distributed cache.

## Decisions

- **CAS vs Generational Naming**: We chose **Content-Addressable Storage (CAS)** because it provides built-in deduplication and idempotency. Since GAV content rarely changes, identifying resources by their hash allows multiple processes to
  safely perform the same work without data corruption or complex name-based locking.
- **MultiReader vs Physical Merge**: We are shifting to **Lucene MultiReader** to eliminate the Project Exclusive Lock. Physical merging is a slow, high-contention write operation. Virtual merging via MultiReader is a read-only metadata
  operation that takes milliseconds, allowing searchers to start immediately without being blocked by dependency processing.
- **Ephemeral Session Views**: To completely bypass Windows "Access Denied" errors during directory deletion/renaming, we create a **unique directory for every resolution**. This ensures that even if a reader has a file open, it doesn't
  block a writer from creating a new version of the project view.
- **External Metadata Store**: We store `manifest.json` and lock files outside the project sources directory to satisfy the **Clean User Workspace** constraint. This ensures users and agents see only dependency sources in their workspace,
  while the MCP server maintains its state in a system-managed location.
- **Advisory Locks**: We use non-blocking `tryLock()` as a performance optimization. Unlike mandatory locks, if an advisory lock acquisition fails, the system safely falls back to polling or performing redundant work, avoiding the
  circular-wait deadlocks inherent in the current model.
- **Junctions for Windows**: We will prioritize **NTFS Junctions** over Symbolic Links on Windows. Junctions do not require developer mode or admin elevation, ensuring the system works reliably for all users on Windows.

## Risks / Trade-offs

- **[Disk Bloat]** → Mitigation: Implement background garbage collection for old CAS and session directories.
- **[Symlink/Junction Support]** → Mitigation: Use `FileUtils` to automatically choose between symlinks (Unix) and junctions (Windows).
- **[Search Performance]** → Mitigation: Lucene's `MultiReader` is highly efficient, but we will monitor for overhead if the number of dependencies in a single project is exceptionally high (e.g., >1000).
