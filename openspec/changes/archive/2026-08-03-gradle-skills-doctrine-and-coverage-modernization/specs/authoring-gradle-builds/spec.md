# Capability Deltas: authoring-gradle-builds

## MODIFIED Requirements

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

### Requirement: Java Builds and Variant-Aware Configuration Coverage
The skill MUST cover Java plugin/source sets/annotation processing/mixed languages and the declarable/resolvable/consumable configuration model with feature variants, capabilities, and variant-aware sharing; variant-aware resolution MUST be preferred over low-level `project(path, configuration)`. This MUST include a comprehensive guide on custom attributes for configurations and dependency resolution.

#### Scenario: Use custom attributes for resolution
- **WHEN** an agent needs to differentiate dependencies by an attribute (e.g. "classification" or "targetPlatform")
- **THEN** it defines the custom attribute and uses it within the configuration's attribute compatibility rules to control resolution

#### Scenario: Share outputs between projects
- **WHEN** an agent shares outputs between projects
- **THEN** it is routed to the variant-aware recipe, not a cross-project task dependency

### Requirement: Build Cache and Configuration Cache Authoring Coverage
The skill MUST cover the cacheability contract (determinism, normalization, unique outputs, `@CacheableTask` as a correctness promise), the configuration-cache requirements matrix, report-driven debugging, and isolated-projects constraint families; frozen enablement corpus entries MUST be labeled as usage rationale handed off to `using-gradle`. Guidance on build-cache, configuration-cache, and IP-compatibility MUST be woven throughout the authoring advice, emphasizing the prevention of eager configuration-time resolution.

#### Scenario: Author an IP-compatible aggregation
- **WHEN** an agent needs to aggregate data or artifacts across subprojects
- **THEN** it implements this in an isolated-projects compatible way, avoiding direct `projects` map access and using a shared configuration or artifact-based approach

#### Scenario: Make a task cacheable
- **WHEN** an agent makes a task cacheable
- **THEN** it loads the authoring contract and is handed off to `using-gradle` for persistent cache enablement

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

## ADDED Requirements

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
The skill MUST present dependency verification as conditional guidance only, with honest reporting of the UX costs associated with its adoption.

#### Scenario: Implement dependency verification
- **WHEN** an agent is asked to enable dependency verification
- **THEN** it informs the user of the UX costs before applying the configuration

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

