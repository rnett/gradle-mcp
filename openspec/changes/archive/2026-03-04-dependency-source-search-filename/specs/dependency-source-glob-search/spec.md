## ADDED Requirements

### Requirement: Search for any file in dependencies using glob patterns

The system SHALL allow users to search for any file (including non-source files) within dependency source trees using standard glob syntax.

#### Scenario: Find all XML files

- **WHEN** user searches with glob pattern `**/*.xml`
- **THEN** system returns all files ending in `.xml` across all directory levels in the dependencies.

#### Scenario: Find files with specific name in any directory

- **WHEN** user searches with glob pattern `**/LICENSE`
- **THEN** system returns all files named `LICENSE` regardless of their directory.

### Requirement: Provide high-signal file snippets for matches

The system SHALL provide a high-signal snippet of the file content for matches found via glob search.

#### Scenario: Snippet for a matched file

- **WHEN** a file matches the glob pattern
- **THEN** the search result includes the relative path and a snippet that skips blank lines, package/import declarations, and leading comments (including multiline blocks).

### Requirement: Use indices for performance

The system SHALL use persistent indices to perform glob searches to ensure high performance even with a large number of dependencies.

#### Scenario: Fast retrieval from index

- **WHEN** user performs a glob search on already-indexed sources
- **THEN** results are returned within milliseconds without walking the filesystem.
