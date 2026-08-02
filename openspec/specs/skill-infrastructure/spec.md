# Capability: skill-infrastructure

## Description
Governs the indexing, documentation, and packaging of the project's skill portfolio.

### Use when
- Generating root-local `_index.md` reference maps for skills.
- Splicing the generated skill inventory into `docs/skills.md`.
- Packaging the finalized skill set into `skills.zip`.

## Purpose
This capability defines the workflows and requirements for skill infrastructure.

## Requirements
### Requirement: Skill Reference Indexing
MUST maintain a complete reference index for each skill, mapping procedures to resources and defining "load-when" triggers.

#### Scenario:
The author adds a new "Dependency Audit" procedure to `using-gradle`; the skill reference index is updated to include the new mapping.

### Requirement: Documentation Splicing
MUST implement the START/END marker-based splicing in `UpdateSkills.kt` to maintain `docs/skills.md` without destroying manually authored content.

#### Scenario:
The project updates the skill portfolio identity and descriptions; the splicing logic replaces only the metadata section of `docs/skills.md`, preserving the manually written "Getting Started" guide.

### Requirement: Package and Install Guardrails
MUST assert that the source set, `skills.zip` entries, and installed directory set exactly equal the four-name portfolio inventory.
MUST verify that `replaceOld=true` correctly deletes target directories marked with the repository author string.

#### Scenario:
An installer is run with `replaceOld=true` on a system with a stale 6-skill taxonomy; the tool deletes the obsolete skill directories and replaces them with the new 4-skill set.
