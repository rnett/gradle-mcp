# Capability: advanced-gradle-dependencies

## Purpose

Defines the fifth shipped skill that owns advanced Gradle dependency engineering across the operate/author split as diagnose→fix loops: its identity and boundary against the four existing skills, its phased reference coverage, its cross-skill handoff contract, and its registration, packaging, documentation-routing, and doctrine obligations.

## ADDED Requirements

### Requirement: Skill Identity and Boundary
`advanced-gradle-dependencies` MUST be a dedicated fifth shipped skill that owns advanced dependency engineering as the cross-cutting diagnose→fix lane spanning the operate/author split. The skill MUST be additive: `using-gradle` MUST retain everyday dependency inspection basics and `authoring-gradle-builds` MUST retain basic dependency declaration, version-catalog basics, and locking authoring. No content MUST be moved out of the existing skills.

#### Scenario: Activate on advanced dependency work
- **WHEN** an agent receives a task requiring advanced dependency depth (variant-aware resolution diagnostics, dependency verification metadata, component metadata or selection rules, substitution/composite builds, or dependency governance)
- **THEN** it activates `advanced-gradle-dependencies` via its positive triggers
- **AND** the existing skills retain their basics unchanged

#### Scenario: Preserve existing skill content
- **WHEN** the skill ships alongside the existing portfolio
- **THEN** no reference or workflow content has been removed from `using-gradle` or `authoring-gradle-builds`
- **AND** those skills' `SKILL.md` files received only handoff routing rows and frontmatter negative-trigger bullets
- **AND** the only retained-reference change is the single D10 routing-alignment edit in `authoring-gradle-builds/references/dependencies-and-catalogs.md`, which replaces its dependency-verification enablement direction with a handoff pointer while keeping the conditional doctrine, UX-cost warning, locking-vs-verification distinction, and disable caution in place; no other retained-reference changes occur

### Requirement: Phase-1 Diagnostics and Safety Coverage
The skill MUST provide Phase-1 references covering:
- Variant-aware resolution diagnostics: attributes, the compatibility-rule vs disambiguation-rule distinction, the `outgoingVariants` report, and `dependencyInsight --all-variants`.
- Dependency verification: `verification-metadata.xml` structure, PGP key handling, checksums, and CI workflows.
- Component metadata rules and dependency selection rules.
- Dependency substitution and composite builds.

#### Scenario: Diagnose a variant selection failure
- **WHEN** variant selection fails or no matching variant is found during resolution
- **THEN** the agent loads `variant-resolution-diagnostics.md` and diagnoses via attribute inspection, the `outgoingVariants` report, and `dependencyInsight --all-variants` before authoring any compatibility or disambiguation rule

#### Scenario: Author dependency verification metadata
- **WHEN** a user explicitly asks to enable or repair dependency verification
- **THEN** the agent loads `dependency-verification.md` for `verification-metadata.xml` structure, PGP key and checksum workflows, and CI integration

### Requirement: Phase-2 Governance and Advanced Authoring Coverage
The skill MUST provide Phase-2 references covering feature variants and configuration roles, capability conflicts, dependency locking lock modes, advanced version catalog topics, repository governance modes (`dependencyResolutionManagement`, content filtering, `exclusiveContent`), and consolidated resolution mechanics (caching/freshness, resolution consistency, and performance/resolution-avoidance).

#### Scenario: Resolve a capability conflict
- **WHEN** a capability conflict or feature-variant selection problem arises
- **THEN** the agent loads `feature-variants-and-capabilities.md` for configuration roles and conflict resolution

#### Scenario: Tune resolution mechanics
- **WHEN** an agent must reason about dependency cache freshness, resolution consistency, or resolution avoidance and performance
- **THEN** `resolution-mechanics.md` consolidates those resolution-engine mechanics as the supporting reference for governance authoring

#### Scenario: Route advanced version catalog work
- **WHEN** a version-catalog task goes beyond everyday catalog entries and library declarations
- **THEN** the agent activates `advanced-gradle-dependencies` and loads `advanced-version-catalogs.md` rather than treating the task as basic catalog authoring in `authoring-gradle-builds`

### Requirement: Diagnose-then-Fix Workflow
The skill MUST structure its workflows as diagnose→fix loops: diagnose with the authoritative report first, apply the minimal authoring fix, then re-diagnose to confirm. The skill MUST enforce a diagnose-before-fix rule and MUST NOT prescribe fixes without a diagnostic step.

#### Scenario: Diagnose before fixing
- **WHEN** an agent is asked to fix an advanced dependency problem
- **THEN** it runs the matching diagnostic procedure first, applies the minimal fix, and re-runs the diagnostic to confirm resolution

