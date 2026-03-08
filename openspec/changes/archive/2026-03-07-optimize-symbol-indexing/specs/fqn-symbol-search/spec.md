## ADDED Requirements

### Requirement: Fully Qualified Name (FQN) Symbol Search

The system SHALL allow users to search for symbols by their fully qualified names (e.g., `org.gradle.api.Project`).

#### Scenario: Searching for an exact FQN match

- **WHEN** searching for "org.gradle.api.Project" using the SYMBOLS search type
- **THEN** the system returns only the exact match for that class.

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
