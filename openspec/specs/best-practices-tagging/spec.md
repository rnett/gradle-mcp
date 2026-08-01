# Capability: best-practices-tagging

## Purpose

Specifies automated detection and multi-tag support for Gradle documentation best-practices content during indexing.

## Requirements

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

### Requirement: Automated Upgrading Page Detection

The indexing system SHALL automatically detect and apply the `upgrading` tag to Gradle documentation files whose paths indicate version-migration content.

#### Scenario: Detecting upgrading page in userguide

- **WHEN** indexing a file with a path like `userguide/upgrading_version_9.md` or `userguide/upgrading_major_version_9.md`
- **THEN** the index entry for this file MUST include the `upgrading` tag in addition to the `userguide` base tag.

#### Scenario: Non-upgrading userguide pages are not tagged

- **WHEN** indexing a file with a path like `userguide/configuration_cache.md` or `userguide/how_to_prevent_accidental_dependency_upgrades.md`
- **THEN** the index entry MUST NOT include the `upgrading` tag.
