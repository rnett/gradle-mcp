---
name: authoring-gradle-builds
description: |
  Authors and modifies Gradle build definitions, project structure, build logic, and delivery wiring, including dependencies and catalogs, convention plugins, custom tasks, build lifecycle, Kotlin DSL, managed types and providers, toolchain and Java builds, configurations and variants, binary plugin development, Kotlin compiler options, publishing via the Central Portal, CI, locking, build scans, Worker API, continuous builds, and advanced configuration such as service injection, build services, value sources, and project isolation.

  ## Positive Triggers (when to activate)
  - Authoring or modifying build.gradle(.kts), settings.gradle(.kts), convention plugins, modules, or subprojects.
  - Adding or changing dependency declarations, version catalogs, repositories, or plugin management.
  - Configuring JDK toolchains, Kotlin compiler options, test frameworks, publishing, or CI wiring.
  - Creating custom tasks, worker actions, build services, value sources, service injection, or project-isolation-compatible build logic.
  - Modifying advanced Gradle configuration and build performance settings.

  ## Negative Triggers (when NOT to activate)
  - Operation/execution (running builds, running tests, diagnosing failures, and read-only dependency inspection/update discovery) belongs to `using-gradle`; authoring/modifying build definitions (including dependency declarations and version catalogs) belongs to `authoring-gradle-builds`. Trivial one-line everyday dependency edits (catalog entry + declaration + version bump) are a sanctioned overlap in `using-gradle`; anything structural (plugins, repositories, modules, toolchains, publishing, CI) is `authoring-gradle-builds` only.
  - Researching internal Gradle APIs (use `using-gradle`'s research workflow).
  - Probing runtime project code (use `interacting-with-project-runtime`).
  - Verifying Compose UI (use `verifying-compose-ui`).
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "1.2.0"
---
<!--
class: authored-local
skill: authoring-gradle-builds
-->

# Gradle Build Authoring

Author or modify Gradle build definitions, build logic, project structure, and delivery wiring. Optimize for lazy, decoupled, configuration-cache-compatible builds.

**More info**: Query `gradle_docs` with `tag:userguide` or `tag:best-practices` plus the topic path. Read `gradle/wrapper/gradle-wrapper.properties` before any version-sensitive authoring. Published MCP tool docs use grouped uppercase source-file routes under https://gradle-mcp.rnett.dev/latest/tools/, not per-tool pages; use the docs-references brief or `using-gradle/references/research.md` for exact pages and `gradle_docs` mechanics.

## Before You Modify

1. Read `gradle/wrapper/gradle-wrapper.properties`; identify the wrapper version.
2. Consult the compatibility quick-reference below; verify version-sensitive claims with `gradle_docs`.
3. Read `settings.gradle.kts`, `gradle/libs.versions.toml`, applied plugins, and convention plugins. Check for existing conventions before proposing changes.
4. Use the Decision Routing table to load the narrowest authored reference, which is the single authoritative procedural load for the authoring action.
5. Treat `references/best-practices/_index.md` and its generated corpus detail as optional rationale, consulted on demand rather than as a mandatory pre-load.

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

- Prefer Kotlin DSL for new authoring (`best_practices_general.md`; use Groovy only when the project requires it).
- Register lazily: use `tasks.register`, `tasks.named`, and `configureEach`, not eager `tasks.create` (`task_configuration_avoidance.md`).
- Use version catalogs when present; centralize versions and aliases (`best_practices_dependencies.md`; catalogs are stable from 7.4).
- Check existing conventions first; use declarative `plugins {}` and settings `pluginManagement {}` (`plugins.md`, `best_practices_structuring_builds.md`).
- Never use `allprojects` or `subprojects`; apply explicit convention plugins and keep projects decoupled (`best_practices_structuring_builds.md`, `isolated_projects.md`).
- Never use `Project` or `Task.project` inside task actions; inject public services and model task inputs (`configuration_cache_requirements.md`, `service_injection.md`).
- Never resolve configurations in the configuration phase; resolve through task inputs or task execution (`best_practices_tasks.md`).
- Do not call `Provider.get()` while configuring unrelated work; wire `Provider` and `Property` values lazily (`properties_providers.md`, `best_practices_tasks.md`).
- Prohibit `afterEvaluate`; use providers, `pluginManager.withPlugin`, and lazy APIs. Permit it only for a documented correctness-critical ordering constraint, with an `afterEvaluate-justification:` comment (`best_practices_general.md`).
- Operation/execution (running builds, running tests, diagnosing failures, and read-only dependency inspection/update discovery) belongs to `using-gradle`; authoring/modifying build definitions (including dependency declarations and version catalogs) belongs to `authoring-gradle-builds`. Trivial one-line everyday dependency edits (catalog entry + declaration + version bump) are a sanctioned overlap in `using-gradle`; anything structural (plugins, repositories, modules, toolchains, publishing, CI) is `authoring-gradle-builds` only.

## Decision Routing

| Authoring action | Reference |
|---|---|
| Add dependency, catalog, repository, or plugin dependency | [Dependencies and Catalogs](references/dependencies-and-catalogs.md) |
| Create module, subproject, settings, or project-isolation wiring | [Modules and Settings](references/modules-and-settings.md) |
| Create or refactor a convention plugin/build logic | [Convention Plugins](references/convention-plugins.md) |
| Create a custom task or task inputs/outputs | [Custom Tasks](references/custom-tasks.md) |
| Use service injection, build services, value sources, or advanced isolation | [Advanced Configuration](references/advanced-configuration.md) |
| Configure a JDK toolchain or resolver | [JDK Toolchains](references/jdk-toolchains.md) |
| Configure Kotlin compiler options | [Kotlin Compiler Options](references/kotlin-compiler-options.md) |
| Configure test frameworks or test behavior | [Testing Configuration](references/testing-configuration.md) |
| Publish artifacts or configure Central Portal delivery | [Artifact Publishing](references/artifact-publishing.md) |
| Wire CI/CD builds | [CI/CD Builds](references/ci-cd-builds.md) |
| Enable or update dependency locking | [Dependency Locking](references/dependency-locking.md) |
| Configure build scans | [Build Scans](references/build-scans.md) |
| Parallelize task work with Worker API | [Worker API](references/worker-api.md) |
| Configure continuous builds | [Continuous Builds](references/continuous-builds.md) |
| Understand build lifecycle, phases, task graph, or hook ordering | [Build Lifecycle](references/build-lifecycle.md) |
| Author Kotlin DSL scripts, accessors, or receivers | [Kotlin DSL](references/kotlin-dsl.md) |
| Model Property/Provider values, managed collections, or lazy files | [Managed Types and Providers](references/managed-types-and-providers.md) |
| Develop a binary plugin, test with TestKit, or publish a plugin | [Plugin Development](references/plugin-development.md) |
| Configure Java source sets, annotation processing, or mixed languages | [Java Builds](references/java-builds.md) |
| Model configurations, feature variants, capabilities, or variant sharing | [Configurations and Variants](references/configurations-and-variants.md) |

## Cross-Skill Handoffs

- Build execution, task running, test running, failure diagnosis, or read-only dependency inspection -> `using-gradle`.
- Enabling/persisting the build cache or configuration cache (gradle.properties/CLI flags, local/remote cache config, CI rollout, cache cleanup) and reading runtime cache/isolation outcomes -> `using-gradle`. This skill authors cacheability and config-cache-safe logic; it does not own enablement.
- Enabling isolated-projects flags/diagnostics and interpreting diagnostics output -> `using-gradle`.
- Runtime probing or arbitrary JVM/Kotlin execution -> `interacting-with-project-runtime`.
- Compose UI rendering or verification -> `verifying-compose-ui`.

## Workflows

### Create Module

1. Read the wrapper version, settings, project layout, catalogs, and applied conventions.
2. Load `modules-and-settings.md` as the single authoritative procedural reference; add the project and its build logic without root-wide mutation.
3. Use existing convention plugins and version aliases; add only module-specific configuration.
4. Hand off to `using-gradle` to verify project discovery and the module's lifecycle tasks.

### Add Dependency

1. Determine whether the change is structural; hand off read-only GAV discovery to `using-gradle`.
2. Load `dependencies-and-catalogs.md` as the single authoritative procedural reference; update the catalog when one exists and declare the alias in the consuming project.
3. Centralize repositories in settings and apply content filters when multiple repositories are required.
4. Hand off to `using-gradle` to verify dependency resolution and the affected configuration.

### Performance Audit

1. Read the wrapper version and use the narrowest authored reference as the single authoritative procedural reference for each audit action.
2. Optionally consult `references/best-practices/_index.md` and its generated detail for rationale; this is not a competing procedural load.
3. Inspect build logic for eager task APIs, provider realization, configuration-phase resolution, cross-project mutation, and configuration-cache violations.
4. Apply the smallest lazy, decoupled change; use `build-scans.md` only when publication is intentional.
5. Hand off to `using-gradle` to run the relevant verification and inspect task outcomes or configuration-cache diagnostics.

## Best-Practices Consultation

Use the authored reference selected by Decision Routing as the single authoritative procedural load for each authoring action. Consult `references/best-practices/_index.md` and its generated corpus detail only when rationale is needed or the authored reference points there; then query `gradle_docs` when deeper rationale or the authoritative version-scoped source is required. The escalation path remains `Index -> Detail -> Gradle Docs`, but it does not force a second competing procedural load. The corpus is frozen: route to it, do not edit it or restate its detail in this hub.
