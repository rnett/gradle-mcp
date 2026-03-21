# Proposal: Optimized Indexing and Parallel Merging

## Summary

Improve indexing performance and user experience by implementing per-provider indexing and parallelizing the index merging process.

## Problem

1. **Flickering Progress:** Indexing progress flickers between different provider types (e.g., full text vs. declaration), creating a jittery UI.
2. **Redundant Work:** All providers are indexed even when only one is requested (e.g., during a single search).
3. **Sequential Bottleneck:** Merging large numbers of dependency indices is done sequentially, causing the process to appear to "hang" at the end.

## Solution

1. **Per-Provider Indexing:** Implement individual marker files for each search provider. This allows checking if a specific provider's index is up-to-date and indexing only what's needed.
2. **Parallel Merging:** Use `unorderedParallelForEach` to merge indices in parallel, significantly reducing the time spent in the finalization phase.
3. **Stable Progress Reporting:** Ensure progress messages remain stable (e.g., "Merging indices") while updating the count based on parallel completion.

## Impact

- Faster indexing for single-provider operations.
- Significantly reduced overall indexing time for large projects.
- Smoother and more predictable progress reporting.
