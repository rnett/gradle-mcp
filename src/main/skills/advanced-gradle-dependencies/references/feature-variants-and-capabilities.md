# Feature Variants and Capabilities

Model and diagnose feature variants, configuration roles, and capability conflict resolution. This is the advanced-authoring layer of variant work: it goes beyond the declarative basics in `authoring-gradle-builds`'s `configurations-and-variants.md` into conflict resolution and governance. Diagnose the resolution failure first, then author the smallest capability or variant change and re-diagnose.

Read `gradle/wrapper/gradle-wrapper.properties` before version-sensitive advice; feature-variant and capability behavior change across Gradle versions.

## Feature Variants

A feature variant adds an optional set of functionality to a project (for example, a "cloud" or "local" implementation) without creating a separate project. Register features with `registerFeature`, which creates the source sets and configurations for the feature:

```kotlin
java {
    registerFeature("cloud") {
        usingSourceSet(sourceSets["cloud"])
    }
}
```

A consumer opts into a feature by requesting its capability, so feature variants participate in capability-based selection rather than attribute matching by default. The advanced question is usually not whether a feature exists but how consumers select it and how its capability interacts with others.

## Configuration Roles

Every configuration has a role that shapes how feature variants and capabilities behave:

- **Declarable (Bucket):** declares dependencies (e.g. `implementation`, `api`); not resolved itself.
- **Resolvable:** resolves a classpath for a task or runtime; cannot declare dependencies directly.
- **Consumable:** exposes artifacts to other projects or the external world (e.g. `apiElements`, `runtimeElements`).

A feature variant wires new configurations across all three roles (feature implementation, feature runtime elements for consumers, etc.). Mixing roles — for example a resolvable configuration used as a declarable one — leads to resolution failures and breaks the Configuration Cache. Lock the role explicitly with `isCanBeResolved`/`isCanBeConsumed` when registering custom configurations.

### Creating Configurations: Lazy vs Eager and the Role APIs

Prefer the lazy role APIs — `resolvable()` / `consumable()` (or `register` over `create`) — which realize configurations only on demand; eager `create` realization hurts configuration-time performance.

Lock roles explicitly with `isCanBeResolved` / `isCanBeConsumed` / `isCanBeDeclared`; use `extendsFrom` to inherit dependency sets between configurations without duplication.

Plugin families add their own configurations (Java `implementation`/`api`; AGP variant-aware `debugImplementation`; KMP source-set-scoped `commonMainImplementation`) — never assume Java names on non-Java builds. Deprecated configurations linger for compatibility.

Resolving a DIFFERENT project's configuration, or resolving at configuration time, is an unsafe access that breaks the Configuration Cache — fix it with a proper variant-aware cross-project dependency.

The standard declarable set feeds the resolvable classpaths: `api`/`implementation`/`compileOnly`/`compileOnlyApi`/`runtimeOnly` (+ `test*` forms) populate `compileClasspath`/`runtimeClasspath` and are exposed via `apiElements`/`runtimeElements`. `implementation` hides a dependency from consumers, `api` exposes it — the diagnostic question behind "why is/isn't X on my classpath". Keep custom configurations role-locked.

## Capabilities and Conflict Resolution

A capability identifies a specific piece of functionality. Capabilities serve two purposes that are easy to confuse:

- **Mutual exclusion:** a module might provide one of several incompatible implementations (e.g. different logging backends), each declaring the capability, so a consumer selecting one must not also select the other.
- **Alternate implementation:** a module declares that it stands in for another (the classic example is a lightweight implementation declaring the capability of the full API), so a consumer requesting the full capability can be satisfied by the replacement.

A **capability conflict** occurs when resolution selects more than one module declaring the same capability, or when two capabilities that must be exclusive are both selected. When this happens:

1. Diagnose with `dependencyInsight --all-variants` to see which modules carry the conflicting capability and why each was pulled in.
2. Identify the requested capability and the candidates that provide it.
3. Decide the intended winner: which producer should provide that capability for this configuration.
4. Resolve with the smallest correct lever — a capability declaration correction, an alignment/platform choice, or removing the second capability provider — and re-run the diagnostic to confirm only the intended provider wins.

**Anti-pattern:** authoring a capability or feature variant before diagnosing the actual conflict, or "fixing" a capability conflict by forcing a version without understanding which capability the consumers actually need.

**Capability coordinates are versioned `group:module:version` triplets.** Every component carries an implicit capability equal to its GAV coordinates, and the version participates in selection.

Declare capabilities to surface conflicts:
- For relocated modules or alternate implementations, via component metadata rules (`addCapability(...)`).
- On local consumable variants, via `outgoing { capability(...) }` — redeclare the implicit capability when adding others: once any explicit capability is declared, all capabilities (including the implicit one) must be listed.

Resolve capability conflicts programmatically with `resolutionStrategy.capabilitiesResolution.withCapability("g:a") { selectHighestVersion() }` or `select(candidate)` with `because(...)`. Selection is restricted to candidates actually present in the graph — you cannot select a module that is not part of the conflict.

## Diagnose-to-Fix Loop

1. Read the wrapper version; run `dependencyInsight --all-variants` for the conflicting dependency/configuration.
2. Classify the problem: feature-variant selection, a capability mutual-exclusion conflict, or an alternate-implementation ambiguity.
3. Author the smallest capability or feature change on the correct producer/consumer side.
4. Re-run the diagnostic to confirm the intended feature/capability now resolves and no conflict remains.

**More info:**
- Component capabilities: `gradle_docs(path="userguide/component_capabilities.md")`
- Feature variants: `gradle_docs(path="userguide/feature_variants.md")`
- Declaring configurations and roles: `gradle_docs(path="userguide/declaring_configurations.md")`
- Dependency configurations (the declarable set and what lands on each classpath): `gradle_docs(path="userguide/dependency_configurations.md")`
- Variant-aware resolution: `gradle_docs(path="userguide/variant_model.md")`
- Resolving conflicts via capabilities (alternate implementations): `gradle_docs(path="userguide/dependencies_intermediate.md")`
- Creating feature variants of a library: `gradle_docs(path="userguide/how_to_create_feature_variants_of_a_library.md")`
- Declarative feature-variant and capability basics: `authoring-gradle-builds`'s [Configurations and Variants](../authoring-gradle-builds/references/configurations-and-variants.md)
- Variant diagnostics: [Variant Resolution Diagnostics](variant-resolution-diagnostics.md); graph inspection via `inspect_dependencies` and `dependencyInsight`.
