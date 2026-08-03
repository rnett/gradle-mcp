# Capability Deltas: using-gradle

## MODIFIED Requirements

### Requirement: Broad Operational Index
The skill MUST provide a compact workflow index that directs an AI feature developer operating an existing Gradle build to specialized procedures for project mapping, execution, testing, diagnosis, dependency inspection, and source research. This MUST include routing to the new `diagnostic-tasks.md` reference.

#### Scenario: Select an execution procedure
- **WHEN** an agent needs to run an unfamiliar task
- **THEN** it reads the wrapper version, maps projects, discovers the task with authoritative task help, and loads the running-builds reference before invoking the Gradle MCP execution tools

#### Scenario: Interpret a successful task
- **WHEN** a task returns a successful result
- **THEN** the agent reads whether it was `EXECUTED`, `UP-TO-DATE`, `FROM-CACHE`, `NO-SOURCE`, `SKIPPED`, or `EXCLUDED` before treating the result as proof that work occurred
- **AND** it uses targeted `--rerun` rather than broad `--rerun-tasks` when the wrapper supports `--rerun`, with a documented fallback for older Gradle versions

#### Scenario: Handle execution footguns
- **WHEN** an agent uses task-execution flags while diagnosing a build
- **THEN** the running-builds guidance explains that `--continue` does not run tasks whose prerequisites failed, `--offline` can reuse stale cached dependencies, and `--warning-mode` changes deprecation reporting rather than fixing the warning

#### Scenario: Execute a diagnostic task
- **WHEN** an agent needs to diagnose a build issue using a reporting task
- **THEN** it consults `diagnostic-tasks.md` to find the appropriate core task for the use-case (e.g. `dependencies`, `dependencyInsight`) or uses the discovery rule for plugin-provided reports

### Requirement: Version-Aware Guidance
The skill MUST mark version-dependent advice inline for Gradle 7, 8, and 9, provide compatibility-safe fallbacks, bias guidance toward the latest supported wrapper version, and include a compact compatibility quick-reference. This MUST include a specific "smell" guidance for the high cost of `--rerun-tasks` vs the targeted nature of `--rerun`.

#### Scenario: Choose a version-safe operation
- **WHEN** an agent works across Gradle 7, 8, and 9
- **THEN** it selects the latest-version path when possible and uses documented fallbacks for `--rerun`, version catalogs, configuration-cache maturity, `properties --property`, toolchain provisioning, and daemon controls when the wrapper is older or uncertain

#### Scenario: Separate JVM roles
- **WHEN** an agent diagnoses a JVM or toolchain failure
- **THEN** it distinguishes the JVM running Gradle from the project compile/test toolchain and test workers, and selects `JAVA_HOME` from the wrapper compatibility matrix rather than from source compatibility

#### Scenario: Diagnose stateful environment behavior
- **WHEN** an agent compares runs that differ in daemon, wrapper, or cache state
- **THEN** it records `GRADLE_USER_HOME`, respects same-version daemon scope for `--status` and `--stop`, checks wrapper distribution checksums, and does not bypass checksum verification

#### Scenario: Handle policy-sensitive diagnostics
- **WHEN** an agent considers `--scan` or stricter warning handling
- **THEN** it is warned that build scans publish build metadata and may require terms acceptance, and that `--warning-mode=fail` is an intentional migration gate rather than a default diagnostic setting

#### Scenario: Choose efficient rerun strategy
- **WHEN** an agent needs to force a task to rerun
- **THEN** it uses `--rerun` for the specific task when supported, and recognizes that needing `--rerun-tasks` is a smell indicating errors in the build logic's output/input tracking

### Requirement: High-Impact Operational Footgun Body Rules

The `using-gradle` `SKILL.md` body SHALL carry the `[Runs builds]` hardest-to-figure-out highlights and High-severity cross-cutting rules as always-loaded operational rules. The rules SHALL cover, where applicable, interpreting task outcomes (`EXECUTED`, `UP-TO-DATE`, `FROM-CACHE`, `NO-SOURCE`, `SKIPPED`, and `EXCLUDED`) before treating success as proof of work, `--continue`, `--offline`, and `--warning-mode` footguns, dependency cache TTL versus `--refresh-dependencies`, same-version daemon scope for `--status` and `--stop`, wrapper checksum verification, `--scan` metadata publication, `--warning-mode=fail` as a migration gate rather than a default, and the `--rerun` versus `--rerun-tasks` distinction: `--rerun` re-runs a specific task, while `--rerun-tasks` re-runs everything, including included builds, is extremely expensive, and should almost never be used — needing `--rerun-tasks` is a smell for build-logic errors in output/input tracking.

#### Scenario: Interpret an operational result

- **WHEN** an agent runs or diagnoses a Gradle task
- **THEN** the body directs it to inspect the task outcome before concluding that work occurred
- **AND** it applies the relevant flag, cache, daemon, wrapper, or scan warning before interpreting the result

#### Scenario: Apply version-sensitive operational guidance

- **WHEN** an operational footgun is marked version-sensitive
- **THEN** the body or linked reference directs the agent to read the wrapper version first
- **AND** the agent checks the exact Gradle version before applying the guidance

### Requirement: Authored Operational Best-Practice References
The skill SHALL provide authored references carrying the remaining `[Runs builds]` recommendations and all do/don't snippets. Guidance SHALL be woven into `running-builds.md`, `troubleshooting.md`, `build-environment.md`, `dependencies.md`, `testing.md`, and `research.md`, and lazed-configuration/IP-compatibility guidance SHALL be woven where operationally sensible.

#### Scenario: Load focused operational guidance
- **WHEN** an agent operates, diagnoses, tests, or researches an existing Gradle build
- **THEN** it loads the corresponding authored reference for the recommendation and its do/don't snippet
- **AND** it follows the linked `gradle_docs` hint for version-specific authority

#### Scenario: Preserve generated-corpus routing
- **WHEN** authored operational guidance needs generated best-practice rationale
- **THEN** it preserves the frozen corpus and links through its `_index.md` entry and detail file
- **AND** it does not replace or restate generated content

## ADDED Requirements

### Requirement: Diagnostic Task Coverage (Topic 10)
The skill MUST provide a use-case driven matrix of core diagnostic tasks and a discovery rule for identifying plugin-contributed reporting tasks.

#### Scenario: Discover plugin-specific reports
- **WHEN** an agent needs to find if a plugin provides a specialized reporting task
- **THEN** it applies the discovery rule (e.g. `tasks --all` or `help --task`) to identify reports that are not part of the core Gradle diagnostic set
