# Capability: authoring-gradle-builds

## Description
Designs and implements modifications to Gradle build definitions, wiring, and project structure.

### Use when
- Creating or modifying `build.gradle.kts` or `settings.gradle.kts` files.
- Organizing projects into modules or defining convention plugins.
- Managing dependency catalogs (`libs.versions.toml`), declarations, and repositories.
- Configuring JVM toolchains, compiler options, or test frameworks.
- Implementing publishing logic, dependency locking, or CI/CD pipeline wiring.
- Applying Gradle build best practices.

### Do NOT use
- Executing tasks or diagnosing failures in an existing build (use `using-gradle`).
- Researching internal Gradle APIs without the intent to use them in a build script.
- Executing arbitrary project code via the REPL.
- Rendering Compose UI components.

## ADDED Requirements

### Requirement: Modification Index
MUST provide a workflow index for build authoring, focused on the modification lifecycle and safe application of patterns.

#### Scenario:
An agent needs to implement a new project module definition; it consults the `authoring-gradle-builds` body for the "Create Module" workflow, then loads the specific reference for `settings.gradle.kts` modifications.

### Requirement: Dependency Modification
MUST consolidate all dependency declaration, catalog management, and repository wiring capabilities (formerly `managing-gradle_dependencies` modification) into this skill.

#### Scenario:
An agent is asked to add a new library to a project but must follow a version catalog pattern; it stays within `authoring-gradle-builds` to update `libs.versions.toml` and add the reference to the build script.

### Requirement: `afterEvaluate` Prohibition
MUST explicitly prohibit the use of `afterEvaluate` except when a documented correctness-critical ordering constraint exists that cannot be solved by `Provider` wiring or other standard APIs.

#### Scenario:
An agent proposes a build script change using `afterEvaluate` to fix a property ordering issue; the skill's core safety constraint triggers a rewrite using `Provider` or Lazy Configuration.

### Requirement: Best Practices Integration
MUST integrate the generated best-practice corpus as rooted local references, maintaining the specified lookup order (Index $\rightarrow$ Detail $\rightarrow$ Gradle Docs).

#### Scenario:
An agent is deciding between two plugin application patterns; it queries the generated `best-practices-index.md` to find the approved project pattern before implementing.

### Requirement: Progressive Disclosure
MUST implement a root-local reference system where detailed authoring procedures are stored in separate files and loaded only upon specific trigger.

#### Scenario:
Detailed steps for "Implementing Publishing Logic" are moved to a separate reference to keep the core body focused on the workflow index and safety constraints.
