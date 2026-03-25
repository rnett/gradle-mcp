## Context

Source indexing and searching in the Gradle MCP server currently has several UX friction points:

1. **Progress Flickering**: `ParallelProgressTracker` updates the global progress message for every individual file indexed across all parallel dependency indexing tasks. This causes the CLI progress bar to flicker rapidly between different
   dependencies.
2. **Search Noise**: Matches found in `import` and `package` statements are treated with the same priority as matches in the actual code body, leading to "noisy" results when searching for common types.
3. **Snippet Readability**: Search snippets are reported with redundant or misleading line information that can clutter the output.

## Goals / Non-Goals

**Goals:**

- Eliminate progress flickering during indexing by stabilizing the message in `ParallelProgressTracker`.
- De-prioritize matches found in `import` and `package` statements in search results.
- Clean up search snippet formatting and reported line numbers.

**Non-Goals:**

- Changing the underlying indexing mechanism (Lucene).
- Adding new search capabilities beyond the current `DECLARATION`, `FULL_TEXT`, and `GLOB`.

## Decisions

### 1. Progress Reporting Stability

Instead of updating the progress message to show whichever file was just indexed across any dependency, `ParallelProgressTracker` will be modified to maintain a more stable view of the currently processing dependency. We will:

- **Stable Dependency Tracking**: Instead of showing the message from the absolute latest event (which causes rapid flickering between parallel tasks), the tracker will maintain a "focus" dependency (e.g., the last one added to the active
  set). The message will consistently show the name of this focus dependency until it completes, at which point it switches to the next active one.
- **Maintain Frequency**: We will continue to report progress updates as frequently as they come in, preserving the overall progress bar's responsiveness.
- **No Phase Distinction**: We will not distinguish between extraction and indexing phases at the high-level tracker, keeping the message simple.

### 2. Search Result Prioritization

In `FullTextSearch`, we will adjust the score of matches based on their context using a multi-field Lucene approach:

- **Stripped Fields**: During indexing, we create a secondary set of fields (`contents_code`, `contents_code_exact`) where `import` and `package` lines are replaced with exact-length whitespace.
- **Offset Preservation**: By using whitespace instead of removing the lines, we preserve the exact character offsets, ensuring that multi-line phrase searches and snippet offsets remain accurate across all fields.
- **Native Boosting**: We use Lucene's native `parseBoostedQuery` to heavily boost the `contents_code` fields.
- **Penalization**: Matches found only in the original `contents` fields (which contain boilerplate) naturally receive a lower score than those found in both or only in the code fields.
- **Outcome**: This ensures that actual code usage and declarations appear above boilerplate imports and package declarations without the overhead of search-time I/O or manual re-ranking.

### 3. Snippet Refinement

We will audit `SearchResult.toSearchResults` and `DependencySourceTools.formatSearchResults` to ensure:

- Snippet content does not contain redundant line number prefixes.
- The `File: path:line` format is the single source of truth for location.
- Snippets correctly highlight the match context without unnecessary boilerplate.

### 4. Structural Refactoring

The `DefaultSourcesService` had grown significantly, handling orchestration, extraction, locking, and indexing. To improve maintainability and single-responsibility principles, it is split into three specialized services:

- **`SourceStorageService`**: Manages the filesystem, locking paths, hashing, and cache directories.
- **`SourceIndexService`**: Coordinates reading extracted files and piping them to Lucene for indexing, handling index merging, and coordinating searches.
- **`SourcesService`**: Handles the high-level orchestration, resolving dependency sequences and triggering the extraction/indexing pipeline.
  This separation allows cleaner Koin injections and separates file I/O locks from index-level logic.

### 5. Index Versioning and Path Prefixing

To support these changes cleanly without causing startup crashes on older caches:

- The Lucene index version constants are bumped (e.g. `v4` to `v6` for `FullTextSearch`).
- The `path` field indexed in Lucene now includes the dependency's relative prefix. This eliminates the need to rewrite paths during index merging, allowing the use of the high-performance `writer.addIndexes(directory)` instead of manual
  document copying.

## Risks / Trade-offs

- **Storage Overhead**: Doubling the content fields in Lucene will increase the disk footprint of the source indices.
    - *Mitigation*: This is a one-time cost per dependency version. The trade-off for significantly better search quality and faster search-time performance is acceptable.
- **Complexity**: Managing multi-field boosts in `parseBoostedQuery` requires careful tuning to ensure code matches always outrank boilerplate matches without drowning out legitimate secondary relevance signals.
    - *Mitigation*: We use a 25x boost for exact code matches to provide a strong priority signal.
- **Progress State Management**: Throttling progress updates in a multi-threaded coroutine environment requires careful state management.
    - *Mitigation*: `ParallelProgressTracker` uses atomic primitives and concurrent collections to ensure thread-safety without heavy locks.