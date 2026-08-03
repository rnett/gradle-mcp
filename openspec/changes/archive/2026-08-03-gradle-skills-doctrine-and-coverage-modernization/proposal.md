# Proposal: Gradle Skills Doctrine and Coverage Modernization

## Why

The shipped Gradle skills (`authoring-gradle-builds` and `using-gradle`) have coverage gaps and doctrine conflicts when measured against the maintainer's 15-topic coverage list — the things to make sure are covered (coverage goals, not a rubric). Three problem classes:

1. **Doctrine conflicts with Gradle 9.x ground truth.** `jdk-toolchains.md` treats the toolchain language version as source/bytecode targeting and does not document `options.release`'s strict `--release` semantics. The JVM running the Gradle daemon is treated as a wrapper-compatibility concern with no Daemon JVM criteria guidance. `dependencies-and-catalogs.md` recommends strict dependency verification as a baseline despite UX costs that make it unsuitable as a default. Five shipped examples still use the formally deprecated Kotlin `by` delegates.
2. **Missing procedural coverage.** No authored references exist for diagnostic/reporting tasks, task property annotations, `Copy`/`Sync`/`Delete` and lazy file APIs, or extensions. Cross-project artifact sharing via configurations, IP-safe aggregation, and custom attributes have no complete recipe.
3. **Operational gaps in `using-gradle`.** No `--rerun` vs `--rerun-tasks` cost/smell guidance and no routing entry for diagnostic tasks.

This change corrects the doctrine against verified Gradle 9.x facts and fills the coverage gaps with four new authored references plus woven edits in existing references, while preserving every scope boundary: the four-skill inventory, the byte-identical frozen generated best-practices corpus, no new enforcement gates, and no `zipSkills`/generator wiring.

## What Changes

Every item names its exact target file and section; items are grouped per skill and lettered so the Coverage Map can point at them. Line numbers cite current file state.

### Skill: `authoring-gradle-builds`

**(a) Three new authored references + SKILL.md routing** (topics 11, 12/13, 15)

Create under `references/`:

- `task-properties.md` — task property annotation doctrine (verified against Gradle 9.6.1):
  1. Canonical annotation set: `@Input`, `@InputFiles`, `@InputDirectory` (singular valid), `@OutputFile`, `@OutputDirectory`, and the rest of the standard input/output set.
  2. Explicit prohibition: `@InputDirectories` (plural) does NOT exist; multiple directory inputs use `@InputFiles` + `@PathSensitive`.
  3. Modifiers: `@IgnoreEmptyDirectories`, `@NormalizeLineEndings`, `@SkipWhenEmpty` (implies `@Incremental`).
  4. Kotlin placement: annotations go on getters.
  5. Failure behavior: validation failures fail the task at execution start.
  6. Cache consequence: declared inputs are the build-cache key; correct annotations are a cacheability precondition.
- `file-operations.md` — file-task and lazy file API doctrine:
  1. `Copy`, `Sync`, and `Delete` task recipes with provider-backed `from`/`into`/`delete` inputs (including `Sync` deletion semantics).
  2. Provider-backed `RegularFileProperty`/`DirectoryProperty` vs realized `File`/`Path` — when each is appropriate and the configuration-cache consequence of capturing realized files.
  3. Lazy file trees: `fileTree`, `zipTree`, `tarTree`; archive-tree laziness (archives are not expanded at configuration time).
  4. `ConfigurableFileCollection` (lazy, mutable) vs `FileCollection` (read-only view).
  5. Constraints: no configuration-time iteration or resolution of these provider types; no early capture of realized files or `Project`.
  6. Each section states its build-cache/configuration-cache consequence.
- `extensions.md` — procedural guide to extensions:
  1. Creating extensions: `extensions.create(...)` in plugin code with managed-property extension classes.
  2. Getting extensions: `extensions.getByType(...)`, `extensions.getByName(...)`, type-safe Kotlin accessors.
  3. Working with extensions: exposing `Property`/`Provider` values, wiring extension properties into task properties via providers (no eager copying), convention/default values.
  4. Anti-patterns: eager realization at configuration time, holding `Project` references in extension objects.

