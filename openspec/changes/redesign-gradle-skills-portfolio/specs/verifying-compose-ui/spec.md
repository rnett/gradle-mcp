# Capability: verifying-compose-ui

## Description
Visually verifies Compose UI components and previews by rendering them to images from the JVM runtime.

### Use when
- Rendering a specific Composable function or an `@Preview` to an image.
- Capturing state transitions of UI components via sequence of images.
- Troubleshooting visual regressions or rendering issues in Compose.

### Do NOT use
- Operating a Gradle build (use `using-gradle`).
- Modifying build definitions (use `authoring-gradle-builds`).
- Probing non-visual project logic (use `interacting-with-project-runtime`).

## ADDED Requirements

### Requirement: Compact Capability Body
MUST preserve the discovery, rendering, and state-transition workflows within a compact, single-file capability body.

#### Scenario:
An agent needs to verify the visual correctness of a newly created Composable; it uses the `verifying-compose-ui` skill to find the component and render it to an image.

### Requirement: Local Troubleshooting Reference
MUST maintain a dedicated root-local troubleshooting reference for rendering and environment issues.

#### Scenario:
The agent encounters a `ClassCastException` during rendering; it consults the local troubleshooting reference to determine if it's a version mismatch or a missing runtime dependency.

### Requirement: Shared Setup Materialization
MUST implement the shared setup plumbing as an `authored-shared` resource, materialized into this skill and `interacting-with-project-runtime`.

#### Scenario:
To render a UI component, the agent must initialize a JVM with specific Compose-compatible flags; it uses the materialized shared setup resource to prepare the environment.
