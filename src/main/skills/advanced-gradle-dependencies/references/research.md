# Advanced Gradle Dependency Research

Authoritative workflow for researching advanced Gradle dependency resolution: official documentation, Gradle internals, the dependency source graph, and the resolution model that underlies governance and variant machinery.

Read `gradle/wrapper/gradle-wrapper.properties` before any version-sensitive research; resolution behavior, variant modeling, and governance modes all change across Gradle versions.

## Documentation & Research Workflow

**Default Path**: Official Docs $\rightarrow$ Source Code $\rightarrow$ Release Notes. Start with `gradle_docs(query="tag:userguide <term>")` for a precise stored search entry point.

**Anti-Pattern**: Reading source to understand a public resolution API (a rule DSL, a variant attribute, a metadata API) before checking `gradle_docs`. Use source only when docs do not pin down exact runtime behavior.

Because this skill spans **diagnosing** resolution and **authoring** the rules that change it, separate the two research intents before you start searching:

- **Diagnosing** (why a resolution is wrong): reproduce the failure, inspect the resolved graph, and trace the config that produced it. Start from [Variant Resolution Diagnostics](variant-resolution-diagnostics.md) and the diagnostic reports, then research the governing mechanic.
- **Authoring a rule** (metadata rule, substitution, lock mode, governance): pin down the exact DSL/API shape you must write, then verify its resolution consequence.

### Official Documentation (`gradle_docs`)

Use `gradle_docs` for authoritative guidance. Stored search links use `gradle_docs(query="tag:<tag> <term>")`; path reads and no-argument section browsing take no tag.

| Tag | Content | Use Case |
| :--- | :--- | :--- |
| `tag:userguide` | User Guide | High-level concepts, "How-to" guides (dependency resolution, variant selection). |
| `tag:dsl` | DSL Reference | Property/method syntax for Groovy and Kotlin DSL (rule DSLs, `resolutionStrategy`, metadata rules). |
| `tag:release-notes` | Release Notes | Breaking changes, new features per version (resolution behavior changes). |
| `tag:best-practices` | Best Practices | Performance tuning, architectural guidance. |
| `tag:javadoc` | JavaDoc | Low-level Gradle/API signatures (rule and metadata APIs). |
| `tag:samples` | Samples | Implementation patterns and examples. |

**Documentation Lookup Ladder**:

1. Search precisely with `gradle_docs(query="tag:<tag> <term>")`. For advanced dependencies, prefer `tag:userguide` for how resolution works and `tag:dsl` for the exact shape of a rule DSL (e.g. a `resolutionStrategy` or `componentMetadata` rule block). Example: `gradle_docs(query="tag:userguide variant selection")` or `gradle_docs(query="tag:dsl componentMetadata")`.
2. If that is too narrow, broaden the runtime search by dropping the tag: `gradle_docs(query="<term>")`.
3. Browse the documentation tree with `gradle_docs(path=".")`; a no-argument `gradle_docs()` call lists the available documentation sections.
4. Read the selected page with `gradle_docs(path="<clean .md path>")`.

**Version-Scoped Research**: Resolution uses an explicit `version` first, then wrapper auto-detection through `projectRoot` or `GRADLE_MCP_PROJECT_ROOT`, then the latest-stable fallback. Wrapper detection can fail without a usable project root or wrapper.
- **Recommended**: Normally omit `version`; specify `version="X.Y"` only when intentionally researching a target different from the wrapper, and state the migration or verification reason.
- **Anti-Pattern**: Use a coarse version such as `"8"`, or assume omission guarantees wrapper detection when no usable project root or wrapper is available.

---

## Source Code Exploration

### Gradle Build Tool Source (`gradleOwnSource: true`)

Use `gradleOwnSource: true` with `search_dependency_sources` and `read_dependency_sources` to inspect Gradle's own implementation of the resolution engine — variant matchers, metadata rule processing, lock and governance logic, and the configuration/attribute model.

- **Do**: Use `searchType: "DECLARATION"` to find class/method/rule-API definitions.
- **Do**: Use `read_dependency_sources(gradleOwnSource: true, path: "...")` to verify internal logic or the exact signature of a resolution API.
- **Anti-Pattern**: Using `gradleSource: true` (deprecated/incorrect parameter).

### Dependency & Plugin Sources

Researches the project's resolved dependency graph, plugins, and the JDK. This is where you inspect the metadata a component actually exposes (declared variants, attributes, capabilities) and third-party resolution/logic you depend on.

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
- **Plugins**: Use `sourceSetPath: ":buildscript"` to search plugin source (e.g. a plugin that wires variant or governance behavior).
- **JDK**: Use `dependency: "jdk"` to search Java standard library source.

