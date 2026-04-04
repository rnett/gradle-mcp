## ADDED Requirements

### Requirement: Deterministic Project Path Resolution

The system SHALL require a specific project path (or root `:`) when resolving and searching dependency sources to ensure deterministic resolution.

#### Scenario: Searching sources with project path

- **WHEN** a user searches dependency sources providing a specific project path
- **THEN** the system SHALL resolve dependencies against that specific project's configuration
- **AND** return search results from those resolved dependencies

## REMOVED Requirements

### Requirement: "All Sources" Scope

**Reason**: Inherently ambiguous in multi-project builds because version resolution is not uniquely determined across subprojects.
**Migration**: Always provide a specific project path (e.g., `:` for root or `:app` for a subproject) when calling `search_dependency_sources` or `read_dependency_sources`.