### Requirement: Cross-Skill Handoff Contract
The skill MUST document bidirectional handoffs with `using-gradle` and `authoring-gradle-builds` in `## Cross-Skill Handoffs` sections of all three skills. `using-gradle` MUST route advanced dependency depth to this skill while retaining everyday inspection basics. `authoring-gradle-builds` MUST route advanced dependency depth to this skill while retaining basic declaration, version-catalog basics, and locking authoring. Each of the two existing skills MUST add one frontmatter negative-trigger bullet for advanced dependency work.

#### Scenario: Hand off from using-gradle
- **WHEN** an agent working in `using-gradle` hits a variant selection failure, verification metadata work, or substitution/composite diagnosis
- **THEN** the handoff row routes it to `advanced-gradle-dependencies`

#### Scenario: Hand off from authoring-gradle-builds
- **WHEN** an agent working in `authoring-gradle-builds` hits capability conflicts, lock modes beyond basics, advanced version catalog topics, verification metadata/key/checksum/repair/CI implementation, or repository governance modes
- **THEN** the handoff row routes it to `advanced-gradle-dependencies`
- **AND** basic dependency declaration, version-catalog basics, and locking stay in `authoring-gradle-builds`

### Requirement: Progressive Disclosure and Discoverability
The skill MUST keep a compact `SKILL.md` body with `## Decision Routing` and `## Reference Discovery` sections, and every file under `references/` MUST be reachable from the `SKILL.md` body. Detailed procedures MUST live in `references/` and load only for the corresponding task.

#### Scenario: Reach every reference
- **WHEN** a reviewer audits reference discoverability
- **THEN** every reference file is reachable from a `SKILL.md` routing or discovery row
- **AND** no orphan references exist

### Requirement: Registration and Packaging
The skill MUST be registered in the `UpdateSkills.kt` `DESCRIPTIONS` map after `authoring-gradle-builds`, MUST satisfy the frontmatter `name:` == directory-name invariant, MUST appear in the spliced `docs/skills.md` inventory produced by `:updateSkillsList`, and MUST be included in `skills.zip` and installer extraction under the five-name portfolio guardrail.

#### Scenario: Install the five-skill portfolio
- **WHEN** the installer runs against an existing installation
- **THEN** all five skills including `advanced-gradle-dependencies` are installed
- **AND** stale directories marked with the repository author string are deleted when `replaceOld=true`

### Requirement: Version-Aware Guidance and Authoritative Documentation Routing
The skill MUST scope version-sensitive advice to the project's wrapper version, which MUST be read before applying such advice. All major topics MUST route to version-scoped official documentation exclusively through `gradle_docs` tool hints (`path=...` or `query="tag:..."`); the skill MUST NOT embed published `docs.gradle.org` URLs or `gradle-mcp.rnett.dev` pointers as documentation citations and MUST NOT fabricate tool names. Each reference MUST close with an established "More info" section citing the authoritative Gradle userguide page(s) for that topic via `gradle_docs(path="userguide/<page>.md")`, and the skill's references MUST substantively cover the enumerated set of Gradle userguide pages through authored prose plus those citations.

#### Scenario: Route through gradle_docs
- **WHEN** a reference cites authoritative Gradle documentation
- **THEN** the citation is a `gradle_docs` hint with a verified tag or path
- **AND** the agent reads the wrapper version before applying version-sensitive advice

#### Scenario: Cite authoritative userguide pages in "More info"
- **WHEN** a reference closes with a documentation section
- **THEN** it uses the established "More info" style listing `gradle_docs(path="userguide/<page>.md")` citations for the topic
- **AND** no published `docs.gradle.org` URL or fabricated tool name is used

#### Scenario: Substantively cover the enumerated userguide pages
- **WHEN** a reviewer audits documentation coverage of the skill's references
- **THEN** the references' authored prose, together with their "More info" citations, substantively cover the enumerated set of Gradle userguide pages

### Requirement: Dependency Verification Doctrine Consistency
The skill MUST present dependency verification as conditional-only guidance consistent with the portfolio doctrine: verification MUST NOT be recommended as a baseline, its UX costs MUST be reported honestly before enabling, and the locking-vs-verification distinction MUST be preserved.

#### Scenario: Keep verification conditional
- **WHEN** an agent considers enabling dependency verification without an explicit user request for supply-chain hardening
- **THEN** the guidance reports UX costs and applies verification only conditionally
- **AND** the doctrine matches `using-gradle` and `authoring-gradle-builds`
