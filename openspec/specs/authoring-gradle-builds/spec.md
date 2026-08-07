# Capability: authoring-gradle-builds

## Description
Authors and modifies Gradle build definitions, project structure, build logic, and delivery wiring.

### Use when
- Authoring or modifying `build.gradle(.kts)`, `settings.gradle(.kts)`, convention plugins, modules, or subprojects.
- Adding or changing dependency declarations, version catalogs, repositories, or plugin management.
- Configuring JDK toolchains, Kotlin compiler options, test frameworks, publishing, or CI wiring.
- Creating custom tasks, worker actions, build services, value sources, service injection, or project-isolation-compatible build logic.
- Modifying advanced Gradle configuration and build performance settings.

### Do NOT use
- Operation/execution (running builds, running tests, diagnosing failures, and read-only dependency inspection/update discovery) belongs to `using-gradle`; authoring/modifying build definitions (including dependency declarations and version catalogs) belongs to `authoring-gradle-builds`. Trivial one-line everyday dependency edits (catalog entry + declaration + version bump) are a sanctioned overlap in `using-gradle`; anything structural (plugins, repositories, modules, toolchains, publishing, CI) is `authoring-gradle-builds` only.
- Researching internal Gradle APIs without the intent to use them in a build script (use `using-gradle`'s research workflow).
- Probing runtime project code (use `interacting-with-project-runtime`).
- Verifying Compose UI (use `verifying-compose-ui`).

## Purpose

This capability defines the workflows and requirements for authoring Gradle builds.
## Requirements
### Requirement: Modification Index
MUST provide a workflow index for build authoring, focused on the modification lifecycle and safe application of patterns. The index SHALL include the Build Health Assessment (Doctor) workflow as `### Build Health Assessment (Doctor)`, which SHALL replace the previous `### Performance Audit` workflow in place as the sole build-health/performance assessment workflow and SHALL be bound to exactly one reference, `references/build-health-assessment.md`, which carries both the assessment procedure and the report material.

#### Scenario:
An agent needs to implement a new project module definition; it consults the `authoring-gradle-builds` body for the "Create Module" workflow, then loads the specific reference for `settings.gradle.kts` modifications.

#### Scenario: Health Assessment Routes to the Doctor
**WHEN** a request is made for a build health assessment, best-practice audit, Gradle doctor, health check, or performance audit.
**THEN** the agent SHALL route it to the `### Build Health Assessment (Doctor)` workflow, which replaces the previous `### Performance Audit` workflow in place.

### Requirement: Dependency Modification
MUST consolidate dependency declaration, version catalog management, repository and plugin-management wiring, and publishing guidance into this skill's dependency authoring path, with `dependencies-and-catalogs.md` as the single home for dependency and catalog procedures.

#### Scenario:
An agent is asked to add a library to a project; it uses `using-gradle` for read-only GAV discovery when needed, then stays within `authoring-gradle-builds` to update `libs.versions.toml`, declare the alias in the consuming build, and apply the repository pattern from `dependencies-and-catalogs.md`.

#### Scenario:
An agent is asked to publish an artifact to Maven Central; it loads the authoring publishing guidance, which directs delivery through the Maven Central Portal rather than the sunset OSSRH staging flow, while keeping repository and credential wiring in the appropriate authoring references.

### Requirement: `afterEvaluate` Prohibition
MUST explicitly prohibit the use of `afterEvaluate` except when a documented correctness-critical ordering constraint exists that cannot be solved by `Provider` wiring or other standard APIs.

#### Scenario:
An agent proposes a build script change using `afterEvaluate` to fix a property ordering issue; the skill's core safety constraint triggers a rewrite using `Provider` or Lazy Configuration.

### Requirement: Best Practices Integration

The skill MUST integrate the frozen generated best-practice corpus as rooted local references, maintaining the lookup order `Index -> Detail -> Gradle Docs`, and MUST avoid duplicating authored guidance that belongs in the generated corpus. Authored best-practice references SHALL coexist with the frozen corpus as the single procedural load for the recommendation field guide; corpus detail remains optional rationale consulted on demand, is cross-linked through `Index -> Detail -> Gradle Docs`, and SHALL never be restated as a competing procedural load.

#### Scenario: Use authored guidance with optional corpus rationale

- **WHEN** an agent is deciding between two plugin application patterns
- **THEN** it loads the relevant authored reference as the single procedural source
- **AND** it may follow `references/best-practices/_index.md` to the matching generated detail entry and query version-scoped `gradle_docs` when deeper rationale or the authoritative source is needed
- **AND** the escalation remains `Index -> Detail -> Gradle Docs` without requiring a second competing procedural load

### Requirement: Progressive Disclosure
MUST implement a root-local reference system where detailed authoring procedures are stored in separate files and loaded only upon a specific trigger.

#### Scenario:
Detailed steps for "Implementing Publishing Logic" are moved to a separate reference to keep the core body focused on the workflow index and safety constraints.

### Requirement: Orientation in Body
The `SKILL.md` body MUST carry a "Before You Modify" sequence that lets an agent orient for a build modification without loading a reference: read the wrapper version, consult the compatibility quick-reference (including the experimental-not-incubating isolated-projects row, the configuration-cache stable-does-not-mean-universal clarification, and the map-notation deprecation note), read existing settings, catalogs, plugins, and conventions, then load the narrowest authored reference as the single authoritative procedural source. The best-practices index and generated detail are optional rationale, consulted on demand; when escalation is needed, preserve the order `Index -> Detail -> Gradle Docs`.

#### Scenario:
An agent receives a request to modify an unfamiliar Gradle build without a loaded reference; it can follow the body sequence from `gradle-wrapper.properties` through the compatibility quick-reference, existing build structure, and decision routing before editing, with optional best-practice escalation in the order `Index -> Detail -> Gradle Docs`.

### Requirement: Version-Aware Guidance
References MUST carry inline `Version notes` for Gradle 7, 8, and 9, and for Kotlin plugin versions where relevant; guidance MUST be biased toward the latest supported version with documented 7.x fallbacks, and the body MUST include a compact compatibility quick-reference. The guidance MUST note that map dependency notation is deprecated since Gradle 9.1 and fails in Gradle 10 in favor of single-string GAV or catalog accessors, that project isolation is experimental and not yet incubating with diagnostics changing across 9.x minors, and that configuration cache stability does not mean every plugin/build is compatible while 9.x strictness continues to evolve.

#### Scenario:
An agent modifies a build using a Gradle 7, 8, or 9 wrapper; it reads the applicable inline `Version notes`, follows the latest-version path when possible, and applies the documented 7.x fallback when the wrapper cannot use the current API.

#### Scenario:
An agent configures Kotlin compiler options or Kotlin build logic; it uses the reference's Kotlin plugin version note to avoid applying a current `compilerOptions` or plugin API blindly to an older Kotlin plugin.

#### Scenario:
An agent sees map dependency notation in a Gradle 9.1+ build; it uses single-string GAV or catalog accessors because map notation is deprecated since 9.1 and fails in Gradle 10.

#### Scenario:
An agent considers project isolation; it treats the feature as experimental, not yet incubating, and checks diagnostics against the applicable 9.x minor rather than assuming stable behavior.

#### Scenario:
An agent enables configuration cache; it does not infer universal plugin/build compatibility from the stable status and accounts for evolving Gradle 9.x strictness.

### Requirement: Authoritative Documentation Routing
The skill MUST route every major authoring topic to a verified version-scoped `gradle_docs` tag and path hint, and MUST NOT embed published `docs.gradle.org` URLs or `gradle-mcp.rnett.dev` tool-documentation pointers as documentation citations. Advanced authoring topics MUST include service injection, build services, value sources, project isolation, build lifecycle, Kotlin DSL, managed types/providers (including incubating dataflow actions), binary plugin development with TestKit and publishing, Java builds and source sets/annotation processing, configurations and feature variants with variant-aware sharing, and build-cache/configuration-cache authoring and debugging. Cache and isolation enablement, persistent configuration, and operational outcome reading route to `using-gradle`; cacheability and configuration-cache-safe authoring remain here.

#### Scenario: Route an authoring topic through the tool
- **WHEN** an agent needs guidance on dependencies, modules and settings, convention plugins, custom tasks, build lifecycle, Kotlin DSL, managed types/providers, toolchains, Kotlin compiler options, Java builds, configurations and variants, testing, plugin development and TestKit, publishing, locking, CI, build scans, or advanced configuration
- **THEN** the relevant reference provides a `gradle_docs` tag and path hint and routes exclusively through `gradle_docs`, with no published `docs.gradle.org` URL and no `gradle-mcp.rnett.dev` pointer

#### Scenario: Route advanced authoring topics
- **WHEN** an agent needs to implement service injection, a build service, a value source, or project-isolation-compatible logic
- **THEN** it is routed to the advanced authoring reference and its verified `gradle_docs` tag and path hint rather than an undocumented or generic recommendation

#### Scenario: Research a migration target version
- **WHEN** an agent performs a Gradle upgrade whose target version differs from the project's wrapper
- **THEN** the upgrading guidance directs the agent to query `gradle_docs` with `tag:upgrading` (and `tag:release-notes`) using an explicit `version="<target>"`, and warns that a coarse version such as `"8"` fails and that omitting `version` resolves to the wrapper rather than the target

### Requirement: Build Lifecycle and Script Model Coverage
MUST provide a reference covering the three lifecycle phases, the configuration-cache serialization sub-phase, the task-graph DAG (not script order), and hook/listener ordering, with version notes and verified documentation pointers.

#### Scenario:
An agent reasons about why top-level script code runs at configuration and loads `build-lifecycle.md` before placing logic.

### Requirement: Kotlin DSL Authoring Coverage
The skill MUST provide a Kotlin DSL primer covering accessor generation and timing, receivers, script naming, public-API-only usage, IDE import, and limitations, operationalizing the Kotlin DSL preference and linking the frozen rationale. It MUST explicitly replace deprecated Kotlin `by` delegates (`by creating`, `by getting`, etc.) with lazy `register` and `named` patterns in all examples and guidance.

#### Scenario: Use lazy registration instead of deprecated delegates
- **WHEN** an agent authors a new extension or plugin in Kotlin DSL
- **THEN** it uses `register("name") { ... }` or `named("name") { ... }` instead of `by creating` or `by getting`
- **AND** it avoids the deprecated `by` delegate syntax entirely

#### Scenario: Troubleshoot missing accessors
- **WHEN** an agent troubleshoots missing accessors
- **THEN** it is routed to accessor-timing guidance

### Requirement: Managed Types and Lazy Configuration Coverage
The skill MUST provide a canonical Property/Provider/managed-object reference covering set-vs-convention, finalizeValue, map/flatMap/zip/orElse, collections/containers, and lazy files, which sibling references link to instead of restating; incubating dataflow actions MUST be gated as non-default. This coverage MUST include a complete lazy producer/consumer recipe for producing artifacts in one project and consuming them in another using configurations.

#### Scenario: Implement lazy artifact sharing
- **WHEN** an agent needs to produce an artifact in project A and consume it in project B
- **THEN** it follows the lazy producer/consumer recipe using configurations to avoid eager project access and maintain IP-compatibility
- **AND** it verifies the producer correctly defines the artifact via a provider-backed task output

#### Scenario: Wire a lazy file provider
- **WHEN** an agent wires a lazy file provider
- **THEN** it loads the managed-types reference rather than duplicating Property semantics in a task reference

### Requirement: Plugin Development Coverage
MUST cover binary plugin authoring (`java-gradle-plugin`, `gradlePlugin{}`, markers), TestKit functional testing, plugin publishing/Portal/signing, and plugin-ID governance, keeping consumer-side test configuration in `testing-configuration.md`.

#### Scenario:
An agent publishing a plugin is routed to Portal publishing with `--validate-only` and credentials-out-of-source guidance.

### Requirement: Java Builds and Variant-Aware Configuration Coverage
The skill MUST cover Java plugin/source sets/annotation processing/mixed languages and the declarable/resolvable/consumable configuration model with feature variants, capabilities, and variant-aware sharing; variant-aware resolution MUST be preferred over low-level `project(path, configuration)`. This MUST include a comprehensive guide on custom attributes for configurations and dependency resolution.

#### Scenario: Use custom attributes for resolution
- **WHEN** an agent needs to differentiate dependencies by an attribute (e.g. "classification" or "targetPlatform")
- **THEN** it defines the custom attribute and uses it within the configuration's attribute compatibility rules to control resolution

#### Scenario: Share outputs between projects
- **WHEN** an agent shares outputs between projects
- **THEN** it is routed to the variant-aware recipe, not a cross-project task dependency

#### Scenario: Publish and customize feature variants
- **WHEN** an agent needs to publish a feature variant or customize which variants or artifacts a component publishes
- **THEN** it adds the feature variant's consumable configuration to the component via `addVariantsFromConfiguration` on the `AdhocComponentWithVariants` component, and customizes the published variants with `withVariantsFromConfiguration` (e.g. `skip()`) or an ad hoc component from `softwareComponentFactory.adhoc(...)`, rather than attaching artifacts out of context

### Requirement: Build Cache and Configuration Cache Authoring Coverage
The skill MUST cover the cacheability contract (determinism, normalization, unique outputs, `@CacheableTask` as a correctness promise), the configuration-cache requirements matrix, report-driven debugging, and isolated-projects constraint families; frozen enablement corpus entries MUST be labeled as usage rationale handed off to `using-gradle`. Guidance on build-cache, configuration-cache, and IP-compatibility MUST be woven throughout the authoring advice, emphasizing the prevention of eager configuration-time resolution.

#### Scenario: Author an IP-compatible aggregation
- **WHEN** an agent needs to aggregate data or artifacts across subprojects
- **THEN** it implements this in an isolated-projects compatible way, avoiding direct `projects` map access and using a shared configuration or artifact-based approach

#### Scenario: Make a task cacheable
- **WHEN** an agent makes a task cacheable
- **THEN** it loads the authoring contract and is handed off to `using-gradle` for persistent cache enablement

### Requirement: High-Impact Authoring Footgun Body Rules

The `authoring-gradle-builds` `SKILL.md` body SHALL carry the `[Writes build logic]` hardest-to-figure-out highlights and High-severity cross-cutting rules as always-loaded rules. The rules SHALL cover, where applicable, keeping expensive work out of configuration, configuration avoidance, provider and managed-property laziness, reading providers only at execution boundaries, public APIs and injected services, model relationships for cross-project behavior, avoiding `afterEvaluate` (version-sensitive), and distinguishing `set(null)` from an absent provider.

#### Scenario: Apply an authoring footgun rule

- **WHEN** an agent is about to author or modify build logic without loading a reference
- **THEN** the body exposes the applicable high-impact rule with a concise reason
- **AND** it links to the authored reference containing the detailed guidance and snippet

#### Scenario: Apply version-sensitive authoring guidance

- **WHEN** an authoring footgun is marked version-sensitive
- **THEN** the body or linked reference directs the agent to read the wrapper version first
- **AND** the agent checks the exact Gradle version before applying the rule

### Requirement: Authored Authoring Best-Practice References
The skill SHALL provide authored, non-generated references carrying the remaining `[Writes build logic]` recommendations and all do/don't snippets. Guidance SHALL be woven into existing references such as `build-lifecycle.md`, `managed-types-and-providers.md`, `custom-tasks.md`, `dependencies-and-catalogs.md`, `convention-plugins.md`, `plugin-development.md`, `jdk-toolchains.md`, and `configurations-and-variants.md`, or into new authored-local files where no natural home exists. This SHALL include:
- `task-properties.md`: Guidance on task property annotations, focusing on the canonical annotation set and the prohibited `@InputDirectories` plural.
- `file-operations.md`: Procedural guidance on `Copy`, `Sync`, and `Delete` tasks and lazy file handling using `RegularFile`/`Directory` providers.
- `extensions.md`: A guide to creating, getting, and working with extensions.

The references SHALL preserve the frozen corpus and its `Index -> Detail -> Gradle Docs` escalation.

#### Scenario: Apply canonical task annotations
- **WHEN** an agent defines a custom task property
- **THEN** it consults `task-properties.md` to ensure it uses `@Input` or `@InputFiles` (and `@PathSensitive`) instead of the non-existent `@InputDirectories`

#### Scenario: Handle lazy file providers
- **WHEN** an agent needs to work with files in a custom task
- **THEN** it consults `file-operations.md` to apply the provider-backed `RegularFileProperty` and `DirectoryProperty` patterns instead of realized `File` objects

#### Scenario: Use the authored procedural reference
- **WHEN** an agent performs an authoring action covered by the recommendation field guide
- **THEN** it loads the relevant authored reference as the single procedural source
- **AND** it can find the recommendation, its do/don't snippet, and its `gradle_docs` hint there

#### Scenario: Follow corpus escalation without duplication
- **WHEN** the authored reference points to generated best-practice rationale
- **THEN** it links through `references/best-practices/_index.md` and the matching detail file
- **AND** it does not restate the generated corpus prose

### Requirement: JVM Compatibility and Toolchain Doctrine
The skill MUST explicitly decouple the compilation JDK selection (managed by toolchains) from the bytecode and API floor enforcement (managed by `options.release`). It MUST recommend `options.release` as the correct mechanism for targeting a specific Java version, and SHALL NOT equate it with legacy `sourceCompatibility` or `targetCompatibility`.

#### Scenario: Target a specific Java version
- **WHEN** an agent configures Java compilation
- **THEN** it uses `options.release = JvmTarget.JDK_17` (or equivalent) to ensure the bytecode level and API floor are strictly enforced
- **AND** it uses toolchains to select the JDK used for compilation

### Requirement: Daemon JVM Criteria Doctrine
The skill MUST specify that the JVM running the Gradle Daemon is selected by the Daemon JVM criteria (via `gradle/gradle-daemon-jvm.properties` or `updateDaemonJvm`), NOT by project toolchains.

#### Scenario: Configure the Gradle Daemon JVM
- **WHEN** an agent needs to change the JVM used by the Gradle Daemon
- **THEN** it is directed to `gradle/gradle-daemon-jvm.properties` rather than modifying the project's toolchain configuration

### Requirement: Dependency Verification Doctrine
The skill MUST present dependency verification as conditional guidance only, with honest reporting of the UX costs associated with its adoption. Verification implementation — `verification-metadata.xml` authoring, PGP key and checksum workflows, verification repair, and CI verification workflows — MUST route to `advanced-gradle-dependencies` through the Advanced Dependency Depth Handoff. The authoritative dependency procedure (`references/dependencies-and-catalogs.md`) MUST retain the conditional framing, the UX-cost reporting, the locking-vs-verification distinction, and the disable caution, but MUST NOT direct verification implementation inside `authoring-gradle-builds`.

#### Scenario: Implement dependency verification
- **WHEN** an agent is asked to enable dependency verification
- **THEN** it informs the user of the UX costs before applying the configuration
- **AND** it routes the verification metadata, key/checksum, repair, and CI workflow implementation to `advanced-gradle-dependencies`

### Requirement: Task Property Annotation Coverage (Topic 11)
The skill MUST provide authoritative coverage for task property annotations, including:
- The canonical set: `@Input`, `@InputFiles`, `@InputDirectory`, `@OutputDirectory`, `@OutputFile`.
- An explicit note that `@InputDirectories` (plural) does NOT exist.
- The use of `@InputFiles` + `@PathSensitive` for multiple directory inputs.
- Modifiers like `@IgnoreEmptyDirectories`, `@NormalizeLineEndings`, and `@SkipWhenEmpty` (which implies `@Incremental`).
- The requirement that annotations be placed on Kotlin getters.
- The consequence of validation failures (task failure at execution start).

#### Scenario: Verify task input directory
- **WHEN** an agent defines a task with multiple input directories
- **THEN** it uses `@InputFiles` combined with `@PathSensitive` to avoid the non-existent `@InputDirectories` annotation

### Requirement: File Operations Coverage (Topics 12/13)
The skill MUST cover the use of `Copy`, `Sync`, and `Delete` tasks, alongside the depth of lazy file API usage:
- The distinction between provider-backed `RegularFileProperty`/`DirectoryProperty` vs realized `File`/`Path`.
- The use of lazy file trees (`fileTree`, `zipTree`, `tarTree`) and archive-tree laziness.
- `ConfigurableFileCollection` vs `FileCollection`.
- The requirement that no configuration-time iteration or resolution of these provider types occurs.

#### Scenario: Use lazy archive trees
- **WHEN** an agent needs to process a ZIP archive in a build
- **THEN** it uses `zipTree` with lazy evaluation to avoid eager resolution of the archive contents at configuration time

### Requirement: Extensions Coverage (Topic 15)
The skill MUST provide comprehensive guidance on creating, getting, and working with extensions.

#### Scenario: Define a project extension
- **WHEN** an agent needs to create a custom project extension for a plugin
- **THEN** it follows the procedural guide in `extensions.md` to register the extension and work with its properties

### Requirement: Advanced Dependency Depth Handoff
The skill MUST retain basic dependency authoring — dependency declarations, version-catalog basics (everyday catalog entries and library declarations), repository and content-filter wiring, constraints/BOMs, and basic dependency locking — together with the conditional-only dependency verification doctrine and its basic cautions, and MUST route advanced dependency depth to `advanced-gradle-dependencies` through a `## Cross-Skill Handoffs` row and a frontmatter negative trigger. Advanced depth comprises dependency verification implementation (`verification-metadata.xml` authoring, PGP key and checksum workflows, verification repair, and CI verification workflows), feature variants and configuration roles, capability conflict resolution, locking lock modes, advanced version catalog topics, component metadata rules, dependency substitution, composite builds, and repository governance modes.

#### Scenario: Hand off capability conflicts
- **WHEN** an agent hits a capability conflict, feature-variant selection problem, version-catalog work beyond everyday entries, or governance-mode authoring beyond basic repository declaration
- **THEN** `authoring-gradle-builds` routes it to the advanced dependency engineering handoff (`advanced-gradle-dependencies`)

#### Scenario: Hand off dependency verification implementation
- **WHEN** an agent is asked to enable or repair dependency verification, or to author verification metadata, PGP keys or checksums, or CI verification workflows
- **THEN** `authoring-gradle-builds` keeps the conditional-doctrine UX-cost warning and routes the implementation to the advanced dependency engineering handoff (`advanced-gradle-dependencies`)

#### Scenario: Keep basic dependency authoring
- **WHEN** an agent authors basic dependency declarations, everyday version-catalog entries, or basic locking
- **THEN** it stays in `authoring-gradle-builds` without activating the advanced skill

### Requirement: Build Health Assessment Workflow
The skill SHALL provide a Build Health Assessment (Doctor) workflow that assesses a Gradle build primarily by reading its files and applying this skill's own knowledge, and reports prioritized findings as proposals. The workflow SHALL be embedded as `### Build Health Assessment (Doctor)`, replacing `### Performance Audit` in place, and SHALL be bound to exactly one reference file, `references/build-health-assessment.md`, which carries both the assessment procedure and the report material.

The workflow SHALL orient on the wrapper version and project shape, then apply the knowledge-source hierarchy defined in references/build-health-assessment.md ([Knowledge sources](references/build-health-assessment.md#knowledge-sources)): the embedded best-practice corpus (references/best-practices/_index.md plus area pages) is the PRIMARY source to systematically read and apply per applicable area/tag; this skill (SKILL.md already read, and its references, including references/upgrading-and-release-notes.md for migration guides and release notes) is SECONDARY without re-listing their contents; gradle_docs is the SUPPLEMENT for authoritative/current version-specific detail (including wrapper-version migration guides and release notes), relying on the agent's Gradle/build knowledge to determine what applies. The workflow SHALL focus on best practices, build structure, linting, and forward-compat/deprecations, and structure hygiene such as wrapper, toolchain, and configuration hygiene (caching/parallel/configuration-cache posture) — not on verifying build task outputs or artifacts and not on hunting for logic bugs; this is not a full code review of the build logic and build-definition mistakes are incidental per the finding taxonomy — and SHALL NOT enumerate an authored check catalog and SHALL NOT define precise probe evidence contracts beyond minimal observable-signal probes and SHALL NOT add machinery to verify build task outputs or artifacts.

#### Scenario: Health Assessment Happy Path (Static-First)
**WHEN** executing a build health assessment.
**THEN** the agent SHALL:
1. **Orient**: Read `gradle/wrapper/gradle-wrapper.properties` (required) and note the wrapper major version for version-sensitive advice; read `settings.gradle(.kts)`, `gradle/libs.versions.toml` (if present), `gradle.properties`, and the module layout as optional, noting absent optional files as project shape, not findings.
2. **Assess**: Apply the hierarchy in references/build-health-assessment.md ([Knowledge sources](references/build-health-assessment.md#knowledge-sources)); record findings per the finding taxonomy and evidence tags in references/build-health-assessment.md ([Knowledge sources](references/build-health-assessment.md#knowledge-sources)) with a doctrine pointer to the knowledge source it relies on per [Knowledge sources](references/build-health-assessment.md#knowledge-sources).
3. **Probe (only if needed)**: Where static reading cannot produce the evidence, run the relevant cheap assessment command as an ordinary workflow step using the existing MCP tools to surface observable signals (not to verify task outputs or artifacts).
4. **Report and propose**: Finalize findings into a summary-first advisory report grouped by finding class (Build Script Errors → Forward-Compat & Risks → Recommendations → Healthy Areas) and present prioritized recommendations as proposals. This workflow is not a build-implementation audit; build-definition mistakes are incidental and not the focus.

#### Scenario: Embedded Corpus Is the Primary Source
**WHEN** assessing a build against the skill's knowledge.
**THEN** the agent SHALL apply the [Knowledge sources](references/build-health-assessment.md#knowledge-sources) hierarchy in references/build-health-assessment.md per [Knowledge sources](references/build-health-assessment.md#knowledge-sources).

#### Scenario: Probe Used Where Static Cannot Decide
**WHEN** static reading cannot produce the evidence for a claim or finding (e.g., whether a deprecated API is actually exercised, whether configuration cache is compatible for a given task, whether version health or plugin posture matters, or what the last-known build problems were).
**THEN** the agent SHALL run the relevant cheap assessment command as an ordinary workflow step to surface observable signals — `gradle help --warning-mode all` (deprecation warnings), `gradle help --configuration-cache` (configuration-cache compatibility), `inspect_dependencies` (dependency/plugin audit), or `query_build` (last-known build problems) — using the existing MCP tools, and fold the result into the findings; probes SHALL NOT verify build task outputs or artifacts. When a probe (e.g., `gradle help --warning-mode all`) emits deprecation warnings, the agent SHALL capture each deprecation, record it as a Future Breakage finding per the finding taxonomy in references/build-health-assessment.md ([Knowledge sources](references/build-health-assessment.md#knowledge-sources)), and highlight it in the report.

#### Scenario: Probe Failed or Unavailable
**WHEN** a probe fails or is unavailable during the assessment.
**THEN** the agent SHALL continue the assessment using available static evidence, label the probe as unavailable/failed, list which tools/probes were available/ran/skipped in the report header so silent degradation stays transparent, and never guess the probe result; nothing is gated on probe execution and defect/error findings rest on direct static evidence without requiring a probe.

#### Scenario: Corpus Freshness
**WHEN** the embedded best-practices corpus does not cover the project's wrapper major version.
**THEN** the agent SHALL still read and apply the embedded corpus, note the corpus freshness in the report, and consult version-specific detail per [Knowledge sources](references/build-health-assessment.md#knowledge-sources).

### Requirement: Doctor Findings and Report
Findings generated by the Doctor SHALL be advisory and structured for readability, and the report SHALL be summary-first and delivered in conversation, with file output only on user request. Findings SHALL carry a Type per the taxonomy in references/build-health-assessment.md ([Knowledge sources](references/build-health-assessment.md#knowledge-sources)) — `Build script errors / mistakes` (incidental) vs `Best practice / recommendation compliance` (sub-types `Future Breakage` / `Risk` / `Recommendation`) — with severity and confidence calibrated per type and evidence tagged `direct` / `observed` / `web`, and the report SHALL define its format first (Report Template) with examples illustrating the defined classes, splitting findings into classes A. Build Script Errors → B. Forward-Compat & Risks → C. Recommendations → D. Healthy Areas (scoped per class). This workflow is not a build-implementation audit; build-definition mistakes are peripheral and expected to be empty.

#### Scenario: Report Structure
**WHEN** delivering the final assessment.
**THEN** the agent SHALL use a summary-first format:
1. Title and wrapper version plus a scope note listing which tools/probes were available, ran, or were skipped and the corpus-freshness note when applicable.
2. Severity counts and prioritized recommendations (0–5, capped by the number of findings supported by evidence; zero or fewer-than-three is valid for healthy builds — do not manufacture recommendations) with per-class totals (e.g., "0 errors; 2 recommendations in Dependencies" when there are no build script errors but stale dependencies).
3. Findings grouped by class in strict order: A. Build Script Errors (with `Fix:`; incidental — expected to be empty — this workflow is not a build-implementation audit) → B. Forward-Compat & Risks (`Future Breakage` / `Risk`, each labeled with its major e.g. `Future Breakage (Gradle 10)`) → C. Recommendations → D. Healthy Areas scoped per class (e.g., "No Build Script Errors; 3 Recommendations in Dependencies — Healthy Areas: ..."); within A/B/C group by area; include Evidence tags `direct` / `observed` / `web`.
4. Optional evidence note (which probes ran, failed, or were skipped with `direct` / `observed` / `web` tags).
5. Next-step proposal with an explicit approval prompt for applying edits.

#### Scenario: Render Finding
**WHEN** reporting a build health issue.
**THEN** the agent SHALL include:
- **Type**: `Build script errors / mistakes` (incidental) or `Best practice / recommendation compliance` with sub-type `Future Breakage` / `Risk` / `Recommendation` where applicable per the taxonomy in references/build-health-assessment.md ([Knowledge sources](references/build-health-assessment.md#knowledge-sources)).
- **Area**: A free-form area heading (e.g., Structure, Build Logic, Dependencies, Performance, Reproducibility — not a closed taxonomy).
- **Severity**: One of `high`, `medium`, `low`, or `info` with a one-line rationale, calibrated per type: `Future Breakage` → `high` only if removal lands in the next Gradle major per the upgrading/release-notes references per [Knowledge sources](references/build-health-assessment.md#knowledge-sources) else `medium` (labeled `Future Breakage (Gradle 10)` or the next major with deprecation-since evidence); `Risk` → `high` if prod-facing or widening blast radius, else `medium`; `Recommendation` → `medium` / `low` / `info` by impact; `Build script errors / mistakes` (incidental) → always `high` when present.
- **Evidence**: File/line excerpt or probe snippet tagged `direct` (from the build files themselves), `observed` (output of the standard minimal probes — e.g., deprecation warnings from the help task), or `web` (version lookups); if a probe was unavailable or failed, say so here. Incidental build-definition observations, when present, rest on `direct` static evidence without requiring a probe.
- **Why**: One sentence with a doctrine pointer to the knowledge source it relies on, per [Knowledge sources](references/build-health-assessment.md#knowledge-sources).
- **Fix / Consider / Risk**: `Fix:` for incidental build-definition observations; `Consider:` / `Risk:` for compliance findings — concrete proposed edit or migration.
- **Confidence** *(optional)*: `high`/`medium`/`low` with a short reason, calibrated per type: Future Breakage `high` only if next-major removal else `medium`; Risk `medium` to `high` by exposure; Recommendation `medium`/`low`/`info`; incidental errors `high` when present.

### Requirement: Doctor Consented Remediation
The Doctor SHALL only propose fixes and SHALL never apply them silently. Remediation SHALL proceed only with explicit user approval per fix, applied through this skill's existing authoring workflows. The Doctor SHALL NOT retain a second performance workflow: `### Performance Audit` is replaced in place by `### Build Health Assessment (Doctor)`, and exactly one doctor reference file exists at `references/build-health-assessment.md`.

#### Scenario: Remediation Process
**WHEN** implementing a recommended fix from a health assessment.
**THEN** the agent SHALL:
1. Obtain explicit user approval for the specific fix.
2. Apply the fix through this skill's existing authoring workflows, routing dependency-deep work to `advanced-gradle-dependencies` via the existing handoff.
3. Re-check the fix the cheapest way (re-read the changed files or re-run the same probe) before marking it resolved.

