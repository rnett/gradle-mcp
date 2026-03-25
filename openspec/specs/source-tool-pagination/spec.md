# Capability: source-tool-pagination

## Purpose

Specifies pagination support for dependency source search and directory listing tools.

## Requirements

### Requirement: Paginated Dependency Source Search

The `search_dependency_sources` tool SHALL support `offset` and `limit` for all search types (SYMBOLS, FULL_TEXT, GLOB) to browse numerous matches.

#### Scenario: Searching symbols with pagination

- **WHEN** `search_dependency_sources` is called with `query=".*"` and `limit=5`
- **THEN** the system SHALL return at most 5 results even if more exist

### Requirement: Paginated Directory Listings

The `read_dependency_sources` tool SHALL support optional `offset` and `limit` for directory listings.

#### Scenario: Listing a large directory with pagination

- **WHEN** `read_dependency_sources` is called on a directory with many files, using `limit=10`
- **THEN** the system SHALL return only the first 10 items in the directory listing