**Critical Technical Warning**: Dependency sources are often mounted as junctions/symlinks. Standard CLI tools like `rg` or `fd` will NOT follow them by default; you MUST use the `--follow` flag if bypassing MCP tools.

#### Cache Management

- `fresh: true`: Use when dependencies have changed (e.g. after a version bump or a governance change).
- `forceDownload: true`: Use ONLY to recover from corrupted or missing source indices.

---

## Resolution Research: From Symptom to Rule

Advanced dependency work is diagnose-then-author. Research should reproduce the resolution symptom, trace it to a mechanic, then pin down the rule that changes it.

### Symptom $\rightarrow$ Diagnostic Ladder

1. **Read the wrapper and record the failing configuration.** Reproduce the failure with the authoritative diagnostic for the symptom: `dependencyInsight` (and `--all-variants`) for wrong winners, variant mismatches, and capability conflicts; `outgoingVariants` for the producer side; graph inspection for the resolved tree. See [Variant Resolution Diagnostics](variant-resolution-diagnostics.md).
2. **Identify the mechanic.** Classify the failure as a component metadata issue, a substitution/composite issue, a capability conflict, a variant-category/feature-variant issue, a locking/consistency issue, or a repository/governance issue. Route to the matching reference in the [Decision Routing](../SKILL.md#decision-routing) table.
3. **Research the golden path.** Use `gradle_docs(query="tag:userguide <mechanic>")` for the conceptual model (e.g. variant-aware matching, `componentMetadata` rules, consistent resolution) then `gradle_docs(query="tag:dsl <rule-block>")` for the exact DSL shape the minimal fix needs.
4. **Inspect the involved metadata.** When a component's declared variants or attributes are the question, read its source or metadata. Use dependency-source search scoped to the configuration that resolves it (`sourceSetPath: ":buildscript"` for plugins, `configurationPath: ":app:runtimeClasspath"` for runtime deps) with `DECLARATION` on the component's classes; this reveals what a third-party library actually exposes.

### Authoring a Rule: DSL + Source Verification

- Confirm the exact rule API with `tag:dsl` before writing it; resolution rule DSLs (component metadata, substitution, `resolutionStrategy`, `dependencyLocking`) are version-sensitive and failure is often silent.
- Verify the resolution consequence after authoring: re-run the diagnostic to confirm the winner changed as intended. Rules that do not change the outcome are evidence of a misidentified mechanic, not a successful fix.
- **Anti-Pattern**: Prescribing a rule without a diagnostic step, or changing the rule blindly when the underlying resolution behavior (cache, repository policy, or metadata source) is the actual cause.

---

## Version Notes

- **Gradle 7.x**: `resolutionStrategy` and component metadata/selection rules stable; consistent resolution (`shouldResolveConsistentlyWith`) available; dependency locking (`--write-locks`) stable in support.
- **Configuration cache**: Opt-in, not enabled by default. Since Gradle 9.0 it is the preferred execution mode, but governance/variant rule compatibility limitations remain; verify the build and treat incompatibilities as expected compatibility work, not necessarily project defects.
- **Wrapper version**: Read `gradle/wrapper/gradle-wrapper.properties` before compatibility advice; variant modeling, capability conflict behavior, and governance modes change across versions.

## More info

- Dependency resolution overview: `gradle_docs(path="userguide/dependency_resolution.md")`
- Graph resolution (conflict resolution, metadata retrieval): `gradle_docs(path="userguide/graph_resolution.md")`
- Viewing and debugging dependencies: `gradle_docs(path="userguide/viewing_debugging_dependencies.md")`
- Variant-aware matching: `gradle_docs(path="userguide/variant_model.md")`; `gradle_docs(path="userguide/variant_attributes.md")`
- Rich versions and constraints: `gradle_docs(path="userguide/rich_versions.md")`; `gradle_docs(path="userguide/dependency_constraints_conflicts.md")`
- Locking: `gradle_docs(path="userguide/dependency_locking.md")`
- Verification: `gradle_docs(path="userguide/dependency_verification.md")`
- Gradle source implementation: use `gradleOwnSource: true` with `search_dependency_sources` and `read_dependency_sources`.

## References

- [advanced-gradle-dependencies SKILL.md](../SKILL.md)
- [Variant Resolution Diagnostics](variant-resolution-diagnostics.md)
- [Component Metadata Rules](component-metadata-rules.md)
- [Substitution and Composites](substitution-and-composites.md)
- [Feature Variants and Capabilities](feature-variants-and-capabilities.md)
- [Dependency Locking Deep Dive](dependency-locking-deep-dive.md)
- [Advanced Version Catalogs](advanced-version-catalogs.md)
- [Repository Governance](repository-governance.md)
- [Resolution Mechanics](resolution-mechanics.md)
- Shared research mechanics: [using-gradle Research](../../using-gradle/references/research.md)
