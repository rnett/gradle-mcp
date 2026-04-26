# Capability: multi-reader-search

## Purpose

TBD

## ADDED Requirements

### Requirement: Virtual Multi-Reader Searching

The system SHALL support virtual searching across multiple dependency indices using Lucene `MultiReader`.

- It MUST load the project manifest and open separate `IndexReader`s for each CAS directory.
- It SHALL wrap the readers in a single `MultiReader` for a unified search result set.
- It MUST eliminate the need for physical merging of Lucene indices into a project-level directory.
- It SHALL support dynamic result filtering based on the artifact's role in the current project (e.g., `isDiff` filtering for KMP).

### Requirement: Search Result Filtering

Search providers SHALL use project-level metadata to filter results from individual dependency indices.

- Each dependency index document SHALL contain a `sourceHash` field.
- The searcher SHALL accept a map of index directories to "diff-only" boolean flags.
- **IF** an index is marked as "diff-only", the searcher MUST add a Lucene `BooleanQuery` that restricts results from that artifact's hash to documents where `isDiff = true`.
- This mechanism SHALL be used to prevent duplicate results in projects with overlapping source sets (e.g., KMP).

#### Scenario: Searching multiple dependencies

- **WHEN** a search query is executed
- **THEN** the system combines individual results from all indexed CAS dependencies

### Requirement: Lock-Free Read Access

Search tools and file readers SHALL access CAS directories without acquiring any shared or exclusive filesystem locks.

- CAS directories MUST be treated as immutable once they are moved into the cache.

#### Scenario: High-concurrency search

- **WHEN** multiple tool calls search the same project simultaneously
- **THEN** they all read the manifest and perform searching with zero lock contention
