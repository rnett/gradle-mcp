## Why

The current symbol search is limited to exact FQN matches or analyzed name matches, and lacks the ability to explore symbols at a package level. This makes it difficult for users to discover and navigate through large libraries when they
only have partial knowledge of the fully qualified names (FQNs) or want to explore an entire package's contents.

## What Changes

- **Case-Sensitive Symbol Search**: All symbol and FQN searches are now **case-sensitive** by default.
- **Analyzed FQN Search**: Update `SymbolSearch` to index and search `fqn` (analyzed, but case-sensitive), allowing for partial FQN matches.
- **Glob-Style FQN Wildcards**: Support `*` for a single package segment and `**` for multiple segments when searching across FQNs.
- **Robust Package Exploration**:
    - **Index-Backed Navigation**: Enhance `read_dependency_sources` to automatically resolve dot-separated package paths by querying the symbol index. This ensures correctness even in Kotlin projects where directory structures may not
      strictly match package names.
    - **Package Content Listing**: When a package is "read", the tool will list all direct symbols and immediate sub-packages discovered via the index.
- **Improved Field Naming**: Use clear, user-friendly field names in the index:
    - `name`: Case-sensitive analyzed simple name.
    - `fqn`: Case-sensitive analyzed fully qualified name.
- **Lucene Syntax for Symbols**: Support full Lucene query syntax for `SYMBOLS` search.
- **Tool Renaming Evaluation**: Consider renaming "symbol search" to a more descriptive name, such as "declaration search", to better reflect its purpose of finding code declarations.
- **Improved Tool Descriptions**: Update `SEARCH_DEPENDENCY_SOURCES` and `READ_DEPENDENCY_SOURCES` tool metadata to explicitly mention these new capabilities.
- **Documentation Updates**: Update relevant skills (`researching_gradle_internals`, `searching_dependency_sources`) with examples of how to use these new features.

## Capabilities

### New Capabilities

- `symbol-search-enhancements`: Improved FQN search with glob wildcards, analyzed fields, Lucene syntax, and universal case-sensitive matching.
- `package-exploration`: Ability to list contents of a package via `read_dependency_sources` by querying the symbol index for accurate package/declaration relationships.

### Modified Capabilities

<!-- No requirement changes to existing capabilities -->

## Impact

- `dev.rnett.gradle.mcp.dependencies.search.SymbolSearch`: Modifications to indexing, query parsing, and search logic to handle case-sensitive FQN and name fields, and package-based lookups.
- `dev.rnett.gradle.mcp.tools.dependencies.DependencySourceTools`: Updates to `read_dependency_sources` to support index-backed package exploration.
- `skills/researching_gradle_internals/SKILL.md` and `skills/searching_dependency_sources/SKILL.md`: Documentation updates.
- `roadmap.md`: Mark the corresponding item as completed.