Routing: add three rows to `SKILL.md` `## Decision Routing` — custom task property annotations -> `task-properties.md`; file copy/sync/delete and lazy file handling -> `file-operations.md`; creating/getting/working with extensions -> `extensions.md`.

**(b) `references/jdk-toolchains.md` doctrine rewrite** (topics 5, 6)

- Rewrite `## Toolchain versus compatibility properties` (line 41):
  - Current: presents `sourceCompatibility`/`targetCompatibility` as source/class-file level controls to keep consistent with the toolchain when both are present ("Use compatibility properties only when a legacy plugin or publishing contract needs an explicit compatibility value in addition to the toolchain. If both are present, keep them consistent...").
  - New: the toolchain selects the compilation/test JDK only. `options.release` enforces both the bytecode level AND the Java API floor (strict `--release`). `sourceCompatibility`/`targetCompatibility` are legacy source/class-file fallbacks only and do NOT prevent compiling against newer APIs. Never equate them with `options.release`.
- Rewrite the `## Default decision` table (line 5) rows accordingly: target a Java version -> `options.release`; select the compile/test JDK -> toolchain; change the daemon JVM -> Daemon JVM criteria.
- Expand `## JVM that runs Gradle versus JVM used by the build` (line 57) with an explicit Daemon JVM criteria subsection: the daemon JVM is selected by the Daemon JVM criteria in `gradle/gradle-daemon-jvm.properties` and managed with `./gradlew updateDaemonJvm`; project toolchains do NOT select the daemon JVM.
- Replace the deprecated example at line 164 (`val jvmMain by getting` -> `named("jvmMain")`).

**(c) `references/java-builds.md` — `options.release` coverage** (topic 5)

- Add an `options.release` section (and Operating-Defaults row): strict bytecode + API floor enforcement, relationship to the toolchain-selected JDK, explicit non-equivalence with `sourceCompatibility`/`targetCompatibility`.

**(d) `references/kotlin-dsl.md` — deprecated Kotlin `by` delegates** (topic 4)

- Add a new "Deprecated Kotlin `by` delegates" section documenting the deprecation scope: only the five Kotlin `by` delegates (`by creating`, `by getting`, etc.) are formally deprecated; general `NamedDomainObjectContainer` `create`/`getByName` remain valid but are avoided for laziness (prefer `register`/`named`); do/don't replacements.

**(e) Replace all five deprecated delegate examples in place:**

| File | Line | Current | Replacement |
| :--- | :--- | :--- | :--- |
| `configurations-and-variants.md` | 38 | `val commonDependencies by creating` | lazy `register`-based configuration creation |
| `jdk-toolchains.md` | 164 | `val jvmMain by getting` | `named("jvmMain")` |
| `kotlin-compiler-options.md` | 139 | `val commonMain by getting {` | `named("commonMain") {` |
| `testing-configuration.md` | 180 | `val commonTest by getting {` | `named("commonTest") {` |
| `testing-configuration.md` | 185 | `val jvmTest by getting {` | `named("jvmTest") {` |

**(f) `references/managed-types-and-providers.md`** (topics 1, 3, 13)

- Add the complete lazy producer/consumer recipe (topic 1): produce the artifact in project A via a provider-backed task output, publish it through a consumable configuration, consume it in project B via a resolvable configuration — no `project(path, configuration)`, no cross-project task dependency, IP-compatible.
- Add an eager->lazy replacement table for common non-lazy APIs (topic 3): `create` -> `register`, `getByName` -> `named`, eager file/collection iteration -> provider-backed equivalents, each with its configuration-time consequence.
- Expand `## Lazy Files` (line 143) (topic 13): `fileTree`/`zipTree`/`tarTree` + archive-tree laziness, `RegularFile`/`Directory` providers vs realized `File`/`Path`, `ConfigurableFileCollection` vs `FileCollection`; link to `file-operations.md` for procedural detail.

