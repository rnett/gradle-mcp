# Proposal: Redesign Gradle Skill Portfolio

## Why

The existing Gradle skill fragmentation—split across six directories—creates a high selection overhead and fragments agent context. Prior attempts to solve this with a complex router and hyperspecialized "expert" roles increased complexity without providing measurable benefits.

This proposal implements the architect-approved fundamental redesign: consolidating the portfolio into exactly four skills based on the stable boundary between **operating existing builds** and **authoring build definitions**, while keeping the distinct runtime and visual verification capabilities separate.

## What Changes

### 1. Portfolio Consolidation
The portfolio is reduced to exactly four installed skills:
- `using-gradle`: The broad workflow index for engineers inspecting and operating an existing build.
- `authoring-gradle-builds`: The broad workflow index for engineers modifying build definitions and wiring.
- `interacting-with-project-runtime`: A focused capability for persistent JVM/Kotlin REPL execution.
- `verifying-compose-ui`: A focused capability for image-based Compose rendering and verification.

### 2. Information Architecture
We transition to a three-level disclosure model:
1. **Metadata**: High-level discovery via names and precise `Use when`/`Do NOT use` descriptions.
2. **Workflow Index**: Compact `SKILL.md` bodies (≤150 lines) focusing on audience, operating contracts, and decision-routing tables.
3. **Local References**: Task-shaped, root-local indexed references for specific procedures (capability bodies ≤220 lines).

### 3. Behavioral Shift
The "Router" is entirely removed. Routing is handled by the host using the refined metadata. Cross-skill handoffs (e.g., `using-gradle` for diagnosis $\rightarrow$ `authoring-gradle-builds` for fix $\rightarrow$ `using-gradle` for verification) are established via semantic guidance in the bodies.

### 4. Technical Infrastructure
- **Provenance**: Every reference is classified by provenance (`authored-local`, `authored-shared`, `generated`) with mandatory deterministic headers.
- **Materialization**: A deterministic `materializeSkills` process handles shared and generated outputs (e.g., best-practices, `_index.md`), gating the `check` task via `verifySkillsMaterialized`.
- **Packaging**: The `skills.zip` and installation sets are strictly limited to the four-name inventory.
- **Documentation**: `UpdateSkills.kt` now uses a marker-based splicing approach to update `docs/skills.md` without destroying surrounding content.

## Canonical Migration Map

| Current Source | Destination | Semantics |
| :--- | :--- | :--- |
| `gradle` | `using-gradle` & `authoring-gradle-builds` | Split by operation vs. modification boundary. |
| `gradle-build-authoring` | `authoring-gradle-builds` | Rename and preserve all authoring/best-practice refs. |
| `exploring_dependency_sources` | `using-gradle` | Merge into progressive-disclosure references. |
| `managing_gradle_dependencies` | `using-gradle` & `authoring-gradle-builds` | Inspection $\rightarrow$ Using; Declaration/Wiring $\rightarrow$ Authoring. |
| `interacting_with_project_runtime` | `interacting-with-project-runtime` | Rename; compact into capability body. |
| `verifying_compose_ui` | `verifying-compose-ui` | Rename; compact into capability body. |

## Final Installed Inventory
1. `using-gradle` (v1.0.0)
2. `authoring-gradle-builds` (v1.0.0)
3. `interacting-with-project-runtime` (v1.0.0)
4. `verifying-compose-ui` (v1.0.0)

## Impact
- **Implementation**: Extensive migration of skill sources and restructuring of the generator/package/install logic.
- **Maintenance**: Reduced sprawl; deterministic docs and packaging.
- **User Experience**: Clearer mental model for skill selection and more concise initial context.
