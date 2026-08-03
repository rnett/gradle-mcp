# Tasks: Gradle Skills Doctrine and Coverage Modernization

Every checklist item names the concrete file and section it changes. Section numbering maps to the proposal.md change inventory (item letters in parentheses).

## Implementation Checklist

### 1. `authoring-gradle-builds` — new references (item a)

- [x] Create `references/task-properties.md`: canonical annotation set (`@Input`, `@InputFiles`, `@InputDirectory` (singular valid), `@OutputFile`, `@OutputDirectory`, and the rest of the standard set); prohibition of `@InputDirectories` (plural) — multiple directory inputs use `@InputFiles` + `@PathSensitive`; modifiers (`@IgnoreEmptyDirectories`, `@NormalizeLineEndings`, `@SkipWhenEmpty` implies `@Incremental`); annotations placed on Kotlin getters; validation failures fail the task at execution start; inputs-as-cache-key consequence.
- [x] Create `references/file-operations.md`: `Copy`/`Sync`/`Delete` recipes with provider-backed inputs (including `Sync` deletion semantics); `RegularFileProperty`/`DirectoryProperty` vs realized `File`/`Path`; lazy trees `fileTree`/`zipTree`/`tarTree` + archive-tree laziness; `ConfigurableFileCollection` vs `FileCollection`; no configuration-time iteration/resolution; every section states its build-cache/configuration-cache consequence.
- [x] Create `references/extensions.md`: creating extensions (`extensions.create(...)` + managed-property extension classes); getting extensions (`getByType`/`getByName`/type-safe accessors); working with extensions (provider wiring into task properties, convention/default values); anti-patterns (eager realization at configuration time, holding `Project`).
- [x] `SKILL.md` `## Decision Routing`: add three rows — custom task property annotations -> `task-properties.md`; file copy/sync/delete and lazy file handling -> `file-operations.md`; creating/getting/working with extensions -> `extensions.md`.

### 2. `authoring-gradle-builds` — doctrine rewrites (items b, c, d, i)

- [x] `references/jdk-toolchains.md`: rewrite `## Toolchain versus compatibility properties` — the toolchain selects the compilation/test JDK only; `options.release` enforces the bytecode level AND the Java API floor (strict `--release`); `sourceCompatibility`/`targetCompatibility` are legacy fallbacks only that do NOT prevent compiling against newer APIs; never equate them with `options.release`.
- [x] `references/jdk-toolchains.md`: rewrite the `## Default decision` table rows accordingly (target a Java version -> `options.release`; select the JDK -> toolchain; daemon JVM -> Daemon JVM criteria).
- [x] `references/jdk-toolchains.md`: expand `## JVM that runs Gradle versus JVM used by the build` with an explicit Daemon JVM criteria subsection (`gradle/gradle-daemon-jvm.properties`, `./gradlew updateDaemonJvm`; project toolchains do NOT select the daemon JVM).
- [x] `references/java-builds.md`: add an `options.release` section/Operating-Defaults row (strict bytecode + API floor; relationship to the toolchain-selected JDK; non-equivalence with `sourceCompatibility`/`targetCompatibility`).
- [x] `references/kotlin-dsl.md`: add a "Deprecated Kotlin `by` delegates" section — scope: only the five `by` delegates (`by creating`, `by getting`, etc.) are formally deprecated; `NamedDomainObjectContainer.create`/`getByName` remain valid but are avoided for laziness (prefer `register`/`named`); do/don't replacements.
- [x] `references/dependencies-and-catalogs.md`: rewrite `## Dependency verification and supply chain` (current: "Enable strict verification, review metadata changes, and never use `--dependency-verification=off` or lenient mode to unblock a build.") into conditional-only guidance with honest UX-cost reporting; keep the locking-vs-verification distinction and the `--dependency-verification=off` caution inside the conditional framing.

### 3. `authoring-gradle-builds` — deprecated example replacements (item e)

- [x] `references/configurations-and-variants.md` line 38: replace `val commonDependencies by creating` with lazy `register`-based creation.
- [x] `references/jdk-toolchains.md` line 164: replace `val jvmMain by getting` with `named("jvmMain")`.
- [x] `references/kotlin-compiler-options.md` line 139: replace `val commonMain by getting {` with `named("commonMain") {`.
- [x] `references/testing-configuration.md` lines 180 and 185: replace `val commonTest by getting {` and `val jvmTest by getting {` with `named("commonTest") {` and `named("jvmTest") {`.

