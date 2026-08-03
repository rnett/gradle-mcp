# Design: Gradle Skills Doctrine and Coverage Modernization

## Technical Context

The shipped Gradle skills currently provide high-level workflows plus frozen generated rationale, but conflict with Gradle 9.x ground truth in several places and lack precise do/don't procedural recipes for high-stakes authoring. This modernization turns `authoring-gradle-builds` and `using-gradle` into doctrine-driven procedural skills: each of the maintainer's 15 coverage topics maps to concrete file-level changes (see proposal.md, "What Changes" and "Coverage Map"), and every doctrine correction is stated as current-vs-new wording.

Scope boundaries that shape the design:

- The four-skill inventory is preserved. `interacting-with-project-runtime` and `verifying-compose-ui` are intentionally ignored — cache/IP weaving lands only where operationally sensible (`authoring-gradle-builds` + `using-gradle`).
- The frozen generated best-practices corpus (`references/best-practices/`) stays byte-identical; authored doctrine takes precedence over corpus examples.
- No runtime code, no `build.gradle.kts` changes, no new enforcement gates (S1), and no `zipSkills` -> `generateBestPracticesDoc` wiring (S2).

## Decisions

### Doctrine vs ground truth (binding decisions, verbatim)

1. **`options.release` semantics**: `options.release` enforces both the bytecode level AND the Java API floor (strict `--release`). `sourceCompatibility`/`targetCompatibility` are legacy source/class-file fallbacks only and do NOT prevent compiling against newer APIs. Never equate them.
   - Delivered by: authoring items (b) `jdk-toolchains.md` rewrite and (c) `java-builds.md`; delta ADDED "JVM Compatibility and Toolchain Doctrine".
2. **Toolchains vs daemon JVM**: project toolchains select the compilation/test JDK; the Gradle daemon JVM is selected by the Daemon JVM criteria, NOT by project toolchains (via `gradle/gradle-daemon-jvm.properties` or `updateDaemonJvm`). The skills decouple these two explicitly.
   - Delivered by: authoring item (b) Daemon JVM criteria subsection; delta ADDED "Daemon JVM Criteria Doctrine".
3. **Kotlin `by` delegate scope**: only the five specific Kotlin `by` delegates (`by creating`, `by getting`, etc.) are formally deprecated. General `NamedDomainObjectContainer` `create`/`getByName` remain valid but are avoided for laziness in favor of `register`/`named`.
   - Delivered by: authoring items (d) `kotlin-dsl.md` section and (e) five example replacements; delta MODIFIED "Kotlin DSL Authoring Coverage".
4. **Dependency verification**: conditional-only guidance with honest UX-cost reporting; not a baseline recommendation.
   - Delivered by: authoring item (i) and using-gradle item (d); delta ADDED "Dependency Verification Doctrine".
5. **Reporting scope**: to avoid an infinite enumeration of plugin-provided tasks, reporting guidance is a use-case matrix of CORE diagnostic tasks (`help`, `projects`, `tasks`, `properties`, `dependencies`, `dependencyInsight`, `buildEnvironment`, `outgoingVariants`, `resolvableConfigurations`, `javaToolchains`, etc.) plus a discovery rule (`tasks --all`, `help --task`).
   - Delivered by: using-gradle item (a) `diagnostic-tasks.md` — homed in `using-gradle` because the Broad Operational Index routing and the ADDED Topic 10 requirement both live in the using-gradle delta; delta ADDED "Diagnostic Task Coverage (Topic 10)".
6. **Weaving scope**: cache and IP guidance are woven deeply into `authoring-gradle-builds` (primary) and `using-gradle` (where it affects operation). REPL and UI skills are explicitly ignored to avoid noise.
   - Delivered by: authoring SKILL.md footguns + per-reference weaving; using-gradle item (e); delta MODIFIED "Build Cache and Configuration Cache Authoring Coverage".
7. **S1 (scope)**: no new enforcement gates (e.g., automated reference reachability checks) are added to the build logic in this change; reachability is a manual/human-review discipline grounded in the `skill-doc-link-convention` spec. Recorded as deferred follow-up #1.
8. **S2 (scope)**: the `generate-best-practices-doc` capability is not modified, and any wiring from `zipSkills` to that generation process is dropped to maintain the frozen-corpus invariant; `build.gradle.kts` is not an affected file. Reconciliation is deferred follow-up #2.
9. **Doctrine precedence**: authored guidance takes absolute precedence over frozen corpus examples; the corpus remains optional historical rationale.
   - Delivered by: delta ADDED "Authored doctrine precedence over frozen corpus examples" in `gradle-skill-best-practices-integration`.

