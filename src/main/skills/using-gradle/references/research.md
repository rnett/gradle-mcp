# Gradle & Dependency Research

Authoritative workflow for researching official documentation, Gradle internals, and the dependency source graph.

## Documentation & Research Workflow
**Default Path**: Official Docs $\rightarrow$ Source Code $\rightarrow$ Release Notes. Start with `gradle_docs(query="tag:userguide <term>")` for a precise stored search entry point.
**Anti-Pattern**: Reading source to understand a public API before checking `gradle_docs`. Use source only when docs do not pin down exact runtime behavior.

### Official Documentation (`gradle_docs`)
Use `gradle_docs` for authoritative guidance. Stored search links use `gradle_docs(query="tag:<tag> <term>")`; path reads and no-argument section browsing take no tag.

| Tag | Content | Use Case |
| :--- | :--- | :--- |
| `tag:userguide` | User Guide | High-level concepts, "How-to" guides. |
| `tag:dsl` | DSL Reference | Property/method syntax for Groovy and Kotlin DSL. |
| `tag:release-notes` | Release Notes | Breaking changes, new features per version. |
| `tag:best-practices` | Best Practices | Performance tuning, architectural guidance. |
| `tag:javadoc` | JavaDoc | Low-level Gradle API signatures. |
| `tag:samples` | Samples | Implementation patterns and examples. |

**Documentation Lookup Ladder**:
1. Search precisely with `gradle_docs(query="tag:<tag> <term>")`.
2. If that is too narrow, broaden the runtime search by dropping the tag: `gradle_docs(query="<term>")`.
3. Browse the documentation tree with `gradle_docs(path=".")`; a no-argument `gradle_docs()` call lists the available documentation sections.
4. Read the selected page with `gradle_docs(path="<clean .md path>")`.

**Version-Scoped Research**: Resolution uses an explicit `version` first, then wrapper auto-detection through `projectRoot` or `GRADLE_MCP_PROJECT_ROOT`, then the latest-stable fallback. Wrapper detection can fail without a usable project root or wrapper.
- **Recommended**: Normally omit `version`; specify `version="X.Y"` only when intentionally researching a target different from the wrapper, and state the migration or verification reason.
- **Anti-Pattern**: Use a coarse version such as `"8"`, or assume omission guarantees wrapper detection when no usable project root or wrapper is available.

---

## Source Code Exploration
### Gradle Build Tool Source (`gradleOwnSource: true`)
Use `gradleOwnSource: true` with `search_dependency_sources` and `read_dependency_sources` to inspect the Gradle tool's implementation.

- **Do**: Use `searchType: "DECLARATION"` to find class/method definitions.
- **Do**: Use `read_dependency_sources(gradleOwnSource: true, path: "...")` to verify internal logic.
- **Anti-Pattern**: Using `gradleSource: true` (deprecated/incorrect parameter).

### Dependency & Plugin Sources
Researches the project's resolved dependency graph, plugins, and the JDK.

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
- **Plugins**: Use `sourceSetPath: ":buildscript"` to search plugin source.
- **JDK**: Use `dependency: "jdk"` to search Java standard library source.

**Critical Technical Warning**: Dependency sources are often mounted as junctions/symlinks. Standard CLI tools like `rg` or `fd` will NOT follow them by default; you MUST use the `--follow` flag if bypassing MCP tools.

#### Cache Management
- `fresh: true`: Use when dependencies have changed (e.g. after a version bump).
- `forceDownload: true`: Use ONLY to recover from corrupted or missing source indices.

## Applied Plugin Inspection

Do not infer plugin origin from task names alone. Identify what is applied and what it contributes:

1. Run `plugins` and `buildEnvironment` for the target project/build logic; record plugin IDs, versions, and classpath dependencies.
2. Run `tasks` and `properties`; map contributed task groups, configurations, extensions, and convention properties to the applied plugin.
3. Classify the plugin as **core** (Gradle-provided), **community** (resolved through the Plugin Portal or another configured repository), or **custom/local** (included build, `buildSrc`, or repository-local implementation).
4. If the origin or behavior remains unclear, use dependency-source search with the project or `:buildscript` scope and inspect settings `pluginManagement` repositories and included builds.

Plugin categories are stable across Gradle 7/8/9, but portal and resolution behavior is settings- and wrapper-dependent. Verify the current settings and wrapper before asserting where a plugin came from.

## Version Notes

- **Gradle 7.x**: Version catalogs experimental in 7.0; stable from 7.4.
- **Configuration cache**: It is opt-in, not enabled by default. Since Gradle 9.0 it is the preferred execution mode, but plugin and feature compatibility limitations remain; verify the build and treat incompatibilities as expected compatibility work, not necessarily project defects.
- **Wrapper version**: Read `gradle/wrapper/gradle-wrapper.properties` before compatibility advice.

## More info

- Plugin basics: `gradle_docs(path="userguide/plugin_basics.md")`
- Gradle source implementation: use `gradleOwnSource: true` with `search_dependency_sources` and `read_dependency_sources`.
- Core topics: `gradle_docs(path="userguide/command_line_interface_basics.md")`; `gradle_docs(path="userguide/gradle_optimizations.md")`; `gradle_docs(path="userguide/java_testing.md")`
- Execution and troubleshooting: `gradle_docs(path="userguide/command_line_interface.md")`; `gradle_docs(path="userguide/controlling_task_execution.md")`; `gradle_docs(path="userguide/configuration_cache_debugging.md")`
- Dependencies: `gradle_docs(path="userguide/dependency_constraints_conflicts.md")`; `gradle_docs(path="userguide/dependency_caching.md")`; `gradle_docs(path="userguide/viewing_debugging_dependencies.md")`

## References
- [using-gradle SKILL.md](../SKILL.md)
- [Running Builds](running-builds.md)
- [Testing](testing.md)
- [Troubleshooting](troubleshooting.md)
- [Dependencies](dependencies.md)
- Build authoring research (plugin use and development): [authoring-gradle-builds Research](../../authoring-gradle-builds/references/research.md)
- Advanced dependency research (resolution mechanics, governance, variants): [advanced-gradle-dependencies Research](../../advanced-gradle-dependencies/references/research.md)
