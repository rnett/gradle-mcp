## ADDED Requirements

### Requirement: Granular Processing Phases

The `DefaultSourcesService.processDependencies` SHALL distinguish between extraction and indexing phases by updating the leftmost part of the progress message while maintaining the dependency identifier.

#### Scenario: Extraction phase reported

- **WHEN** a dependency's sources are being extracted
- **THEN** the progress message SHALL start with "Extracting sources for " followed by the dependency ID (e.g., `Extracting sources for com.example:lib:1.0.0`)

#### Scenario: Indexing phase reported

- **WHEN** a dependency's sources are being indexed for the same dependency
- **THEN** the progress message SHALL update its leftmost part to "Indexing sources for " followed by the same dependency ID (e.g., `Indexing sources for com.example:lib:1.0.0`)