### Change inventory (mirrors proposal.md "What Changes")

- `authoring-gradle-builds`:
  - (a) three new references — `task-properties.md`, `file-operations.md`, `extensions.md` — plus `## Decision Routing` rows in `SKILL.md`.
  - (b) `jdk-toolchains.md`: rewrite `## Toolchain versus compatibility properties` and the `## Default decision` table; expand `## JVM that runs Gradle versus JVM used by the build` into an explicit Daemon JVM criteria subsection.
  - (c) `java-builds.md`: `options.release` section/Operating-Defaults row.
  - (d) `kotlin-dsl.md`: new "Deprecated Kotlin `by` delegates" section.
  - (e) five deprecated-example replacements (`configurations-and-variants.md:38`, `jdk-toolchains.md:164`, `kotlin-compiler-options.md:139`, `testing-configuration.md:180,185`).
  - (f) `managed-types-and-providers.md`: lazy producer/consumer recipe, eager->lazy replacement table, `## Lazy Files` expansion.
  - (g) `configurations-and-variants.md`: custom attributes section + `## Variant-Aware Consumption` extension.
  - (h) `modules-and-settings.md`: IP-safe aggregation in `## Project Isolation`.
  - (i) `dependencies-and-catalogs.md`: conditional-only rewrite of `## Dependency verification and supply chain`.
  - Cache/CC/IP weaving in `## Always-Loaded Best-Practice Footguns` and per-reference consequences.
- `using-gradle`:
  - (a) new `references/diagnostic-tasks.md`; (b) `running-builds.md` `--rerun`/`--rerun-tasks` smell guidance; (c) `## Reference Discovery` routing row; (d) `dependencies.md` `### Locking and verification` alignment; (e) operational cache/CC/IP footgun weaving.
- Tooling: `UpdateSkills.kt` `DESCRIPTIONS` rewrites for the two skills (lines 32-33); `:updateSkillsList` re-splices `docs/skills.md`; `:verifySkillsList` verifies (explicit task, not part of `check`).

### Topic 11 facts (verified against Gradle 9.6.1)

- Canonical set: `@Input`, `@InputFiles`, `@InputDirectory` (singular valid), `@OutputFile`, `@OutputDirectory`, etc.
- `@InputDirectories` (plural) does NOT exist — multiple directory inputs use `@InputFiles` + `@PathSensitive`.
- Modifiers: `@IgnoreEmptyDirectories`, `@NormalizeLineEndings`, `@SkipWhenEmpty` (implies `@Incremental`).
- Annotations go on Kotlin getters.
- Validation failures fail the task at execution start.

### Topic 13 facts

- Provider-backed `RegularFile`/`Directory` (`RegularFileProperty`/`DirectoryProperty`) vs realized `File`/`Path`.
- Lazy file trees: `fileTree`/`zipTree`/`tarTree`; archive-tree laziness.
- `ConfigurableFileCollection` (lazy, mutable) vs `FileCollection` (read-only).
- Build-cache/config-cache consequences: no configuration-time iteration or resolution of providers; no early capture of realized files or `Project`.

## Verification Plan

- **Inventory**: `dev.rnett.gradle.mcp.tools.skills.SkillToolsTest` — top-level skill directories preserved (keyed on directories).
- **Safety audit**: `dev.rnett.gradle.mcp.skills.SkillArtifactSafetyTest` — every `afterEvaluate` mention in skill markdown is in a prohibition context or accompanies an `afterEvaluate-justification:` block.
- **Docs splice sync**: `dev.rnett.gradle.mcp.UpdateSkillsTest` — `docs/skills.md` matches the `UpdateSkills.kt` `DESCRIPTIONS`.
- **Metadata**: explicit `./gradlew :verifySkillsList` (not part of `check`).
- **Spec structure**: `openspec validate gradle-skills-doctrine-and-coverage-modernization --strict`.
- **Human review**: maintainer coverage review against the 15-topic list; per S1, this review is the reference-reachability gate.

## Risks

- **Over-specification**: too many do/don't rules raise agent cognitive load. Mitigation: progressive disclosure — compact SKILL.md bodies, detail in references, routing tables that load references on demand.
- **Frozen corpus conflict**: new authored guidance could contradict frozen corpus examples. Mitigation: the doctrine-precedence requirement; the corpus stays byte-identical and is cited only as optional rationale.
- **Unreachable references**: new reference files might not be reachable from routing/discovery tables, and no automated gate exists (S1). Mitigation: explicit `## Decision Routing` and `## Reference Discovery` rows are tasks.md checklist items, plus human review.
