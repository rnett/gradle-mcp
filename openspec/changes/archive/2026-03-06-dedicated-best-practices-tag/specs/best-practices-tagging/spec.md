## ADDED Requirements

### Requirement: Automated Best Practices Detection

The indexing system SHALL automatically detect and apply the `best-practices` tag to relevant Gradle documentation files.

#### Scenario: Detecting best practice file in userguide

- **WHEN** indexing a file with a path like `userguide/best_practices_general.md`
- **THEN** the index entry for this file MUST include the `best-practices` tag.

### Requirement: Multi-Tag Document Support

The documentation index SHALL support multiple tags for a single document to allow broad and specialized queries.

#### Scenario: Document with multiple tags

- **WHEN** a best practice document from the `userguide` is indexed
- **THEN** it MUST be searchable via both `tag:userguide` and `tag:best-practices`.
