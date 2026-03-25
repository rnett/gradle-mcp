# Capability: dependency-source-search

## Purpose

TBD

## ADDED Requirements

### Requirement: Search Result Import and Package De-prioritization

The system SHALL identify matches found within `import` and `package` statements and apply a score penalty to them. This penalty SHALL be sufficient to ensure that matches in actual code (class definitions, methods, etc.) appear above these
matches for the same search term.

#### Scenario: Search for common type like 'List'

- **WHEN** searching for "List" in dependency sources
- **THEN** matches in class bodies and declarations SHALL be ranked higher than matches in `import` or `package` statements
- **AND** import- and package-only matches SHALL appear lower in the result set

### Requirement: Clean Search Snippets

The system SHALL provide clean snippets in search results that focus on the match context. The snippet SHALL NOT contain redundant line number prefixes in the content.

#### Scenario: Display search result snippet

- **WHEN** formatting a search result for display
- **THEN** the snippet content SHALL contain only the lines of source code
- **AND** no internal line number labels SHALL be added to the snippet text itself
