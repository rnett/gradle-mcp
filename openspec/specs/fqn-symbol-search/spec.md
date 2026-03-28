# Capability: fqn-symbol-search

## Purpose

Enables searching for symbols by fully qualified names, navigating packages as directory structures, and filtering symbol searches by declaration type.

## Requirements

### Requirement: Fully Qualified Name (FQN) Symbol Search

The system SHALL allow users to search for symbols by their fully qualified names (e.g., `org.gradle.api.Project`).

#### Scenario: Searching for an exact FQN match

- **WHEN** searching for "org.gradle.api.Project" using the SYMBOLS search type
- **THEN** the system returns only the exact match for that class.

#### Scenario: Field-level symbol search

- **WHEN** a SYMBOLS search query includes a field prefix (e.g., `name:MyClass` or `fqn:com.example.*`)
- **THEN** it SHALL only search that specific field.
- **AND** `fqn` field matching SHALL be non-tokenized (matching the full literal path).

#### Scenario: Regex search for FQNs

- **WHEN** a SYMBOLS search query is wrapped in `/` (e.g., `/.*\.internal\..*/`)
- **THEN** the system SHALL perform a full regular expression match on the `fqn` field.

### Requirement: Package Navigation and Search

The system SHALL treat packages like directory structures, allowing users to "read" a package to see all symbols contained within it.

#### Scenario: Listing symbols in a package

- **WHEN** a package (e.g., "org.gradle.api") is read using the `read_dependency_sources` tool
- **THEN** the system returns a list of sub-packages and symbols defined in that package.

### Requirement: Improved Regex Search for Symbols

Symbol searches SHALL support refined regex patterns, including options to restrict the search to specific types (e.g., only classes, only functions).

#### Scenario: Filtering symbol search by type

- **WHEN** a user searches for "Configuration" with a type filter for "interface"
- **THEN** only interface declarations matching "Configuration" are returned.
