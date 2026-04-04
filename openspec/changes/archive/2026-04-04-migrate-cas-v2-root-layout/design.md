## Context

The CAS uses a content-addressable layout where each dependency artifact is stored in a directory named by its SHA-256 hash. Currently, multiple schema versions (e.g., `normalized`, `normalized-target`) are stored as subdirectories within
this hash directory. Advisory locks are placed alongside the hash directory in the root `cas/` folder.

## Goals / Non-Goals

**Goals:**

- Namespace all CAS data and locks under a schema version prefix (e.g., `cas/v2/`).
- Ensure that different versions of the server logic never contend for the same lock file.
- Simplify manual or automated cache purging by allowing atomic deletion of a versioned root.

**Non-Goals:**

- Migrating existing `v1` data to `v2`. We will start with a fresh cache for `v2` to ensure total isolation.
- Changing the hashing algorithm or the internal `v1` subdirectory names within the hash folder (they will now be redundant but can remain for implementation simplicity).

## Decisions

### 1. Root Versioning: `cas/v2/<hash>/`

Instead of `cas/<hash>/v2/`, we use `cas/v2/<hash>/`.

- **Rationale:** This isolates the entire file tree for a specific schema version. Locks moved to `cas/v2/<hash>.lock` are also isolated.
- **Alternatives:**
    - `cas/<hash>/v2/`: Current approach. Leads to lock contention because the lock is shared at the hash level.
    - `v2/cas/<hash>/`: Similar to the chosen approach but namespacing the entire cache root. Chosen `cas/v2/` is more specific to the CAS.

### 2. Lock Placement: `cas/v2/<hash>.lock`

Move lock files from the global `cas/` root to the versioned `cas/v2/` root.

- **Rationale:** Complete isolation. `v1` server and `v2` server can work on the same artifact simultaneously without blocking.

### 3. Cleanup of internal version subdirectories

The current implementation uses `normalizedDir = baseDir.resolve("normalized")`. In the new layout, this results in `cas/v2/<hash>/normalized/`.

- **Decision:** Keep the internal `normalized/` and `normalized-target/` subdirectories for now to minimize changes to the normalization logic. The root `v2/` prefix already provides the necessary versioning.

## Risks / Trade-offs

- **[Risk] Disk Usage** → Side-by-side versions will duplicate JAR extractions. Mitigation: The cache is local and source JARs are relatively small.
- **[Risk] Path Lengths** → Deeply nested structures on Windows can hit the 260-character MAX_PATH limit. Mitigation: The hash is already split into two levels (e.g., `ab/abcd...`). Adding `v2/` is a minor increase.
