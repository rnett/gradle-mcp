## Why

The current user experience with source indexing and searching has several small friction points that accumulate into a suboptimal workflow. Indexing progress flickers confusingly when processing multiple dependencies, search results are
often cluttered with low-signal import and package matches, and snippet line numbers in search results can be misleading.

## What Changes

- **Indexing Progress Refinement**: Smooth out the indexing progress reporting in `ParallelProgressTracker` to prevent flickering and provide a more stable, high-level view of the operation.
- **Search Result Prioritization**: De-prioritize matches found in `import` and `package` statements in search results to highlight actual code usage and declarations.
- **Search Snippet Cleanup**: Remove misleading or redundant line numbers from search result snippets to improve readability.
- **Search Tool Output**: Update the search results formatting in `DependencySourceTools` to be cleaner and more consistent.

## Capabilities

### Modified Capabilities

- `source-processing-granular-progress`: Refine how progress is reported during parallel extraction and indexing to avoid flickering.
- `dependency-source-search`: Improve search result quality by de-prioritizing imports and packages, and refining snippet output.

## Impact

- `dev.rnett.gradle.mcp.dependencies.ParallelProgressTracker`: Improved message handling.
- `dev.rnett.gradle.mcp.dependencies.search.FullTextSearch`: Score adjustment for import and package matches.
- `dev.rnett.gradle.mcp.dependencies.search.SearchProvider`: Refined snippet generation.
- `dev.rnett.gradle.mcp.tools.dependencies.DependencySourceTools`: Updated search result formatting.