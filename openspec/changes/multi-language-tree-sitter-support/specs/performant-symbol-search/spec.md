## ADDED Requirements

### Requirement: Performance Parity Across Languages

The indexing and search performance for newly supported languages SHALL be comparable to that of Java and Kotlin. The C FFI `ts_pack_process()` approach (single FFI call per file) SHALL be at least as efficient as the previous parse →
query → traverse pipeline.

#### Scenario: Indexing a large multi-language project

- **WHEN** indexing a project containing a mix of Java, C++, and Python files
- **THEN** the total indexing time SHALL be proportional to the total number of files and their complexity
- **AND** the single `ts_pack_process()` call per file SHALL not introduce significant overhead compared to the previous multi-step approach

### Requirement: Concurrent Extraction

The system SHALL support concurrent symbol extraction from multiple files without corruption or crashes. The C FFI `ts_pack_process()` is stateless and thread-safe.

#### Scenario: Parallel indexing

- **WHEN** multiple files are indexed concurrently
- **THEN** each `ts_pack_process()` call SHALL return correct results for its input file
- **AND** no shared mutable state SHALL be accessed without synchronization
