## Context

The current `search_dependency_sources` tool is limited to searching symbols and text within source files (Java, Kotlin, Groovy). There is a need to find specific files (like `AndroidManifest.xml`, `LICENSE`, or resources) within
dependencies. This design introduces a `GLOB` search capability that uses standard glob patterns to find files by their relative paths across the entire dependency tree.

## Goals / Non-Goals

**Goals:**

- Provide a `GLOB` search type in `search_dependency_sources`.
- Index all file paths in each dependency, not just source files.
- Support standard glob syntax (e.g., `**/AndroidManifest.xml`).
- Return results with relative paths and content snippets.
- Use persistent, merged indices for high-performance searching.

**Non-Goals:**

- Full-text search within non-source files (e.g., searching within binary files or large XML files).
- Using glob patterns for content searching (globs are strictly for file paths).

## Decisions

### 1. Indexing Strategy: Persistent Filename List

We will store a list of all relative file paths for each dependency in a `filenames.txt` file within the dependency's index directory.

- **Rationale**: For glob search, the most efficient approach is to have the full list of paths available. Using `java.nio.file.FileSystem.getPathMatcher("glob:<pattern>")` against a list of paths is extremely fast and avoids repeated
  filesystem walks.

### 2. Merging Indices

When `IndexService` merges indices for multiple dependencies, it will create a consolidated `filenames.txt` where each path is prepended with its relative prefix within the merged source tree.

- **Rationale**: This maintains the same pattern used for symbol and full-text indices, allowing the search to be performed over the entire set of dependencies at once.

### 3. Snippet Generation: Relevant Content Filtering

For `GLOB` search results, the snippet calculation will be refined to provide high-signal content. It will skip:

- Blank lines
- Package declarations
- Import statements
- Comments (e.g., license headers, Javadoc), including multiline `/* ... */` blocks.
- **Rationale**: This ensures that the returned snippet immediately shows meaningful code or configuration rather than boilerplate or metadata.

## Risks / Trade-offs

- **[Risk] Large Number of Files** → A dependency with an exceptionally large number of files (e.g., thousands of small resources) could lead to a large `filenames.txt` and high memory usage during search.
    - **Mitigation**: We will monitor index sizes and can implement a limit on the number of files indexed per dependency if it becomes a problem. Most library dependencies have a manageable number of files.
- **[Trade-off] Simple Text Index** → Searching a large list of strings linearly with regex/glob matching is slower than a more advanced trie or Lucene index.
    - **Mitigation**: Given the typical number of files in a dependency graph (usually < 100,000), linear matching of paths is still fast enough for interactive use (milliseconds).
