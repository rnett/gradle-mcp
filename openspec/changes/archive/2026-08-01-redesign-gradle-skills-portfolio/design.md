# Design: Gradle Skill Portfolio Redesign

## Decision

Install exactly four public skills, all starting at semantic version `1.0.0`:

1. `using-gradle`: Broad workflow index for operating/inspecting existing builds.
2. `authoring-gradle-builds`: Broad workflow index for modifying build definitions.
3. `interacting-with-project-runtime`: Focused capability for non-visual persistent JVM/Kotlin REPL execution.
4. `verifying-compose-ui`: Focused capability for image-based Compose rendering and verification.

The fundamental boundary is **operation versus modification**. `using-gradle` handles everything from task execution to internal source research. `authoring-gradle-builds` handles everything that changes the build's structure or configuration.

### Portable Naming Authority
The naming of shipped cross-host skills is governed by the official Agent Skills specification: names must be 1–64 lowercase alphanumeric characters or hyphens, with no leading, trailing, or consecutive hyphens, and the skill name must exactly match its parent directory. This authoritative standard explicitly overrides any local `snake_case` authoring guidance. The finalized portfolio adheres to this via the four kebab-case names: `using-gradle`, `authoring-gradle-builds`, `interacting-with-project-runtime`, and `verifying-compose-ui`.

## Information Architecture

### Three-Level Disclosure
To minimize default-context noise while preserving depth:
1. **Metadata**: Host discovery via kebab-case names and precise `Use when` / `Do NOT use` flags.
2. **Workflow Index**: `SKILL.md` bodies (max 150 lines) focused on audience, operating contracts, and a decision-routing table.
3. **Local References**: Task-shaped, root-local references (max 220 lines) containing specific procedures.

### Reference Provenance
Every resource in a skill root must have a deterministic provenance header:
- `authored-local`: Written once in the skill directory.
- `authored-shared`: Written in a canonical shared source, materialized into the skill.
- `generated`: Produced by a tool (e.g., Best Practices generator), containing version/hash.

### Materialization and Indexing
- **Materialization**: Shared and generated resources are materialized idempotently. `verifySkillsMaterialized` gates the build, ensuring no manual edits to materialized files.
- **Indexing**: Every skill contains a generated `references/_index.md` mapping procedures to resources with defined "load-when" triggers.

## Detailed Ownership

### `using-gradle`
**Scope**: Read-only inspection, execution, and diagnosis of any Gradle build.
- **Project/Task Mapping**: Hierarchy, task discovery, property inspection.
- **Execution**: Foreground/background runs, monitoring, output capture.
- **Diagnosis**: Test failure isolation, diagnostic tasks, `query_build` patterns.
- ** Research**: Gradle official docs, release notes, internal APIs, and Gradle-own-source research.
- **Dependency Inspection**: Graph visualization, version conflicts, update discovery, and plugin resolution.
- ** laSource Research**: Full-text and symbol search across dependency and plugin sources.

### `authoring-gradle-builds`
**Scope**: Modifying build definitions and wiring.
- **Build Logic**: Scripts, settings, convention plugins, and plugin authoring.
- **Dependencies**: Catalogs, declarations, repositories, and constraints.
- **Configuration**: Toolchains, compiler options, test config, and lazy wiring (`Provider`/`Property`).
- **Lifecycle**: Publishing, locking, CI integration, and build scans.
- **Safety**: Strict `afterEvaluate` prohibition (with explicit last-resort exception criteria).
- **Best Practices**: Integrates the generated best-practice corpus.

### `interacting-with-project-runtime`
**Scope**: Persistent JVM/Kotlin REPL for probing project logic.
- Compacted into a single capability body.
- Shares common setup plumbing (materialized via `authored-shared`) with Compose.

### `verifying-compose-ui`
** laScope**: Visual verification of Compose components.
- Compacted into a single capability body.
- Preserves discovery and render workflows; troubleshooting in a local reference.

## Technical Infrastructure

### Metadata Contract
- **Names**: Lowercase alphanumeric kebab-case.
- **Descriptions**: Single-line, third-person gerund, precisely distinguishing the "Using" vs "Authoring" boundary.
- **Author**: `https://github.com/rnett/gradle-mcp`.

### Documentation Splicing
`UpdateSkills.kt` uses unique START/END markers to splice the generated skill list into `docs/skills.md`, preserving surrounding hand-authored content.

### Packaging and Installation
- **Set Equality**: The source set, `skills.zip` content, and installed directories must equal the exact four-name inventory.
- **Installation**: `replaceOld=true` deletes any directory marked with the repository author string.
- **Rollback**: Complete implementation revert and re-installation of the prior verified artifact.

## Behavioral Corpus (Non-Measured)
The redesign is evaluated against a qualitative rubric of common engineering journeys:
- **Investigative Loop**: `using-gradle` (Filtered test fails) $\rightarrow$ `using-gradle` (Query diagnostics) $\rightarrow$ `using-gradle` (Inspect dependency conflict) $\rightarrow$ `using-gradle` (Read source).
- **Modification Loop**: `using-gradle` (Identify missing dependency) $\rightarrow$ `authoring-gradle-builds` (Add to catalog) $\rightarrow$ `using-gradle` (Verify build).
- **Runtime Probe**: `interacting-with-project-runtime` (Probing state).
- **Visual Check**: `verifying-compose-ui` (Rendering preview).
