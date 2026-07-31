---
name: authoring-gradle-builds
description: |
  Designs and implements modifications to Gradle build definitions, wiring, and project structure.

  ## Positive Triggers (when to activate)
  - Creating or modifying build.gradle.kts or settings.gradle.kts files.
  - Organizing projects into modules or defining convention plugins.
  - Managing dependency catalogs (libs.versions.toml), declarations, and repositories.
  - Configuring JVM toolchains, compiler options, or test frameworks.
  - Implementing publishing logic, dependency locking, or CI/CD pipeline wiring.
  - Applying Gradle build best practices.

  ## Negative Triggers (when NOT to activate)
  - Executing tasks or diagnosing failures in an existing build (use `using-gradle`).
  - Researching internal Gradle APIs without the intent to use them in a build script (use `using-gradle`).
  - Executing arbitrary project code via the REPL (use `interacting-with-project-runtime`).
  - Rendering Compose UI components (use `verifying-compose-ui`).
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "1.0.0"
---
<!--
class: authored-local
skill: authoring-gradle-builds
-->

# Gradle Build Authoring & Build Logic Engineering

Designs, writes, and refactors Gradle build scripts, creates modules, manages dependencies, and applies idiomatic build conventions.

## Constitution

- **ALWAYS** prefer Kotlin DSL (`.kts`) unless the project explicitly uses Groovy.
- **ALWAYS** use lazy APIs (`tasks.register`) instead of eager APIs (`tasks.create`).
- **ALWAYS** use version catalogs (`gradle/libs.versions.toml`) for dependency management when present.
- **ALWAYS** check for existing conventions before proposing changes.
- **NEVER** use `allprojects`/`subprojects` blocks — use convention plugins applied selectively.
- **NEVER** access the `Project` object directly inside task actions — use `Property<T>` and `Provider<T>`.
- **NEVER** resolve configurations during the configuration phase.

## Safety: `afterEvaluate` Prohibition

**`afterEvaluate` is prohibited** except when a documented correctness-critical ordering constraint exists that cannot be solved by `Provider` wiring, lazy configuration, or other standard APIs. If you find yourself reaching for `afterEvaluate`:

1. First try `Provider`/`Property` wiring for lazy evaluation.
2. Then try `tasks.named(...)` / `tasks.configureEach` for deferred configuration.
3. Only use `afterEvaluate` as a last resort, and document it with an `afterEvaluate-justification:` comment explaining why no other approach works.

## Decision Routing

| Need | Reference | Load When |
|------|-----------|-----------|
| Add or modify dependencies | [Dependency Declaration](references/dependency-declaration.md) | Adding libraries, catalogs, or repositories |
| Create a new module | [Common Build Patterns](references/common-build-patterns.md) | Adding a new subproject or module |
| Configure toolchains or compiler | [JDK Toolchains](references/jdk-toolchains.md) | Setting up JDK requirements |
| Set up test frameworks | [Testing Configuration](references/testing-configuration.md) | Configuring JUnit, Kotest, etc. |
| Manage version catalogs | [Version Catalogs](references/version-catalogs.md) | Working with libs.versions.toml |
| Implement publishing | [Artifact Publishing](references/artifact-publishing.md) | Publishing to Maven or other repos |
| Set up CI/CD | [CI/CD Builds](references/ci-cd-builds.md) | Wiring builds for CI pipelines |
| Configure dependency locking | [Dependency Locking](references/dependency-locking.md) | Locking dependency versions |
| Enable build scans | [Build Scans](references/build-scans.md) | Collecting build performance data |
| Use convention plugins | [Common Build Patterns](references/common-build-patterns.md) | Refactoring shared build logic |
| Configure compiler options | [Kotlin Compiler Options](references/kotlin-compiler-options.md) | Setting Kotlin compiler flags |
| Use worker API | [Worker API](references/worker-api.md) | Parallelizing task work |
| Apply best practices | [Best Practices Index](references/best-practices/_index.md) | Before changing build logic |

## Cross-Skill Handoffs

- **Running builds, tests, or diagnostics** → Load `using-gradle`.
- **Dependency graph inspection or updates** → Load `using-gradle`.
- **Runtime code probing** → Load `interacting-with-project-runtime`.

## Workflows

### Creating a New Module

1. Discover the existing module layout via `using-gradle` → Project Structure.
2. Create directory structure and add to `settings.gradle.kts`.
3. Create `build.gradle.kts` using convention plugins and version catalogs.
4. Verify the module is recognized via `using-gradle`.

See [Common Build Patterns](references/common-build-patterns.md) for the full procedure.

### Adding a Dependency

1. Use `using-gradle` → Dependency Inspection to verify the correct GAV.
2. Add the version and library alias to `gradle/libs.versions.toml`.
3. Add the dependency to the consuming module's `build.gradle.kts`.
4. Verify resolution via `using-gradle` → Dependency Inspection.

See [Dependency Declaration](references/dependency-declaration.md) for the full procedure.

### Performance Audit

1. Check configuration cache status.
2. Run a build scan for performance data.
3. Identify violations and propose lazy API migrations.
4. Consult the best practices reference first.

## Best-Practices Consultation

Start with `references/best-practices/_index.md`. Choose the relevant area, then open the linked detail file before changing build logic. Treat the detail page as focused implementation guidance.

For version-specific guidance, use `gradle_docs(query="tag:best-practices <term>")`.
