## ADDED Requirements

### Requirement: Paginated Documentation Search

The `gradle_docs` tool with `query` SHALL support `offset` and `limit` to navigate search results efficiently.

#### Scenario: Documentation search with pagination

- **WHEN** `gradle_docs` is called with `query="kotlin"` and `limit=3`
- **THEN** the system SHALL return at most 3 documentation search results

### Requirement: Paginated Documentation Page List

The `gradle_docs` tool SHALL support pagination when listing all available pages.

#### Scenario: Listing documentation pages with pagination

- **WHEN** `gradle_docs` is called with no `query` or `path`, using `limit=15`
- **THEN** the system SHALL return only 15 documentation page titles in the list
