## Why

The `search_dependency_sources` tool (powered by Lucene) currently exhibits several usability issues:

- **Strict Syntax Errors**: Special characters like `:` and `=` are treated as Lucene syntax, leading to `ParseException` or zero results when searching for literal code fragments (e.g., `LANGUAGE:` or `val x = 10`).
- **Poor Prioritization**: Exact case-sensitive matches for identifiers are often outranked by noisy files containing the same word in larger mixed-case identifiers (e.g., `configureCommonLanguageFeatures` outranking `val LANGUAGE`).

This change aims to provide a more robust and predictable search experience by improving prioritization and providing clear, actionable feedback when search queries contain invalid syntax.

## What Changes

- **Improved Query Feedback**: Provide the LLM with the interpreted query string as parsed by Lucene (e.g., `Query.toString()`). This allows the caller to verify if their query was interpreted as intended (e.g., correctly handling word
  boundaries or special characters) and self-correct if it was not.
- **Enhanced Prioritization**: Modify the full-text search analyzer to better prioritize exact, case-sensitive word matches over partial matches in larger identifiers.
- **Improved Error Feedback**: Catch Lucene syntax errors and return descriptive error messages that guide the user (or LLM) on how to escape special characters or use phrase queries for literal searches.
- **Documentation Updates**: Explicitly document the use of Lucene syntax and the need for escaping special characters in tool descriptions and relevant skills.

## Capabilities

### New Capabilities

- `search-error-feedback`: descriptive error messages for invalid Lucene search syntax.
- `search-result-prioritization`: improved scoring logic to favor exact, case-sensitive matches in full-text search.
- `search-query-interpretation`: provide the interpreted query string to help the LLM verify its search intent.

### Modified Capabilities

<!-- No existing capabilities are being modified at the requirement level, we are just improving the implementation of search_dependency_sources tool. -->

## Impact

- **Affected Code**: `FullTextSearch.kt` (Analyzer and Search logic).
- **APIs**: `search_dependency_sources` tool output (will now include descriptive error messages instead of raw exceptions).
- **Documentation**: `DependencySourceTools.kt`, `SKILL.md` (researching_gradle_internals), `PROJECT_DEPENDENCY_SOURCE_TOOLS.md`.