**(g) `references/configurations-and-variants.md`** (topics 1, 9)

- Add a custom attributes section (topic 9): `Attribute.of(...)` definition, attribute placement on consumable/resolvable configurations, attribute compatibility rules, and when to prefer custom attributes over feature variants/capabilities.
- Extend `## Variant-Aware Consumption` (line 83) with IP-safe artifact-sharing detail (topic 1) linking to the producer/consumer recipe in `managed-types-and-providers.md`.

**(h) `references/modules-and-settings.md`** (topic 2)

- Extend `## Project Isolation` (line 101) with IP-safe cross-project aggregation: avoid `subprojects`/`allprojects` and direct cross-project state access; aggregate via shared configurations or artifact-based approaches, or per-project contributions collected by an aggregating project.

**(i) `references/dependencies-and-catalogs.md`** (topic 7)

- Rewrite `## Dependency verification and supply chain` (line 182):
  - Current (baseline recommendation): "Enable strict verification, review metadata changes, and never use `--dependency-verification=off` or lenient mode to unblock a build."
  - New (conditional-only): dependency verification is NOT a baseline recommendation. Report its UX costs honestly before enabling (metadata maintenance on every dependency change, friction during dependency updates, failure modes from missing or stale metadata). Apply it only when the user explicitly asks for supply-chain hardening; keep the locking-vs-verification distinction and the `--dependency-verification=off` caution inside that conditional framing.

**SKILL.md body weaving** (topic 14; `authoring-gradle-builds` is primary)

- `## Always-Loaded Best-Practice Footguns`: add compact build-cache/configuration-cache/IP rules — no configuration-time resolution or iteration, no capture of realized files or `Project`, prefer provider wiring.
- Each edited or new reference above states its cache/CC/IP consequence where relevant (deep weaving, not a standalone add-on).

### Skill: `using-gradle`

**(a) New `references/diagnostic-tasks.md`** (topic 10)

This reference belongs in `using-gradle` — the Broad Operational Index routing and the ADDED "Diagnostic Task Coverage (Topic 10)" requirement both live in the `using-gradle` delta. Content outline:

1. Use-case matrix of CORE diagnostic tasks — "question you are asking -> task to run" — covering `help`, `projects`, `tasks`, `properties`, `dependencies`, `dependencyInsight`, `buildEnvironment`, `outgoingVariants`, `resolvableConfigurations`, and `javaToolchains`.
2. Discovery rule for plugin-provided reports: `tasks --all` and `help --task <name>`.
3. Explicit non-goal: no exhaustive enumeration of plugin-contributed tasks.

**(b) `references/running-builds.md`** (topic 8)

- Add `--rerun` vs `--rerun-tasks` guidance in the `## Essential Flags` table (line 91) and `### Cache and network decision table` (line 122): `--rerun` re-runs one specific task; `--rerun-tasks` re-runs everything including included builds and is extremely expensive; needing `--rerun-tasks` is a smell for build-logic errors in output/input tracking; keep a documented fallback for Gradle versions without `--rerun`.

**(c) `SKILL.md` `## Reference Discovery` (line 94)**

- Add routing for `references/diagnostic-tasks.md`: diagnosing a build issue or choosing a reporting task -> `diagnostic-tasks.md`.

**(d) `references/dependencies.md`** (topic 7)

- Align `### Locking and verification` (line 103) with the conditional-only doctrine:
  - Current: "Keep strict verification enabled. NEVER use `--dependency-verification=off` or lenient mode to unblock a build: missing metadata, bad checksums, or untrusted signatures require review."
  - New: conditional guidance consistent with authoring item (i) — verification only when the user asks for supply-chain hardening, with UX costs stated; keep the locking-vs-verification distinction and the caution against silently disabling verification.

**(e) Operational cache/CC/IP weaving** (topic 14)

