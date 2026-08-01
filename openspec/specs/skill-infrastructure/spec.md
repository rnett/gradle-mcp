# Capability: skill-infrastructure

## Description
Governs the materialization, indexing, documentation, and packaging of the project's skill portfolio.

### Use when
- Implementing the deterministic materialization of shared or generated skill resources.
- Generating root-local `_index.md` reference maps for skills.
- Splicing the generated skill inventory into `docs/skills.md`.
- Packaging the finalized skill set into `skills.zip`.

## Purpose

This capability defines the workflows and requirements for skill infrastructure.
## Requirements
### Requirement: Deterministic Materialization
MUST implement an idempotent `materializeSkills` process that fans out `authored-shared` and `generated` sources into skill roots.

#### Scenario:
A project update changes a shared setup resource; `materializeSkills` is run by the build system and atomically updates the identical file in both `interacting-with-project-runtime` and `verifying-compose-ui` roots.

### Requirement: Materialization Validation
MUST implement `verifySkillsMaterialized` to gate the `check` task by verifying shared fan-out, generated content hashes, index completeness, and the documentation-routing invariant that no skill markdown file contains a URL whose host is `docs.gradle.org` or `gradle-mcp.rnett.dev`. The URL invariant SHALL be a host blocklist scoped to those two documentation-citation hosts; other external URLs (for example the skill `author:` metadata URL, Maven Central Portal guides, and example or license URLs inside code snippets) SHALL NOT be flagged.

#### Scenario: Detect manual drift
- **WHEN** a developer manually edits a materialized file in a skill root instead of the source
- **THEN** `verifySkillsMaterialized` detects the drift from the authoritative source and fails the build

#### Scenario: Reject a blocked documentation URL
- **WHEN** any skill markdown file (authored or generated) contains a URL whose host is `docs.gradle.org` or `gradle-mcp.rnett.dev`
- **THEN** `verifySkillsMaterialized` reports the file and URL as a violation and fails the build

#### Scenario: Permit non-documentation external URLs
- **WHEN** a skill markdown file contains an external URL whose host is not `docs.gradle.org` or `gradle-mcp.rnett.dev` (for example `central.sonatype.org` or the `author:` metadata URL)
- **THEN** `verifySkillsMaterialized` does not flag it

### Requirement: Index Generation
MUST generate a `references/_index.md` for every skill, mapping procedures to resources and defining "load-when" triggers.

#### Scenario:
The author adds a new "Dependency Audit" procedure to `using-gradle`; the generator automatically updates `references/_index.md` to include the new mapping.

### Requirement: Documentation Splicing
MUST implement the START/END markerL-based splicing in `UpdateSkills.kt` to maintain `docs/skills.md` without destroying manually authored content.

#### Scenario:
The project updates the skill portfolio identity and descriptions; the splicing logic replaces only the metadata section of `docs/skills.md`, preserving the manually written "Getting Started" guide.

### Requirement: Package and Install Guardrails
MUST assert that the source set, `skills.zip` entries, and installed directory set exactly equal the four-name portfolio inventory.
MUST verify that `replaceOld=true` correctly deletes target directories marked with the repository author string.

#### Scenario:
An installer is run with `replaceOld=true` on a system with a stale 6-skill taxonomy; the tool deletes the obsolete skill directories and replaces them with the new 4-skill set.

