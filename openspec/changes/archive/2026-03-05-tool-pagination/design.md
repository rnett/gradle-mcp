## Context

The Gradle MCP server currently hosts several tools that can return large volumes of data, such as dependency trees, documentation search results, and source code symbols. Returning all this data in a single response can overwhelm the LLM's
context window, leading to truncated information or lost context. While `search_maven_central` already implements basic pagination, most other high-output tools do not, leading to inconsistent behavior and efficiency issues.

## Goals / Non-Goals

**Goals:**

- Define a standardized `PaginationInput` model for all MCP tools.
- Add pagination support to `inspect_dependencies`, `search_dependency_sources`, `read_dependency_sources`, and `gradle_docs`.
- Provide clear metadata in tool responses about the current range and total available items.
- Maintain backward compatibility by making pagination parameters optional.

**Non-Goals:**

- Migrating tools from string-based output to structured JSON (this remains out of scope for the current architectural phase).
- Implementing complex server-side cursor management or stateful sessions.

## Decisions

### 1. Standardized `PaginationInput` Component

A reusable `@Serializable` data class will be created in `dev.rnett.gradle.mcp.tools.PaginationInput`.

- **Fields**: `offset: Int = 0`, `limit: Int = 20`.
- **Rationale**: Using a consistent model ensures that all tools follow the same naming and default behavior, making it easier for agents to learn how to paginate through different data types.

### 2. Reusable `paginate` and `paginateText` Utilities

The base component will provide specialized utilities for different data types:

- **`paginate(List<T>, ...)`**: Handles collection slicing (projects, dependencies, search results).
- **`paginateText(text: String, input: PaginationInput, unit: PaginationUnit)`**: Handles slicing large raw strings.
    - **`PaginationUnit.LINES`**: Slices by line count (ideal for logs, source files).
    - **`PaginationUnit.CHARACTERS`**: Slices by character count (ideal for unstructured text/docs).
- **Rationale**: This ensures that even tools returning large single-string outputs (like `read_dependency_sources` when reading a file) can benefit from standardized context management.

### 3. Search Provider Pagination (Top-N Approach)

The `SearchProvider` interface will be updated to accept `offset` and `limit`.

- **Lucene Implementation**: Uses `indexSearcher.search(query, offset + limit)`. The handler will then skip the first `offset` `ScoreDocs` to provide the requested window.
- **Why not `searchAfter`?**: `searchAfter` is ideal for stateful, sequential pagination (e.g., "Next Page" buttons), but it requires passing a `ScoreDoc` from the previous result. For stateless MCP tool calls with arbitrary `offset` and
  `limit`, the Top-N approach is the most robust and standard implementation, as it doesn't require the client to maintain search state.
- **Regex/Glob Implementations**: Uses Kotlin `Sequence` with `.drop(offset).take(limit)` for memory-efficient slicing of in-memory match results.
- **Rationale**: This provides a consistent pagination interface across all search types while respecting Lucene's API constraints and the stateless nature of MCP.

### 3. LLM-Optimized Pagination Metadata

All paginated tools will use a consistent, high-signal metadata block at the beginning or end of the response:

```markdown
---
Pagination: Showing items $start to $end of $total.
To see more results, use: `offset=${end + 1}`, `limit=$limit`.
---
```

- **Rationale**: LLMs parse structured blocks reliably. Explicitly providing the next `offset` value in the response reduces the reasoning steps required for the agent to continue its search.

### 4. Proactive "Context Warning" in Descriptions

Tool `@Description` annotations will be updated to include explicit guidance:
*"Large result sets are paginated. If you see a pagination notice, increment the `offset` to continue your exploration."*

- **Rationale**: This primes the LLM to look for and act on the pagination metadata.

## Risks / Trade-offs

- **[Risk]** → Performance overhead of generating full reports only to slice them.
- **[Mitigation]** → For extremely large datasets (like `inspect_dependencies` in massive projects), we will investigate passing limits down to the Gradle services in a follow-up optimization. For now, the focus is on context-safe output.
- **[Risk]** → Inconsistent "item" definitions (e.g., is an item a project, a configuration, or a dependency?).
- **[Mitigation]** → Clearly define the unit of pagination in each tool's documentation string.