- `SKILL.md` `## Always-Loaded Operational Footguns` (line 44): add compact operational build-cache/configuration-cache/IP rules where operationally sensible (e.g., reading configuration-cache reports, cache invalidation when diagnosing stale results). Nothing is added to `interacting-with-project-runtime` or `verifying-compose-ui`.

### Tooling and metadata

- Rewrite the `DESCRIPTIONS` entries for `using-gradle` (line 32) and `authoring-gradle-builds` (line 33) in `src/main/kotlin/dev/rnett/gradle/mcp/UpdateSkills.kt` to reflect the modernized coverage (diagnostic tasks, task property annotations, file operations, extensions, `options.release`/Daemon JVM doctrine, conditional dependency verification).
- Re-splice `docs/skills.md` by running `./gradlew :updateSkillsList` (between the `SKILLS_LIST_START`/`SKILLS_LIST_END` markers); verify with `./gradlew :verifySkillsList` (explicit task, not wired into `check`).

### Spec deltas (already authored in this change)

- `authoring-gradle-builds` — MODIFIED: Kotlin DSL Authoring Coverage, Managed Types and Lazy Configuration Coverage, Java Builds and Variant-Aware Configuration Coverage, Build Cache and Configuration Cache Authoring Coverage, Authored Authoring Best-Practice References. ADDED: JVM Compatibility and Toolchain Doctrine, Daemon JVM Criteria Doctrine, Dependency Verification Doctrine, Task Property Annotation Coverage (Topic 11), File Operations Coverage (Topics 12/13), Extensions Coverage (Topic 15).
- `using-gradle` — MODIFIED: Broad Operational Index, Version-Aware Guidance, High-Impact Operational Footgun Body Rules, Authored Operational Best-Practice References. ADDED: Diagnostic Task Coverage (Topic 10).
- `gradle-skill-best-practices-integration` — ADDED: authored doctrine precedence over frozen corpus examples.
- All MODIFIED deltas retain every base scenario verbatim. This revision applies only two typo fixes to the authoring delta: "The an explicit note" -> "An explicit note" and "WHEN an la agent" -> "WHEN an agent".

## Coverage Map

The 15 topics are the maintainer's coverage goals ("things I want to make sure are covered"), not a rubric. Each row points to the concrete change(s) that deliver it; item letters refer to the inventory above.

| # | Topic | Delivered by |
| :--- | :--- | :--- |
| 1 | Produce artifacts in one project, consume in another (using configurations) | authoring (f) producer/consumer recipe in `managed-types-and-providers.md`; (g) `## Variant-Aware Consumption` extension; delta scenario "Implement lazy artifact sharing" |
| 2 | Aggregate across projects/subprojects, isolated-projects-compatible | authoring (h) `## Project Isolation` extension in `modules-and-settings.md`; delta scenario "Author an IP-compatible aggregation" |
| 3 | Non-lazy APIs to avoid + eager->lazy replacement table | authoring (f) replacement table in `managed-types-and-providers.md` + SKILL.md footgun weaving |
| 4 | Kotlin `by` delegates (`by creating`/`by getting`, etc.) deprecated — do not use | authoring (d) `kotlin-dsl.md` section + (e) five example replacements; delta MODIFIED Kotlin DSL Authoring Coverage |
| 5 | JVM version compatibility of compiled code: `options.release`, NOT toolchains | authoring (b) `jdk-toolchains.md` rewrite + (c) `java-builds.md` section; delta ADDED JVM Compatibility and Toolchain Doctrine |
| 6 | Toolchains select the compilation JDK; daemon JVM via Daemon JVM criteria | authoring (b) Daemon JVM criteria subsection (`gradle/gradle-daemon-jvm.properties`, `updateDaemonJvm`); delta ADDED Daemon JVM Criteria Doctrine |
| 7 | Dependency verification: conditional-only, not a baseline | authoring (i) rewrite + using (d) alignment; delta ADDED Dependency Verification Doctrine |
| 8 | `--rerun` vs `--rerun-tasks`; `--rerun-tasks` extremely expensive, needing it is a smell | using (b) `running-builds.md`; delta MODIFIED Version-Aware Guidance + High-Impact Operational Footgun Body Rules |
| 9 | Custom attributes for configurations/dependency resolution | authoring (g) custom attributes section; delta MODIFIED Java Builds and Variant-Aware Configuration Coverage |
| 10 | Built-in reporting/diagnostic tasks: use-case driven, core tasks, plus discovery rule | using (a) new `diagnostic-tasks.md` + (c) Reference Discovery row; delta ADDED Diagnostic Task Coverage (Topic 10) |
| 11 | Task property annotations (`@Input`, `@Output*`, etc.) | authoring (a) `task-properties.md`; delta ADDED Task Property Annotation Coverage (Topic 11) |
| 12 | `Copy`/`Sync`/`Delete` file tasks | authoring (a) `file-operations.md`; delta ADDED File Operations Coverage (Topics 12/13) |
| 13 | Lazy file APIs: file trees incl. archives, `RegularFile`/`Directory` vs `File`/`Path` | authoring (a) `file-operations.md` + (f) `## Lazy Files` expansion; delta ADDED File Operations Coverage (Topics 12/13) |
| 14 | Build cache / configuration cache / isolated projects compatibility, woven throughout | authoring SKILL.md footguns + all authoring items (primary); using (e) operational weaving; REPL/UI skills intentionally ignored; delta MODIFIED Build Cache and Configuration Cache Authoring Coverage |
| 15 | Creating, getting, and working with extensions | authoring (a) `extensions.md`; delta ADDED Extensions Coverage (Topic 15) |

