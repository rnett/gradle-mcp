## ADDED Requirements

### Requirement: High-Impact Operational Footgun Body Rules

The `using-gradle` `SKILL.md` body SHALL carry the `[Runs builds]` hardest-to-figure-out highlights and High-severity cross-cutting rules as always-loaded operational rules. The rules SHALL cover, where applicable, interpreting task outcomes (`EXECUTED`, `UP-TO-DATE`, `FROM-CACHE`, `NO-SOURCE`, `SKIPPED`, and `EXCLUDED`) before treating success as proof of work, `--continue`, `--offline`, and `--warning-mode` footguns, dependency cache TTL versus `--refresh-dependencies`, same-version daemon scope for `--status` and `--stop`, wrapper checksum verification, `--scan` metadata publication, and `--warning-mode=fail` as a migration gate rather than a default.

#### Scenario: Interpret an operational result

- **WHEN** an agent runs or diagnoses a Gradle task
- **THEN** the body directs it to inspect the task outcome before concluding that work occurred
- **AND** it applies the relevant flag, cache, daemon, wrapper, or scan warning before interpreting the result

#### Scenario: Apply version-sensitive operational guidance

- **WHEN** an operational footgun is marked version-sensitive
- **THEN** the body or linked reference directs the agent to read the wrapper version first
- **AND** the agent checks the exact Gradle version before applying the guidance

### Requirement: Authored Operational Best-Practice References

The `using-gradle` skill SHALL provide authored references carrying the remaining `[Runs builds]` recommendations and all do/don't snippets. Guidance SHALL be woven into `running-builds.md`, `troubleshooting.md`, `build-environment.md`, `dependencies.md`, `testing.md`, and `research.md`, or into new authored-local files where no natural home exists. The references SHALL preserve the frozen corpus and route authoritative documentation through `gradle_docs` hints.

#### Scenario: Load focused operational guidance

- **WHEN** an agent operates, diagnoses, tests, or researches an existing Gradle build
- **THEN** it loads the corresponding authored reference for the recommendation and its do/don't snippet
- **AND** it follows the linked `gradle_docs` hint for version-specific authority

#### Scenario: Preserve generated-corpus routing

- **WHEN** authored operational guidance needs generated best-practice rationale
- **THEN** it preserves the frozen corpus and links through its `_index.md` entry and detail file
- **AND** it does not replace or restate generated content
