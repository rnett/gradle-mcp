---
name: authoring-gradle-builds
description: |
  Authors and modifies Gradle build definitions, project structure, build logic, and delivery wiring, including dependencies and catalogs, convention plugins, custom tasks, build lifecycle, Kotlin DSL, managed types and providers, toolchain and Java builds, configurations and variants, binary plugin development, Kotlin compiler options, publishing via the Central Portal, CI, locking, build scans, Worker API, continuous builds, and advanced configuration such as service injection, build services, value sources, and project isolation.

  ## Positive Triggers (when to activate)
  - Authoring or modifying build.gradle(.kts), settings.gradle(.kts), convention plugins, modules, or subprojects.
  - Adding or changing dependency declarations, version catalogs, repositories, or plugin management.
  - Configuring JDK toolchains, Kotlin compiler options, test frameworks, publishing, or CI wiring.
  - Declaring or modifying composite builds (included builds via `includeBuild`, build-logic wiring).
  - Creating custom tasks, worker actions, build services, value sources, service injection, or project-isolation-compatible build logic.
  - Modifying advanced Gradle configuration and build performance settings.

  ## Negative Triggers (when NOT to activate)
  - Operation/execution (running builds, running tests, diagnosing failures, and read-only dependency inspection/update discovery) belongs to `using-gradle`; authoring/modifying build definitions (including dependency declarations and version catalogs) belongs to `authoring-gradle-builds`. Trivial one-line everyday dependency edits (catalog entry + declaration + version bump) are a sanctioned overlap in `using-gradle`; anything structural (plugins, repositories, modules, toolchains, publishing, CI) is `authoring-gradle-builds` only.
  - Researching internal Gradle APIs (use `using-gradle`'s research workflow).
  - Probing runtime project code (use `interacting-with-project-runtime`).
  - Verifying Compose UI (use `verifying-compose-ui`).
  - Advanced dependency engineering — variant-aware resolution diagnostics, dependency verification implementation (verification-metadata.xml authoring, PGP key and checksum workflows, verification repair, and CI verification workflows), component metadata rules, dependency substitution rules and composite-build diagnosis, capability conflicts, lock modes beyond basics, advanced version catalogs, and repository governance modes (use `advanced-gradle-dependencies`). Composite-build authoring stays here. Basic dependency declaration, version-catalog basics, and basic locking stay here.
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "1.4.0"
---

# Gradle Build Authoring

Author or modify Gradle build definitions, build logic, project structure, and delivery wiring. Optimize for lazy, decoupled, configuration-cache-compatible builds.

**More info**: Search the User Guide with `gradle_docs(query="tag:userguide <term>")` or best practices with `gradle_docs(query="tag:best-practices <term>")`. Read `gradle/wrapper/gradle-wrapper.properties` before any version-sensitive authoring.

## Before You Modify

1. Read `gradle/wrapper/gradle-wrapper.properties`; identify the wrapper version.
2. Consult the compatibility quick-reference below; verify version-sensitive claims with `gradle_docs(query="tag:userguide <term>")`.
3. When the change is version-sensitive (wrapper upgrade, API migration, deprecation fix), consult the upgrading page for the wrapper's major version via `gradle_docs(path="userguide/upgrading_version_<N>.md")` and check `gradle_docs(query="tag:release-notes")` for breaking changes. See [Upgrading and Release Notes](references/upgrading-and-release-notes.md).
4. Read `settings.gradle.kts`, `gradle/libs.versions.toml`, applied plugins, and convention plugins. Check for existing conventions before proposing changes.
5. Load the narrowest authored reference: links in the directives and workflows above are loaded in context; for the remaining actions, use the Decision Routing table.
6. Treat `references/best-practices/_index.md` and its generated corpus detail as optional rationale, consulted on demand rather than as a mandatory pre-load.

## Compatibility Quick-Reference

| Behavior | Gradle 9 | Gradle 8.x | Gradle 7.x / fallback |
|---|---|---|---|
| Version catalogs | Stable; prefer them | Stable; prefer them | 7.4+ stable; 7.0-7.3 preserve an existing catalog cautiously, otherwise use `buildSrc`, applied scripts, or `ext` |
| Configuration cache | Stable and opt-in; stable ≠ every plugin/build compatible, 9.x strictness still evolving; enable when compatible | Stable from 8.1; 8.0 pre-stable | Incubating/experimental; use only for explicit migration experiments |
| Project isolation | Experimental (not yet incubating); diagnostics change across 9.x minors | Do not enable as a baseline; use decoupled logic | Use decoupled logic + provider wiring; no isolation support |
| Dependency notation | Map notation deprecated since 9.1 and fails in Gradle 10; use single-string GAV or catalog accessors | Use single-string GAV or catalog accessors | Use single-string GAV or catalog accessors |
| Toolchain auto-provisioning | Supported through a resolver plugin configured in settings | Supported; resolver plugin availability is version-specific | 7.5 auto-download; 7.6 pluggable resolver repositories; earlier versions require a local JDK |
| JVM required to run Gradle | 17+ | Java 8 minimum; maximum varies by minor | Java 8 minimum; 7.0-7.2 cannot run on Java 17, 7.3+ can |
| Kotlin DSL / `compilerOptions` | Prefer Kotlin DSL; use typed `compilerOptions` for current KGP | Prefer Kotlin DSL; verify KGP API version | Kotlin DSL is supported; use version-compatible compiler options and hedge unverified KGP boundaries |

## Constitution

- Prefer [Kotlin DSL](references/kotlin-dsl.md) for new authoring (`gradle_docs(path="userguide/best_practices_general.md")`; use Groovy only when the project requires it).
- Register lazily: use [Custom Tasks](references/custom-tasks.md), `tasks.register`, `tasks.named`, and `configureEach`, not eager `tasks.create` (`gradle_docs(path="userguide/task_configuration_avoidance.md")`).
- Use version catalogs when present; centralize versions and aliases (`gradle_docs(path="userguide/best_practices_dependencies.md")`; catalogs are stable from 7.4).
- Check existing conventions first; use declarative `plugins {}` and settings `pluginManagement {}` (`gradle_docs(path="userguide/plugins.md")`, `gradle_docs(path="userguide/best_practices_structuring_builds.md")`).
- Never use `allprojects` or `subprojects`; apply explicit [Convention Plugins](references/convention-plugins.md) and keep projects decoupled (`gradle_docs(path="userguide/best_practices_structuring_builds.md")`, `gradle_docs(path="userguide/isolated_projects.md")`).
- Never use `Project` or `Task.project` inside task actions; inject [Advanced Configuration](references/advanced-configuration.md) services and model task inputs (`gradle_docs(path="userguide/configuration_cache_requirements.md")`, `gradle_docs(path="userguide/service_injection.md")`).
- Never resolve configurations in the configuration phase; resolve through task inputs or task execution (`gradle_docs(path="userguide/best_practices_tasks.md")`).
- Do not call `Provider.get()` while configuring unrelated work; wire [Managed Types and Providers](references/managed-types-and-providers.md) with `Provider` and `Property` values lazily (`gradle_docs(path="userguide/properties_providers.md")`, `gradle_docs(path="userguide/best_practices_tasks.md")`).
- Prohibit `afterEvaluate`; use providers, `pluginManager.withPlugin`, and lazy APIs. Permit it only for a documented correctness-critical ordering constraint, with an `afterEvaluate-justification:` comment (`gradle_docs(path="userguide/best_practices_general.md")`).

## Always-Loaded Best-Practice Footguns

These compact rules are loaded before any authoring reference. Links provide detailed rationale or, where available, the frozen generated detail; use the linked reference for the procedural guidance.

- **Model initialization, configuration, and execution separately.** Phase boundaries are easy to blur, and the resulting ordering and performance bugs are often silent. See [Build Lifecycle](references/build-lifecycle.md).
- **Keep expensive work out of configuration.** Unselected tasks still pay configuration-time costs, which makes this mistake hard to spot from a successful build. See [Build Lifecycle](references/build-lifecycle.md).
- **Use configuration avoidance throughout the model.** Eager APIs look harmless but silently realize tasks and domain objects before they are needed. See [Custom Tasks](references/custom-tasks.md).
- **Propagate laziness with providers and managed properties.** Provider-looking values can still be realized too early, losing provenance and cache inputs. See [Managed Types and Providers](references/managed-types-and-providers.md).
- **Read providers only at an execution boundary.** Configuration-time reads can work in simple builds while breaking laziness or cache behavior in larger ones. See [Custom Tasks](references/custom-tasks.md).
- **Use provider-backed managed model types.** Ad hoc mutable fields hide validation, lifecycle, and caching semantics that Gradle must observe. See [Managed Types and Providers](references/managed-types-and-providers.md).
- **Use public APIs and injected services only.** Internal types often appear convenient until an upgrade exposes an undocumented compatibility break. See [Advanced Configuration](references/advanced-configuration.md).
- **Wire cross-project behavior through model relationships.** Callback-based mutation depends on evaluation order and becomes hostile to project isolation. See [Convention Plugins](references/convention-plugins.md).
- **Avoid `afterEvaluate` and `projectsEvaluated` as configuration mechanisms (version-sensitive).** Their timing can appear to repair ordering while masking a model relationship that should be explicit; read the wrapper first. See [Build Lifecycle](references/build-lifecycle.md).
- **Distinguish `set(null)` from an absent provider.** Both represent "no value" at a glance, but only one lets a convention apply. See [Managed Types and Providers](references/managed-types-and-providers.md).
- **Never resolve or iterate at configuration time.** Configuration-phase resolution, iteration, or eager file-tree walking realizes values early and breaks laziness, the configuration cache, and project isolation. See [File Operations](references/file-operations.md) and [Managed Types and Providers](references/managed-types-and-providers.md).
- **Do not capture realized files or `Project`.** Retaining an eager `File`/`Path` or the `Project` object freezes values that must stay lazy and is incompatible with the configuration cache and isolated projects. See [File Operations](references/file-operations.md).
- **Prefer provider wiring over declaration copying.** Connect task and extension properties with providers (`set(...)`, `from(...)`, `map`/`flatMap`) so changes propagate without re-realizing values. See [Managed Types and Providers](references/managed-types-and-providers.md).
- Operation/execution (running builds, running tests, diagnosing failures, and read-only dependency inspection/update discovery) belongs to `using-gradle`; authoring/modifying build definitions (including dependency declarations and version catalogs) belongs to `authoring-gradle-builds`. Trivial one-line everyday dependency edits (catalog entry + declaration + version bump) are a sanctioned overlap in `using-gradle`; anything structural (plugins, repositories, modules, toolchains, publishing, CI) is `authoring-gradle-builds` only.

## Decision Routing

| Authoring action | Reference |
|---|---|
| Configure a JDK toolchain or resolver | [JDK Toolchains](references/jdk-toolchains.md) |
| Configure Kotlin compiler options | [Kotlin Compiler Options](references/kotlin-compiler-options.md) |
| Configure test frameworks or test behavior | [Testing Configuration](references/testing-configuration.md) |
| Publish artifacts or configure Central Portal delivery | [Artifact Publishing](references/artifact-publishing.md) |
| Customize published variants, components, or artifacts | [Artifact Publishing](references/artifact-publishing.md) |
| Wire CI/CD builds | [CI/CD Builds](references/ci-cd-builds.md) |
| Enable or update dependency locking | [Dependency Locking](references/dependency-locking.md) |
| Parallelize task work with Worker API | [Worker API](references/worker-api.md) |
| Configure continuous builds | [Continuous Builds](references/continuous-builds.md) |
| Declare or modify composite builds (included builds, `includeBuild`, build-logic) | [Composite Builds](references/composite-builds.md) |
| Understand build lifecycle, phases, task graph, or hook ordering | [Build Lifecycle](references/build-lifecycle.md) |
| Develop a binary plugin, test with TestKit, or publish a plugin | [Plugin Development](references/plugin-development.md) |
| Configure Java source sets, annotation processing, or mixed languages | [Java Builds](references/java-builds.md) |
| Model configurations, feature variants, capabilities, or variant sharing | [Configurations and Variants](references/configurations-and-variants.md) |
| Declare custom task property annotations or model task inputs/outputs | [Task Properties](references/task-properties.md) |
| Copy, sync, delete, or lazily handle files in a task | [File Operations](references/file-operations.md) |
| Create, get, or work with a plugin extension | [Extensions](references/extensions.md) |

## Cross-Skill Handoffs

- Build execution, task running, test running, failure diagnosis, or read-only dependency inspection -> `using-gradle`.
- Enabling/persisting the build cache or configuration cache (gradle.properties/CLI flags, local/remote cache config, CI rollout, cache cleanup) and reading runtime cache/isolation outcomes -> `using-gradle`. This skill authors cacheability and config-cache-safe logic; it does not own enablement.
- Enabling isolated-projects flags/diagnostics and interpreting diagnostics output -> `using-gradle`.
- Runtime probing or arbitrary JVM/Kotlin execution -> `interacting-with-project-runtime`.
- Compose UI rendering or verification -> `verifying-compose-ui`.
- Advanced Dependency Engineering -> `advanced-gradle-dependencies`. Routes advanced dependency depth out, including dependency verification implementation (verification-metadata.xml authoring, PGP key and checksum workflows, verification repair, and CI verification workflows), component metadata rules, dependency substitution rules and composite-build diagnosis, capability conflicts, lock modes beyond basics, advanced version catalogs, and repository governance modes. Composite-build authoring stays here. Basic dependency declaration, version-catalog basics, and basic locking stay here.

## Workflows

### Create Module

1. Read the wrapper version, settings, project layout, catalogs, and applied conventions.
2. Load [Modules and Settings](references/modules-and-settings.md) as the single authoritative procedural reference; add the project and its build logic without root-wide mutation.
3. Use existing convention plugins and version aliases; add only module-specific configuration.
4. Hand off to `using-gradle` to verify project discovery and the module's lifecycle tasks.

### Add Dependency

1. Determine whether the change is structural; hand off read-only GAV discovery to `using-gradle`.
2. Load [Dependencies and Catalogs](references/dependencies-and-catalogs.md) as the single authoritative procedural reference; update the catalog when one exists and declare the alias in the consuming project.
3. Centralize repositories in settings and apply content filters when multiple repositories are required.
4. Hand off to `using-gradle` to verify dependency resolution and the affected configuration.

### Performance Audit

1. Read the wrapper version and use the narrowest authored reference as the single authoritative procedural reference for each audit action; links in the directives and workflows above identify references that are already loaded in context.
2. Optionally consult [Best-Practices Index](references/best-practices/_index.md) and its generated detail for rationale; this is not a competing procedural load.
3. Inspect build logic for eager task APIs, provider realization, configuration-phase resolution, cross-project mutation, and configuration-cache violations.
4. Apply the smallest lazy, decoupled change; use [Build Scans](references/build-scans.md) only when publication is intentional.
5. Hand off to `using-gradle` to run the relevant verification and inspect task outcomes or configuration-cache diagnostics.

## Best-Practices Consultation

Use the authored reference linked in the relevant directive or workflow as the single authoritative procedural load when one is provided; for the remaining authoring actions, use the Decision Routing table. Consult `references/best-practices/_index.md` and its generated corpus detail only when rationale is needed or the authored reference points there; then use `gradle_docs(query="tag:userguide <term>")` when deeper rationale or the authoritative version-scoped source is required. The escalation path remains `Index $\rightarrow$ Detail $\rightarrow$ Gradle Docs`, but it does not force a second competing procedural load. The corpus is frozen: route to it, do not edit it or restate its detail in this hub.
