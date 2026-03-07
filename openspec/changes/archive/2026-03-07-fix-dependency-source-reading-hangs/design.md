## Context

The `gradle-mcp` server provides tools for reading and searching dependency source code. This involves downloading, extracting, and indexing thousands of files in a shared cache directory (`environment.cacheDir`). Currently, these
operations lack synchronization, leading to race conditions and inconsistent hangs when multiple tools or server instances attempt to access or modify the same source directories simultaneously, particularly on Windows where file locking is
strict.

## Goals / Non-Goals

**Goals:**

- Ensure exclusive access to project-specific source caches during extraction and indexing.
- Synchronize access to shared global dependency source folders to prevent corrupted extractions.
- Implement robust timeouts for all blocking lock operations to prevent indefinite hangs.
- Provide clear diagnostic feedback when an operation is blocked by another process.

**Non-Goals:**

- Implementing a distributed locking mechanism for network-mounted drives.
- Optimizing for maximum concurrency (correctness and stability are prioritized over raw throughput).

## Decisions

### 1. File-Based Locking for Cross-Process Synchronization

We will use `java.nio.channels.FileChannel` and `FileLock` for synchronization instead of simple coroutine `Mutex`.

- **Rationale**: Multiple instances of the MCP server or external tools may share the same cache directory. OS-level file locks ensure synchronization even across different processes.
- **Alternatives**: Coroutine `Mutex` (only works within one JVM process), checking for a `.lock` file (prone to being orphaned if the process crashes). `FileLock` is automatically released by the OS if the process terminates.

### 2. Hierarchical Locking Strategy

Locks will be applied at two levels:

- **Project Level**: A `.lock` file within each project's source cache directory (`sources/<hash>/metadata/.lock`). This prevents multiple agents from refreshing the same project's source view concurrently.
- **Dependency Level**: A `.lock` file within each global dependency extraction folder (`extracted-sources/<group>/<artifact>/.lock`). This prevents race conditions when extracting common dependencies shared across multiple projects.

### 3. Fail-Fast with Timeouts

All lock acquisition attempts will have a mandatory timeout (default: 60 seconds).

- **Rationale**: The current "hang" behavior is caused by indefinite blocking. Failing fast with a "Resource Busy" error allows the agent to retry or handle the failure gracefully instead of being stuck.
- **Implementation**: Use a loop with `tryLock()` and a small delay, or a dedicated lock manager that handles timeouts.

### 4. Diagnostic Progress Logging

Logging will be added before and during lock waits.

- **Rationale**: Provides visibility into why an operation is taking time.

## Risks / Trade-offs

- **[Risk] Resource Contention** → [Mitigation] Use granular dependency-level locks so that extracting `kotlin-stdlib` doesn't block extracting `junit`.
- **[Risk] Stale Lock Files** → [Mitigation] `FileLock` is handled by the OS; the physical `.lock` file can remain on disk, but the lock itself is released when the file handle is closed.
- **[Risk] Windows Strictness** → [Mitigation] Ensure all file handles (including `FileChannel`) are strictly closed in `finally` blocks.
