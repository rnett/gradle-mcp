## ADDED Requirements

### Requirement: Multi-Language Search Support

The system SHALL support searching declarations and sources across all languages registered in the system. This includes non-JVM languages such as C++, Rust, Python, and Go when they are present in the dependency graph.

#### Scenario: Searching for a Python function in dependencies

- **WHEN** searching for a function name in a project that has Python dependencies
- **THEN** the system SHALL return matches from `.py` files with correct line numbers and offsets
- **AND** the symbols SHALL be extracted using the multi-language tree-sitter extraction logic
