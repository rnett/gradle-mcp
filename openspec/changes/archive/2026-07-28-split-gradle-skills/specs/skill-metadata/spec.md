# Capability: skill-metadata

## ADDED Requirements

### Requirement: Frontmatter negative triggers for routing-ambiguous skills

Skills that overlap with or route similarly to other skills SHALL include explicit negative triggers in their frontmatter description ("Do NOT use for ...") that prevent routing ambiguity.

#### Scenario: Build-authoring skill discovery with negative triggers

- **WHEN** the agent needs to modify a `build.gradle.kts` file or create a convention plugin
- **THEN** it finds the `gradle-build-authoring` skill description with positive triggers ("build.gradle(.kts) modification", "convention plugins", "build performance")
- **AND** the negative trigger on `gradle` ("Do NOT use for build script authoring or plugin development") prevents mis-routing

#### Scenario: Build execution skill excludes build-authoring

- **WHEN** the agent reads the `gradle` skill description
- **THEN** it sees explicit exclusion of build script authoring and plugin development (use `gradle-build-authoring`)
- **AND** it will not route build-authoring questions to the `gradle` skill