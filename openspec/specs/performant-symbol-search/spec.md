# Capability: performant-symbol-search

## Purpose

Specifies the use of Lucene-based indexing for high-performance symbol search with incremental indexing and low memory overhead.

## Requirements

### Requirement: Lucene-based Symbol Indexing

The system SHALL use Lucene to index and search for symbols in project dependency sources. This replaces the flat-file, line-by-line regex search previously used.

#### Scenario: Searching for a symbol in a large dependency

- **WHEN** searching for "Project" in the Gradle source set
- **THEN** the system returns results from the Lucene-indexed symbol store within 500ms for large codebases.

### Requirement: Incremental Indexing for Symbols

The system SHALL support incremental indexing of symbols, where only modified or new sources are re-indexed.

#### Scenario: Fast re-index after minor change

- **WHEN** only one source file is added or modified in a dependency
- **THEN** only that file's symbols are re-indexed into the Lucene index.

### Requirement: Low Memory Overhead

Symbol searching MUST be performed without loading the entire index into memory.

#### Scenario: Memory-efficient search

- **WHEN** performing a search across multiple large libraries
- **THEN** the JVM heap usage for the search operation remains significantly lower than the total size of the indices.
