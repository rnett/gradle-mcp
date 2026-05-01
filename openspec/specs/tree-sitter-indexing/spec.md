# Capability: tree-sitter-indexing

## Purpose

Provides advanced symbol extraction and indexing using Tree-Sitter grammars for high-accuracy declaration search across multiple programming languages.

## Requirements

### Requirement: Automated Tree-Sitter Parser Management

The system SHALL automatically download, verify, and cache Tree-Sitter parser libraries for the required languages.

#### Scenario: Download and cache a new parser

- **WHEN** the system needs to extract symbols from a Java source file for the first time
- **THEN** it SHALL download the `tree-sitter-java` library for the current platform from a trusted source.
- **AND** it SHALL verify the SHA-256 hash of the downloaded library against a bundled manifest.
- **AND** it SHALL cache the library in a global directory (e.g., `~/.mcps/rnett-gradle-mcp/cache/tree-sitter-language-pack`) for reuse.

#### Scenario: Parser extraction from bundle

- **WHEN** a parser is part of a language group (e.g., `kotlin`, `java`) in a compressed bundle
- **THEN** the system SHALL download the bundle, extract the required language libraries, and cache them individually.

### Requirement: Secure Parser Execution

The system SHALL ensure that downloaded parser libraries are verified before being loaded into the JVM.

#### Scenario: Verification of downloaded libraries

- **WHEN** loading a cached parser library
- **THEN** the system SHALL verify its SHA-256 hash against the manifest.
- **AND** it SHALL NOT load the library if the hash does not match, reporting a security error.

### Requirement: Thread-Safe Parser Access

The system SHALL manage concurrent access to parser libraries to prevent race conditions during downloading and indexing.

#### Scenario: Concurrent indexing requests for the same language

- **WHEN** multiple indexing tasks request the same language parser simultaneously
- **THEN** the system SHALL use a mutex to ensure the parser is only downloaded or extracted once.

### Requirement: Accurate Declaration Extraction

The system SHALL use Tree-Sitter's concrete syntax tree (CST) to extract declarations with high precision.

#### Scenario: Extracting nested declarations

- **WHEN** parsing a Kotlin file with nested classes and functions
- **THEN** the system SHALL correctly identify and extract all declarations, including their fully qualified names (FQNs) based on the syntax structure.
- **AND** it SHALL associate the correct symbol type (e.g., `CLASS`, `METHOD`) with each extracted item.
