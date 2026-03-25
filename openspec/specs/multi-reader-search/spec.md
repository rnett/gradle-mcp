# Capability: multi-reader-search

## Purpose

TBD

## ADDED Requirements

### Requirement: Virtual Multi-Reader Searching

The system SHALL support virtual searching across multiple dependency indices using Lucene `MultiReader`.

- It MUST load the project manifest and open separate `IndexReader`s for each CAS directory.
- It SHALL wrap the readers in a single `MultiReader` for a unified search result set.
- It MUST eliminate the need for physical merging of Lucene indices into a project-level directory.

#### Scenario: Searching multiple dependencies

- **WHEN** a search query is executed
- **THEN** the system combines individual results from all indexed CAS dependencies

### Requirement: Lock-Free Read Access

Search tools and file readers SHALL access CAS directories without acquiring any shared or exclusive filesystem locks.

- CAS directories MUST be treated as immutable once they are moved into the cache.

#### Scenario: High-concurrency search

- **WHEN** multiple tool calls search the same project simultaneously
- **THEN** they all read the manifest and perform searching with zero lock contention
