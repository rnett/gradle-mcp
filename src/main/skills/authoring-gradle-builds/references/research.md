# Gradle Build Authoring Research

Authoritative workflow for researching Gradle build authoring: official documentation, Gradle internals, dependency and plugin sources, and the behavior of plugins you apply or develop.

Read `gradle/wrapper/gradle-wrapper.properties` before any version-sensitive research; authoring APIs, plugin application, and source scoping all change across Gradle versions.

## Documentation & Research Workflow

**Default Path**: Official Docs $\rightarrow$ Source Code $\rightarrow$ Release Notes. Start with `gradle_docs(query="tag:userguide <term>")` for a precise stored search entry point.

**Anti-Pattern**: Reading source to understand a public authoring API (a task type, extension, or plugin API) before checking `gradle_docs`. Use source only when docs do not pin down exact runtime behavior or the exact signature you must call.

Because authoring spans both **using** and **developing** plugins, separate the two research intents before you start searching:

- **Using a plugin** (applying a published or third-party plugin to a consumer build): pin down the `plugins {}`/`pluginManagement {}` declaration, then discover what the applied plugin contributes (tasks, extensions). See [Applied & External Plugins](#applied--external-plugin-research).
- **Developing a plugin** (writing logic others apply): research the Gradle plugin APIs, extension/task models, and TestKit/protocol conventions. See [Plugin Development Research](#plugin-development-research).

### Official Documentation (`gradle_docs`)

Use `gradle_docs` for authoritative guidance. Stored search links use `gradle_docs(query="tag:<tag> <term>")`; path reads and no-argument section browsing take no tag.

| Tag | Content | Use Case |
| :--- | :--- | :--- |
| `tag:userguide` | User Guide | High-level concepts, "How-to" guides (plugin application, configuration, lifecycle). |
| `tag:dsl` | DSL Reference | Property/method syntax for Groovy and Kotlin DSL (extensions, `plugins {}`, task configuration). |
| `tag:release-notes` | Release Notes | Breaking changes, new features per version. |
| `tag:best-practices` | Best Practices | Performance tuning, architectural guidance. |
| `tag:javadoc` | JavaDoc | Low-level Gradle/plugin API signatures. |
| `tag:samples` | Samples | Implementation patterns and examples. |

**Documentation Lookup Ladder**:

1. Search precisely with `gradle_docs(query="tag:<tag> <term>")`. For authoring, prefer `tag:userguide` for how-to topics (e.g. applying a plugin, configuration avoidance) and `tag:dsl` for exact DSL/property syntax (e.g. the shape of the `plugins {}` block, or an extension's property signatures). Example: `gradle_docs(query="tag:userguide plugins { }")` or `gradle_docs(query="tag:dsl plugins")`.
2. If that is too narrow, broaden the runtime search by dropping the tag: `gradle_docs(query="<term>")`.
3. Browse the documentation tree with `gradle_docs(path=".")`; a no-argument `gradle_docs()` call lists the available documentation sections.
4. Read the selected page with `gradle_docs(path="<clean .md path>")`.

**Version-Scoped Research**: Resolution uses an explicit `version` first, then wrapper auto-detection through `projectRoot` or `GRADLE_MCP_PROJECT_ROOT`, then the latest-stable fallback. Wrapper detection can fail without a usable project root or wrapper.
- **Recommended**: Normally omit `version`; specify `version="X.Y"` only when intentionally researching a target different from the wrapper, and state the migration or verification reason.
- **Anti-Pattern**: Use a coarse version such as `"8"`, or assume omission guarantees wrapper detection when no usable project root or wrapper is available.

---

## Source Code Exploration

### Gradle Build Tool Source (`gradleOwnSource: true`)

Use `gradleOwnSource: true` with `search_dependency_sources` and `read_dependency_sources` to inspect Gradle's own implementation of authoring APIs — task types, extension base classes, plugin framework internals, and configuration-model classes.

- **Do**: Use `searchType: "DECLARATION"` to find class/method/task-type definitions.
- **Do**: Use `read_dependency_sources(gradleOwnSource: true, path: "...")` to verify internal logic or the exact signature of an authoring API.
- **Anti-Pattern**: Using `gradleSource: true` (deprecated/incorrect parameter).

### Dependency & Plugin Sources

Researches the project's resolved dependency graph, plugins, and the JDK. This is where you inspect the implementation of a plugin you apply or develop.

#### Search Modes

- `DECLARATION`: Case-sensitive search for class, method, or interface definitions.
- `FULL_TEXT`: Case-insensitive exhaustive text search.
- `GLOB`: Case-insensitive file path search.

#### Scoping Parameters

| Parameter | Purpose | Note |
| :--- | :--- | :--- |
| `projectPath` | Target specific module | e.g. `:app` |
| `sourceSetPath` | Target specific source set | e.g. `:app:main` |
| `configurationPath` | Target a runtime configuration | e.g. `:app:runtimeClasspath` |

**Specialized Sources**:
- **Plugins**: Use `sourceSetPath: ":buildscript"` to search applied plugin source; the buildscript classpath carries the plugins you apply, so this is the authoritative scope for an applied plugin's implementation.
- **JDK**: Use `dependency: "jdk"` to search Java standard library source.

**Critical Technical Warning**: Dependency sources are often mounted as junctions/symlinks. Standard CLI tools like `rg` or `fd` will NOT follow them by default; you MUST use the `--follow` flag if bypassing MCP tools.

#### Cache Management

- `fresh: true`: Use when dependencies have changed (e.g. after a version bump or after changing an applied plugin version).
- `forceDownload: true`: Use ONLY to recover from corrupted or missing source indices.

---

## Applied & External Plugin Research

Researching **using** a plugin — a build that applies a published or third-party plugin — has a lookup ladder of its own, distinct from developing one. Do not infer a plugin's contribution from task names alone.

### Application & Resolution Lookup Ladder

1. **Pin the declaration.** Read the `plugins {}` block in the target build script and the `pluginManagement {}` block in `settings.gradle(.kts)`. For applied plugins, record the plugin ID, version, and the repository that resolves it. Verify the exact `plugins {}`/application syntax with `gradle_docs(query="tag:userguide plugins { }")` or `gradle_docs(path="userguide/plugins.md")`.
2. **Establish plugin resolution.** Determine whether the plugin resolves from the Plugin Portal, a `pluginManagement { repositories {} }` entry, an included build, or `buildSrc`. Read the fenced portal/resolution guidance: core plugins ship with Gradle; community plugins come through the Plugin Portal or a configured repository; custom/local plugins come from `buildSrc` or an included build. Confirm with `gradle_docs(path="userguide/plugins.md")` (see also [Plugin Development](plugin-development.md) for plugin ID governance).
3. **Discover what the applied plugin contributes.** Run `tasks` and `properties` for the target project; map contributed task groups, configurations, extensions, and convention properties back to the applied plugin. Use `tag:dsl` (`gradle_docs(query="tag:dsl <extension-name>")`) to resolve the shape of the plugin's extension and its task type signatures once applied.
4. **Investigate the applied plugin's implementation.** Use dependency-source search scoped to the buildscript classpath — `search_dependency_sources(sourceSetPath: ":buildscript", ...)` with `DECLARATION` for the plugin's `Plugin<T>` class, its extension interface, and its task types; `read_dependency_sources` to read them. This is the authoritative path for understanding an externally applied plugin's behavior when docs are thin. If the plugin is a local/custom one (included build or `buildSrc`), read the source directly from that build.

### Plugin Portal / Plugin Resolution

- The Plugin Portal is the default community plugin repository; a build can override or add repositories under `pluginManagement { repositories {} }` in settings.
- Plugin resolution is settings- and wrapper-dependent. Verify the current settings and wrapper before asserting where a plugin came from; plugin categories (core, community, custom/local) are stable across Gradle 7/8/9, but portal behavior and resolution order are configuration-specific.
- For plugin version research (what a plugin version does or a breaking change), prefer `gradle_docs(query="tag:release-notes <term>")` for Gradle-side changes and the plugin's own release notes/changelog for plugin-side changes.

**Anti-Patterns**: Assuming a plugin's extension or task names from the plugin ID alone; attributing a task to the wrong plugin without checking the buildscript classpath; or reading a plugin's source blindly before confirming the plugin actually resolves onto the buildscript classpath.

---

## Plugin Development Research

Researching **developing** a plugin — authoring logic that other builds apply — follows the general source-exploration path, targeting Gradle's plugin framework and the plugin's own API surface.

1. **Research the plugin framework APIs.** Use `gradleOwnSource: true` + `DECLARATION` for plugin interfaces (`Plugin<T>`), extension base classes, and task base types; read their exact signatures before authoring.
2. **Confirm extension and task model conventions.** Load [Extensions](extensions.md), [Custom Tasks](custom-tasks.md), and [Plugin Development](plugin-development.md); verify version-scoped rules (e.g. `compileOnlyApi` for `gradleApi()` on Gradle 9.4+) against the wrapper via `gradle_docs(path="userguide/upgrading_version_<N>.md")`.
3. **Verify against the consumer.** When a plugin must be applied by a consumer, reason about what the applied plugin will reveal at application time using the [Applied & External Plugin Research](#applied--external-plugin-research) ladder — extensions, tasks, and configurations are authored to be consumed that way.
4. **TestKit/protocol research.** For functional verification, reference [Plugin Development](plugin-development.md) and `gradle_docs(path="userguide/testing_gradle_plugins.md")`.

---

## Version Notes

- **Gradle 7.x**: Version catalogs experimental in 7.0; stable from 7.4. `plugins {}`/`pluginManagement {}` available and stable; precompiled script plugins in `buildSrc` supported.
- **Configuration cache**: Opt-in, not enabled by default. Since Gradle 9.0 it is the preferred execution mode, but plugin and feature compatibility limitations remain; verify the build and treat incompatibilities as expected compatibility work, not necessarily project defects. Authoring must stay config-cache-safe (see [Advanced Configuration](advanced-configuration.md), [Managed Types and Providers](managed-types-and-providers.md)).
- **Wrapper version**: Read `gradle/wrapper/gradle-wrapper.properties` before compatibility advice; authoring APIs (Kotlin DSL, `compilerOptions`, plugin application) change across versions.

## More info

- Plugin application: `gradle_docs(path="userguide/plugins.md")`; `gradle_docs(path="userguide/plugin_basics.md")`
- Plugin management/repositories: `gradle_docs(path="userguide/plugins.md")`; `gradle_docs(path="userguide/plugin_management.md")`
- Implementing plugins: `gradle_docs(path="userguide/implementing_gradle_plugins.md")`; `gradle_docs(path="userguide/java_gradle_plugin.md")`
- Testing plugins: `gradle_docs(path="userguide/testing_gradle_plugins.md")`
- Publishing plugins: `gradle_docs(path="userguide/publishing_gradle_plugins.md")`
- Gradle source implementation: use `gradleOwnSource: true` with `search_dependency_sources` and `read_dependency_sources`.
- Configuration & lifecycle: `gradle_docs(path="userguide/build_lifecycle_details.md")`; `gradle_docs(path="userguide/lazy_configuration.md")`; `gradle_docs(path="userguide/configuration_cache.md")`

## References

- [authoring-gradle-builds SKILL.md](../SKILL.md)
- [Modules and Settings](modules-and-settings.md)
- [Convention Plugins](convention-plugins.md)
- [Plugin Development](plugin-development.md)
- [Custom Tasks](custom-tasks.md)
- [Extensions](extensions.md)
- [Kotlin DSL](kotlin-dsl.md)
- [Advanced Configuration](advanced-configuration.md)
- [Managed Types and Providers](managed-types-and-providers.md)
- Shared research mechanics: [using-gradle Research](../../using-gradle/references/research.md)
