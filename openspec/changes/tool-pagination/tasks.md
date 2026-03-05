## 1. Core Pagination Infrastructure

- [x] 1.1 Create `PaginationInput` data class in `dev.rnett.gradle.mcp.tools` package
- [x] 1.2 Implement reusable `paginate` higher-order functions in `McpServerComponent` to support:
    - [x] Eager slicing for pre-computed lists
    - [x] Lazy windowing for expensive data providers
    - [x] Text-based slicing (by lines or characters) for large raw outputs
    - [x] Standardized LLM-optimized metadata blocks
- [x] 1.3 Add Top-N pagination helper for `IndexSearcher` to consistently skip `offset` results

## 2. Dependency Tool Pagination

- [x] 2.1 Update `InspectDependenciesArgs` in `GradleDependencyTools.kt` to include optional pagination
- [x] 2.2 Implement slicing for `inspect_dependencies` project-level results
- [x] 2.3 Implement slicing for `inspect_dependencies` update summary results
- [x] 2.4 Update documentation strings in `GradleDependencyTools.kt` to explain pagination behavior

## 3. Source Tool Pagination

- [x] 3.1 Update `SearchProvider` interface and implementations (`FullTextSearch`, `SymbolSearch`, `GlobSearch`) to accept `offset` and `limit`
- [x] 3.2 Update `SourcesService` to propagate `offset` and `limit` to `SearchProvider`
- [x] 3.3 Update `SearchDependencySourcesArgs` and `ReadDependencySourcesArgs` in `DependencySourceTools.kt`
- [x] 3.4 Implement result slicing for `search_dependency_sources` across all search types
- [x] 3.5 Implement directory listing slicing for `read_dependency_sources`
- [x] 3.6 Add LLM-optimized pagination metadata to source tool outputs
- [x] 3.7 Update tool documentation strings for source discovery tools

## 4. Documentation Tool Pagination

- [x] 4.1 Update `QueryGradleDocsArgs` in `GradleDocsTools.kt` to include pagination
- [x] 4.2 Implement slicing for documentation search results
- [x] 4.3 Implement slicing for the full documentation page list
- [x] 4.4 Add pagination metadata to `gradle_docs` output and update documentation strings

## 5. Verification and Testing

- [x] 5.1 Run existing tests to verify zero regressions in tool behavior
- [x] 5.2 Create new test cases for paginated tool calls in `InspectDependenciesTest`, `DependencySearchTest`, etc.
- [x] 5.3 Run `check` task to ensure all tests, linting, and tools list are up-to-date
- [ ] 6.1 Update `gradle` tool and skills documentation regarding output flooding and background execution
