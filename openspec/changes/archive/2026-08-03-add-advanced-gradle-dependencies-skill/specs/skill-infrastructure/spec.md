# Capability Deltas: skill-infrastructure

## MODIFIED Requirements

### Requirement: Package and Install Guardrails
MUST assert that the source set, `skills.zip` entries, and installed directory set exactly equal the five-name portfolio inventory.
MUST verify that `replaceOld=true` correctly deletes target directories marked with the repository author string.

#### Scenario: Replace a stale installed taxonomy
- **WHEN** an installer is run with `replaceOld=true` on a system with a stale skill taxonomy
- **THEN** the tool deletes the obsolete skill directories marked with the repository author string and replaces them with the current five-skill set

#### Scenario: Assert the five-name inventory
- **WHEN** the packaging or install guardrails run
- **THEN** the source skill directories, `skills.zip` entries, and installed directory set are asserted to exactly equal the five-name portfolio inventory (`using-gradle`, `authoring-gradle-builds`, `interacting-with-project-runtime`, `verifying-compose-ui`, `advanced-gradle-dependencies`)
