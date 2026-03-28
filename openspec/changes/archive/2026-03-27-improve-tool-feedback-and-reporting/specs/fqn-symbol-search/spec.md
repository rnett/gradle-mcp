## MODIFIED Requirements

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
