---
name: gradle_mcp_caching_expert
description: >-
  Detailed strategies for heavy source caching, multi-layer filtering, and lock management for shared resources within the Gradle MCP project.
metadata:
  author: rnett
  version: "1.0"
---

# Skill: Gradle MCP Caching Expert

This skill provides expert guidance on the heavy caching infrastructure used for Gradle sources and dependency resolution within the Gradle MCP project.

## Caching Strategy

- **Heavy Caching**: This workspace uses extensive caching for Gradle sources. Use `inspect_dependencies` and `search_dependency_sources` with `fresh = true` only when dependencies change.
- **Flexible Path Abstractions**: When representing filesystem structures for caches (like `SourcesDir`), prefer interfaces with flexible implementations (e.g., `MergedSourcesDir`, `SingleDependencySourcesDir`) over rigid data classes.
- **Cache Invalidation**: When implementing re-extraction or re-indexing logic with `forceDownload`, explicitly propagate the flag to all underlying services (like `IndexService`) to ensure stale caches are invalidated.
- **Expensive Operations**: In `withSources` (and similar cached operations), perform expensive external calls (like Gradle `resolve()`) exactly once under an exclusive lock, and only after checking a shared lock for a fresh cache.
- **Lock File Naming**: Include the group name or a hash of the full path in global lock file names for shared resources to prevent collisions across different organizations.

## Multi-Layer Filtering

- **Discrepancy Documentation**: When implementing filtering across multiple layers (e.g., init script vs service), document discrepancies in filtering capabilities (e.g., G:A:V vs G:A:V:Variant).
- **Verification**: Ensure the final layer provides precise verification to safely handle over-fetching from limited earlier layers while maintaining correctness and performance.

## Examples

### Implementing a new cached operation

1. Check for a fresh cache under a shared lock.
2. If stale, acquire an exclusive lock.
3. Perform the expensive operation (e.g., Gradle resolve) exactly once.
4. Update the cache and release the lock.

### Handling multi-layer filters

1. Apply the G:A:V filter in the init script.
2. In the service layer, verify the exact G:A:V:Variant to handle over-fetching.
3. Document why the two-stage process is necessary for performance.
