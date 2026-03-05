## Why

Several tools in the Gradle MCP server, such as dependency lookups, source searches, and documentation queries, can return extremely large outputs that exceed optimal context limits. Standardized pagination allows agents to retrieve this
data in surgical, manageable chunks, improving performance and reducing token waste.

## What Changes

- **Standardized Pagination Interface**: Introduce a reusable `PaginationInput` component for tool argument models to ensure consistent `offset` and `limit` behavior across the server.
- **Enhanced Dependency Reporting**: Add pagination to `inspect_dependencies` to handle projects with large dependency trees or many updates.
- **High-Performance Search Pagination**: Update `SearchProvider` and its implementations (Lucene, Regex, Glob) to support `offset` and `limit` at the indexing and retrieval layer, ensuring efficient browsing of thousands of source code
  matches.
- **Documentation Query Pagination**: Add pagination to `gradle_docs` for broad search queries and full-page lists.
- **Directory Listing Pagination**: Update `read_dependency_sources` to paginate directory contents for libraries with large package structures.

## Capabilities

### New Capabilities

- `tool-pagination`: Base infrastructure and common patterns for paginated tool responses.
- `dependency-tool-pagination`: Range-based retrieval and formatting for dependency reports and update checks.
- `source-tool-pagination`: Offset/limit support for symbol, full-text, and glob searches within dependency sources.
- `docs-tool-pagination`: Paginated search results and page listings for the Gradle documentation service.

## Impact

- **McpServerComponent**: New common input models for pagination.
- **Tool APIs**: `inspect_dependencies`, `search_dependency_sources`, `read_dependency_sources`, and `gradle_docs` will have modified input schemas (non-breaking as new fields are optional).
- **Service Layer**: `GradleDependencyService`, `SourcesService`, and `GradleDocsService` will need internal logic updates to support data slicing.
