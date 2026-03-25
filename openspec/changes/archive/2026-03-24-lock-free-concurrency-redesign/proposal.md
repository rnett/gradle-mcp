## Why

The current Gradle MCP server architecture relies on in-place mutation of shared indices and complex, multi-layered file locking. This has led to several systemic failures that require a fundamental redesign:

1. **Re-entrancy Deadlocks**: High-level orchestrators (like `SourcesService`) acquire exclusive locks and then call downstream services that attempt to acquire shared locks on the same files. Due to `OverlappingFileLockException` and
   coroutine-unsafe re-entrancy attempts, this frequently triggers 60-second timeouts and hangs.
2. **Windows File Contention**: On Windows, once a reader (like Lucene's `MMapDirectory`) opens a file, the OS often prevents writers from renaming or deleting the parent directory. Our current "merge-and-swap" model is fundamentally
   incompatible with these OS-level constraints.
3. **Physical Merging Bottlenecks**: Merging thousands of small dependency indices into a single project index is a slow, high-contention write operation. It forces all search tools to wait for a long-running, exclusive-lock-holding process
   to finish.
4. **Starvation**: The competitive spin-loop in `FileLockManager` does not guarantee fairness, allowing processes to be starved of locks under heavy load.

### Constraints & Invariants

- **Stable GAV Content**: Group:Artifact:Version (GAV) content rarely changes. We can leverage this to treat dependency extractions as immutable.
- **Windows Junction Support**: Windows requires junctions for directory symlinks to avoid permission/elevation issues.
- **Clean User Workspace**: The absolute path returned to the user must be a "workspace" containing only dependency sources, with all metadata (manifests, locks) stored externally.
- **Idempotency**: Multiple processes (IDE, CLI) must be able to resolve the same state simultaneously without data corruption.

## What Changes

- **BREAKING**: Replaced in-place index mutation with an immutable, generational Content-Addressable Storage (CAS) model.
- **BREAKING**: Replaced project-level index merging with virtual `MultiReader` search across individual dependency indices.
- **BREAKING**: Moved project-level metadata (manifests) from the user-facing project root to a system-managed external Metadata Store.
- Removed complex re-entrancy and retry logic from `FileLockManager`.
- Introduced a lightweight, non-blocking advisory lock system for coordinated first-time extraction and indexing.
- Implemented an Ephemeral Session View system for totally isolated project snapshots.

## Capabilities

### New Capabilities

- `cas-dependency-cache`: Implements a global, immutable cache where dependencies are stored by their content hash. Handles atomic moves and advisory locking for concurrent processing.
- `multi-reader-search`: Implements virtual searching across multiple individual dependency indices using Lucene `MultiReader`, eliminating the need for physical merging.
- `ephemeral-session-views`: Manages unique, isolated project snapshots (junctions/symlinks and manifests) for each resolution tool call, ensuring readers never collide with writers.
- `cache-garbage-collection`: Implements background pruning of abandoned session views and unreferenced CAS directories.

### Modified Capabilities

- `build-monitoring-progress`: Updated to handle progress reporting during the new CAS-based resolution and indexing pipeline.

## Impact

- **Core Services**: `SourcesService`, `SourceStorageService`, and `SourceIndexService` will be fundamentally refactored to support the CAS and Session View models.
- **Search System**: All search providers and `IndexService` will be updated to use the `MultiReader` architecture.
- **File Locking**: `FileLockManager` will be simplified, removing re-entrancy support.
- **Storage Layout**: The `~/.mcp/` cache directory structure will be significantly updated to include `cas/`, `projects/`, and `views/` hierarchies.
