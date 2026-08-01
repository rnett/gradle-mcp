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

## ADDED Requirements

### Requirement: Modification Index
MUST provide a workflow index for build authoring, focused on the modification lifecycle and safe application of patterns.

#### Scenario:
An agent needs to implement a new project module definition; it consults the `authoring-gradle-builds` body for the "Create Module" workflow, then loads the specific reference for `settings.gradle.kts` modifications.

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
MUST integrate the frozen generated best-practice corpus as rooted local references, maintaining the lookup order `Index -> Detail -> Gradle Docs`, and MUST avoid duplicating authored guidance that belongs in the generated corpus. The authored reference is the single authoritative procedural load for each authoring action; corpus detail is optional rationale consulted on demand, not a mandatory competing procedural load.

#### Scenario:
An agent is deciding between two plugin application patterns; it loads the relevant authored reference as the single procedural source, then optionally follows `references/best-practices/_index.md` to the matching generated detail entry and queries version-scoped `gradle_docs` when deeper rationale or the authoritative source is needed. The escalation path remains `Index -> Detail -> Gradle Docs` without requiring a second competing procedural load.

#### Scenario:
An agent needs a best-practice explanation already covered by the generated corpus; it links to the corpus entry and authoritative Gradle documentation instead of restating or creating a competing copy in the authored skill references.

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
The skill MUST route every major authoring topic to a verified version-scoped Gradle documentation hint and published `docs.gradle.org` URL, and MUST link relevant MCP tools to their published documentation pages. Advanced authoring topics MUST include service injection, build services, value sources, project isolation, build lifecycle, Kotlin DSL, managed types/providers (including incubating dataflow actions), binary plugin development with TestKit and publishing, Java builds and source sets/annotation processing, configurations and feature variants with variant-aware sharing, and build-cache/configuration-cache authoring and debugging. Cache and isolation enablement, persistent configuration, and operational outcome reading route to `using-gradle`; cacheability and configuration-cache-safe authoring remain here.

#### Scenario:
An agent needs guidance on dependencies, modules and settings, convention plugins, custom tasks, build lifecycle, Kotlin DSL, managed types/providers, toolchains, Kotlin compiler options, Java builds, configurations and variants, testing, plugin development and TestKit, publishing, locking, CI, build scans, or advanced configuration; the relevant reference provides a `gradle_docs` tag and path hint plus the corresponding versioned `docs.gradle.org` URL.

#### Scenario:
An agent needs to implement service injection, a build service, a value source, or project-isolation-compatible logic; it is routed to the advanced authoring reference and its verified Gradle documentation rather than an undocumented or generic recommendation.

#### Scenario:
An agent is ready to inspect dependencies, look up Maven releases, query Gradle documentation, or read dependency sources while authoring; the relevant reference points to the MCP tool's verified published page under `https://gradle-mcp.rnett.dev/latest/tools/` and keeps execution or read-only inspection handoffs in `using-gradle`.

### Requirement: Build Lifecycle and Script Model Coverage
MUST provide a reference covering the three lifecycle phases, the configuration-cache serialization sub-phase, the task-graph DAG (not script order), and hook/listener ordering, with version notes and verified documentation pointers.

#### Scenario:
An agent reasons about why top-level script code runs at configuration and loads `build-lifecycle.md` before placing logic.

### Requirement: Kotlin DSL Authoring Coverage
MUST provide a Kotlin DSL primer covering accessor generation and timing, receivers, script naming, public-API-only usage, IDE import, and limitations, operationalizing the Kotlin DSL preference and linking the frozen rationale.

#### Scenario:
An agent troubleshoots missing accessors and is routed to accessor-timing guidance.

### Requirement: Managed Types and Lazy Configuration Coverage
MUST provide a canonical Property/Provider/managed-object reference covering set-vs-convention, finalizeValue, map/flatMap/zip/orElse, collections/containers, and lazy files, which sibling references link to instead of restating; incubating dataflow actions MUST be gated as non-default.

#### Scenario:
An agent wires a lazy file provider and loads the managed-types reference rather than duplicating Property semantics in a task reference.

### Requirement: Plugin Development Coverage
MUST cover binary plugin authoring (`java-gradle-plugin`, `gradlePlugin{}`, markers), TestKit functional testing, plugin publishing/Portal/signing, and plugin-ID governance, keeping consumer-side test configuration in `testing-configuration.md`.

#### Scenario:
An agent publishing a plugin is routed to Portal publishing with `--validate-only` and credentials-out-of-source guidance.

### Requirement: Java Builds and Variant-Aware Configuration Coverage
MUST cover Java plugin/source sets/annotation processing/mixed languages and the declarable/resolvable/consumable configuration model with feature variants, capabilities, and variant-aware sharing; variant-aware resolution MUST be preferred over low-level `project(path, configuration)`.

#### Scenario:
An agent sharing outputs between projects is routed to the variant-aware recipe, not a cross-project task dependency.

### Requirement: Build Cache and Configuration Cache Authoring Coverage
MUST cover the cacheability contract (determinism, normalization, unique outputs, `@CacheableTask` as a correctness promise), the configuration-cache requirements matrix, report-driven debugging, and isolated-projects constraint families; frozen enablement corpus entries MUST be labeled as usage rationale handed off to `using-gradle`.

#### Scenario:
An agent making a task cacheable loads the authoring contract and is handed off to `using-gradle` for persistent cache enablement.
