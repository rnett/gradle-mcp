## Purpose

Defines requirements for filtering and paginating Gradle build console output via regex patterns.

## Requirements

### Requirement: Console Regex Filtering

The `query_build` tool SHALL support a `query` parameter when `kind="CONSOLE"` that acts as a regular expression filter.

#### Scenario: Filter logs for error

- **WHEN** user calls `query_build(kind="CONSOLE", query="ERROR")`
- **THEN** system returns only lines containing "ERROR", prefixed with their original line number

### Requirement: Console Tail-First Pagination

The `query_build` tool SHALL default to returning the end of the console log (or filtered results) when `kind="CONSOLE"` and no offset is provided.

#### Scenario: Default console view

- **WHEN** user calls `query_build(kind="CONSOLE")` without pagination arguments
- **THEN** system returns the last 20 lines (default limit) of the build console