## Impact

- **Affected skills**:
  - `authoring-gradle-builds`: `SKILL.md` (`## Decision Routing`, `## Always-Loaded Best-Practice Footguns`); nine references edited (`jdk-toolchains.md`, `java-builds.md`, `kotlin-dsl.md`, `configurations-and-variants.md`, `kotlin-compiler-options.md`, `testing-configuration.md`, `managed-types-and-providers.md`, `modules-and-settings.md`, `dependencies-and-catalogs.md`); three new references (`task-properties.md`, `file-operations.md`, `extensions.md`).
  - `using-gradle`: `SKILL.md` (`## Reference Discovery`, `## Always-Loaded Operational Footguns`); two references edited (`running-builds.md`, `dependencies.md`); one new reference (`diagnostic-tasks.md`).
- **Tooling/metadata**: `src/main/kotlin/dev/rnett/gradle/mcp/UpdateSkills.kt` `DESCRIPTIONS`; `docs/skills.md` re-spliced via `:updateSkillsList`.
- **Affected specs**: `authoring-gradle-builds`, `using-gradle`, `gradle-skill-best-practices-integration`.
- **Unchanged**: no runtime code or API changes; `interacting-with-project-runtime` and `verifying-compose-ui` untouched; the frozen `references/best-practices/` corpus stays byte-identical; `build.gradle.kts`, `zipSkills`, and the `generate-best-practices-doc` capability unchanged; no new enforcement gates.

## Deferred Follow-ups

1. **Missing enforcement gates (S1).** This change adds no automated enforcement gates; reference reachability is enforced by manual/human-review discipline grounded in the `skill-doc-link-convention` spec. The real verification gates are `SkillToolsTest` (inventory, keyed on top-level directories), `SkillArtifactSafetyTest` (afterEvaluate prohibition contexts), `UpdateSkillsTest` (docs/skills.md splice sync), the explicit `:verifySkillsList` (not part of `check`), `openspec validate --strict`, and human review; tasks such as `checkReferenceReachability`, `checkGeneratedContent`, or `verifySkillsMaterialized` do not exist. Adding automated reachability gates is follow-up work.
2. **zipSkills / generator / frozen-corpus reconciliation (S2).** No `zipSkills` -> `generateBestPracticesDoc` wiring is added; `build.gradle.kts` is not an affected file; the `generate-best-practices-doc` capability is not modified. Reconciling that spec with the build and the frozen-corpus invariant is follow-up work.
