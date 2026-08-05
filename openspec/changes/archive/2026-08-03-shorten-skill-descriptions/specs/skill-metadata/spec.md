## MODIFIED Requirements

### Requirement: Frontmatter negative triggers for routing-ambiguous skills

Skills that overlap with or route similarly to other skills SHALL document explicit negative routing guidance at two levels. Their frontmatter description SHALL state the primary negative boundary needed for discovery-time routing (for example, "Do NOT use for ..."), and their `SKILL.md` body SHALL contain a Negative Triggers section with detailed cases and cross-skill pointers.

#### Scenario: Build-authoring skill discovery with negative triggers

- **WHEN** the agent needs to modify a `build.gradle.kts` file or create a convention plugin
- **THEN** it finds the `gradle-build-authoring` skill description with positive triggers ("build.gradle(.kts) modification", "convention plugins", "build performance")
- **AND** the negative trigger on `gradle` ("Do NOT use for build script authoring or plugin development") prevents mis-routing

#### Scenario: Build execution skill excludes build-authoring

- **WHEN** the agent reads the `gradle` skill description
- **THEN** it sees explicit exclusion of build script authoring and plugin development (use `gradle-build-authoring`)
- **AND** it will not route build-authoring questions to the `gradle` skill

#### Scenario: Routing-ambiguous skill metadata

- **WHEN** a shipped skill overlaps another skill's routing domain
- **THEN** its frontmatter states the primary negative boundary within the description budget
- **AND** its body contains detailed negative cases and cross-skill pointers

#### Scenario: Routing guidance is not duplicated in frontmatter

- **WHEN** a routing-ambiguous skill has several negative cases
- **THEN** the complete list appears in the body Negative Triggers section
- **AND** frontmatter retains only the discovery-critical boundary
