## ADDED Requirements

### Requirement: Resolved versions for source research

The system SHALL use concrete versions resolved from aliases when searching for or reading source code for a specific version of Gradle.

#### Scenario: Research internal APIs with "current"

- **WHEN** the user researches internal APIs for version `"current"`
- **THEN** the system SHALL resolve `"current"` to a concrete version (e.g., `"8.6.1"`) and use that version for all source-level research cache directories (e.g., `.../8.6.1/`)