### 4. `authoring-gradle-builds` — coverage recipes and weaving (items f, g, h + topic 14)

- [x] `references/managed-types-and-providers.md`: add the complete lazy producer/consumer recipe — producer task with provider-backed output -> consumable configuration -> resolvable consumption in another project; no `project(path, configuration)`; IP-compatible.
- [x] `references/managed-types-and-providers.md`: add the eager->lazy replacement table for common non-lazy APIs (`create` -> `register`, `getByName` -> `named`, eager file/collection iteration -> provider-backed equivalents), each with its configuration-time consequence.
- [x] `references/managed-types-and-providers.md`: expand `## Lazy Files` — `fileTree`/`zipTree`/`tarTree` + archive-tree laziness, `RegularFile`/`Directory` providers vs `File`/`Path`, `ConfigurableFileCollection` vs `FileCollection`; link to `file-operations.md`.
- [x] `references/configurations-and-variants.md`: add the custom attributes section — `Attribute.of(...)`, attribute placement on consumable/resolvable configurations, attribute compatibility rules, when to prefer custom attributes over feature variants/capabilities.
- [x] `references/configurations-and-variants.md`: extend `## Variant-Aware Consumption` with IP-safe artifact sharing linking to the producer/consumer recipe.
- [x] `references/modules-and-settings.md`: extend `## Project Isolation` with IP-safe cross-project aggregation (no `subprojects`/`allprojects`/direct cross-project state access; shared-configuration/artifact-based collection).
- [x] `SKILL.md` `## Always-Loaded Best-Practice Footguns`: add compact build-cache/configuration-cache/IP rules (no configuration-time resolution/iteration, no capture of realized files or `Project`, prefer provider wiring); weave the same consequences into every reference edited in sections 2-4.

### 5. `using-gradle` (items a-e)

- [x] Create `references/diagnostic-tasks.md`: use-case matrix ("question -> task") of CORE diagnostic tasks — `help`, `projects`, `tasks`, `properties`, `dependencies`, `dependencyInsight`, `buildEnvironment`, `outgoingVariants`, `resolvableConfigurations`, `javaToolchains`; discovery rule for plugin-provided reports (`tasks --all`, `help --task <name>`); no exhaustive plugin enumeration.
- [x] `SKILL.md` `## Reference Discovery`: add routing for `references/diagnostic-tasks.md` (diagnosing a build issue or choosing a reporting task).
- [x] `references/running-builds.md`: add `--rerun` vs `--rerun-tasks` guidance in the `## Essential Flags` table and `### Cache and network decision table` — `--rerun` re-runs one specific task; `--rerun-tasks` re-runs everything including included builds and is extremely expensive; needing `--rerun-tasks` is a smell for build-logic errors in output/input tracking; keep the documented fallback for Gradle versions without `--rerun`.
- [x] `references/dependencies.md`: align `### Locking and verification` (current: "Keep strict verification enabled. NEVER use `--dependency-verification=off` or lenient mode...") with the conditional-only doctrine, stating UX costs; keep the locking-vs-verification distinction.
- [x] `SKILL.md` `## Always-Loaded Operational Footguns`: weave operational build-cache/configuration-cache/IP guidance where operationally sensible (e.g., reading configuration-cache reports, cache invalidation when diagnosing stale results).

### 6. Tooling and metadata

- [x] `src/main/kotlin/dev/rnett/gradle/mcp/UpdateSkills.kt`: rewrite the `DESCRIPTIONS` entries for `using-gradle` (line 32) and `authoring-gradle-builds` (line 33) to reflect the modernized coverage.
- [x] Run `./gradlew :updateSkillsList` to re-splice `docs/skills.md` between the `SKILLS_LIST_START`/`SKILLS_LIST_END` markers.

### 7. Verification

- [x] Run `./gradlew :test --tests "dev.rnett.gradle.mcp.UpdateSkillsTest"` (docs/skills.md splice sync).
- [x] Run `./gradlew :test --tests "dev.rnett.gradle.mcp.tools.skills.SkillToolsTest"` (skill inventory, keyed on top-level directories).
- [x] Run `./gradlew :test --tests "dev.rnett.gradle.mcp.skills.SkillArtifactSafetyTest"` (afterEvaluate prohibition-context audit).
- [x] Run `./gradlew :verifySkillsList` (explicit task; not part of `check`).
- [x] Run `openspec validate gradle-skills-doctrine-and-coverage-modernization --strict`.
- [x] Human review: maintainer coverage review against the 15-topic list; per S1, this review is the reference-reachability gate.
