# Component Metadata Rules

Component metadata rules and dependency selection rules correct a resolution outcome when the published module metadata is missing, wrong, or otherwise inappropriate for your build. They are diagnose-first levers: inspect the resolved graph, identify the wrong outcome, confirm a rule is the correct correction, then author the smallest rule and re-diagnose.

Read `gradle/wrapper/gradle-wrapper.properties` before version-sensitive advice; rule evaluation, metadata caching, and the resolution model change across Gradle versions.

## Component Metadata Rules

A component metadata rule lets you modify the metadata of a component as it is resolved, without republishing it. Register rules in `dependencies` `components` blocks (or via `ComponentMetadataHandler`), scoped by module coordinate:

```kotlin
dependencies {
    components {
        withModule("com.example:some-lib") {
            // correct or augment this module's metadata
        }
    }
}
```

Common uses:

- **Declare a status** (`allVariants { withStatus(...) }`) or change existing status/status-scheme so change-reporting and conflict-resolution work as intended.
- **Declare missing attributes** so a consumer's attribute matching succeeds where metadata omitted them.
- **Replace a broken dependency or capability** on the component (for example, removing an incorrect capability that conflicts).
- **Correct the variant model** exposed by the component (must conform to the rules the resolve already enforces).

A rule runs during metadata resolution and only affects how the component is seen, never the published artifact. This makes rules the right tool when you cannot change the upstream publication but the build can correct for it locally.

**Anti-pattern:** using a component metadata rule to force behavior that belongs in the consuming build (see Selection Rules), or authoring a rule before confirming the wrong metadata is the actual cause with `dependencyInsight`.

**Cacheability:** author rules as a `ComponentMetadataRule` class annotated `@CacheableRule` so results are cached instead of re-run on every resolution. Rule instances are isolated; pass inputs via `params(...)` at registration (every parameter must be `Serializable` or a recognized Gradle type) and inject services such as `ObjectFactory` through the constructor with `@Inject`. A rule should always be cacheable to avoid slow builds.

**Modifiable-parts API:** this underpins most corrections. `allVariants` modifies every variant, `withVariant(name)` targets one variant, and `addVariant(name)` or `addVariant(name, base)` adds a new variant from scratch or copies from an existing base variant. Within a variant you can adjust `attributes`, `withCapabilities`, `withDependencies`, artifact choices, and status. This is the basis for making classified jars explicit, splitting out version-encoded variants, teaching Ivy modules their variants, and adding missing capabilities.

**Governance:** declare rules in settings so they apply to the whole build — `dependencyResolutionManagement { components { ... } }` — with `rulesMode` controlling precedence between settings and project rules (`PREFER_SETTINGS`, `FAIL_ON_PROJECT_RULES`, or the default `PREFER_PROJECT`).

**Status schemes:** rules can correct a wrong published status (or scheme) so that status drives change-reporting and conflict selection as intended. Default schemes map `integration`/`release`; a custom scheme can insert or rename status levels.

## Dependency Selection Rules

A dependency selection rule (`ComponentSelectionRule`) changes which version of a module is selected during conflict resolution. Register rules in the `resolutionStrategy` of the relevant configuration:

```kotlin
configurations.all {
    resolutionStrategy {
        // e.g. select only 2.x releases, or reject a known-bad version
    }
}
```

Common uses:

- **Select only stable releases** for a module (`reject` all non-release candidates).
- **Reject a specific known-bad version** without forcing a global version change.
- **Apply version selection policy** that the plain highest-version rule would not produce.

A selection rule runs during version selection, before the winner is fixed. It is narrower than `force` and more expressive than a strict constraint when the policy is "reject these candidates" rather than "pin exactly this version."

## Rich Versions and Version Declarations

A version string is richer than an exact pin. You can declare an exact version, a dynamic version (`latest.release` / `latest.integration`), a changing version (`-SNAPSHOT`, or a module marked changing), a Maven-style range with inclusive/exclusive bounds (`[1.0, 2.0[` — `[`/`]` inclusive, `(`/`)` exclusive), or a prefix range (`1.+`, meaning any version matching the prefix). Exact and range declarations are the reproducible default; dynamic and changing versions trade freshness for reproducibility and are cached for a configurable TTL (cross-reference `resolution-mechanics.md` for that cache behavior).

Rich versions combine several strength levels on one dependency via `version { ... }`. Strongest to weakest:

- **`strictly` (or `!!`)**: only these versions are accepted, can downgrade a declared dependency, overrides any `require` and clears any `reject`, and resolution fails if no acceptable version exists. Strict versions overwrite transitive versions, so prefer a range here (`strictly("[1.7, 1.8[")`).
- **`require`**: the minimum version; may be upgraded by conflict resolution even past an exclusive upper bound. This is the default for a direct dependency.
- **`prefer`**: the softest; used only when no stronger non-dynamic version is specified. Complements `strictly` or `require`.
- **`reject`**: excludes specific versions, causing failure if one would be selected. Sits outside the hierarchy.

`strictly` + `prefer` is the flexible-pinning pattern: the engine must stay within the strict range but prefers your version (`strictly("[1.7, 1.8[") prefer("1.7.25")`). You can also endorse a strict version explicitly; platforms endorse strict versions by default (see Version Alignment), which you can opt out of.

## Dependency Constraints

`constraints { ... }` (inside `dependencies`) sets version requirements for a module **without adding it as a dependency**; the constraint only applies when the coordinate already appears in the graph, directly or transitively. Use constraints for version governance and controlled transitive upgrades. Constraints are not strict by default (they usually mean "at least this version"); attach rich or strict versions and a `because(...)` rationale as needed.

Two properties matter in practice:

- **Transitive across the graph:** a constraint declared by a dependency applies downstream, so you can govern versions through a whole graph from one place.
- **Published only via Gradle Module Metadata:** constraints are preserved for consumers only through GMM; Maven and Ivy consumers do not see them. Version catalogs cannot declare constraints — centralizing constraints is the job of a platform (see Version Alignment).

## Version Conflict Governance: Prevent, Downgrade, Upgrade

Optimistic resolution upgrades to the highest requested version, so a transitive can silently drift forward. Choose a lever by its scope:

- **`force`** — configuration-wide override that pins a version against all requests (`resolutionStrategy.force("g:n:ver")`). Blunt; overrides constraints.
- **Rich versions / strict constraints** — declaration-scoped pinning (`strictly`), the narrowest prevention lever.
- **Constraints** — graph-wide, conditional on the coordinate being present; lets you upgrade a transitively-pulled version without declaring the module.
- **Selection rules** — reject/select policy on which candidates are eligible.

To make drift loud, `resolutionStrategy.failOnVersionConflict()` fails the build on any version conflict instead of silently upgrading. Dependency locking is the robust, reviewed option for reproducible graphs (cross-reference `dependency-locking-deep-dive.md`). To upgrade a transitively-pulled older version, add a `constraints { implementation("g:n:new") { because("...") } }` that overrides it without declaring the module.

To downgrade, use `version { strictly("older") }` — behaves like a force and fails if something requires higher — or `strictly("[1.9, 2.0[")` with `prefer("1.9")` for flexibility. A configuration-level `force("g:n:old")` overrides all constraints but is the coarsest tool. Before downgrading, verify with `dependencyInsight` that no platform or `enforcedPlatform` is the real pin; adding a rule on top of an enforced platform only masks the cause.

**Capability conflicts** (two components declaring the same capability fail the build) are a different conflict kind, settled with `resolutionStrategy.capabilitiesResolution.withCapability("g:n") { selectHighestVersion() }`. Cross-reference `feature-variants-and-capabilities.md` for the conceptual model.

## Version Alignment

Version misalignment happens when transitive resolution pulls different versions of modules from one logical group. Align the group so they move together:

- **Published platform:** `implementation(platform("g:bom:ver"))` imports a BOM that aligns the group.
- **Virtual platform (no published BOM):** a `ComponentMetadataRule` calls `belongsTo("g:virtual-platform:${id.version}")`, registered via `components.all(...)`, so all modules in the group align under one version.
- **Native route for co-versioned local modules:** a `java-platform` module declares constraints on the projects, consumed via `api(platform(project(":platform")))`.

## The Wider Resolution-Rules Toolbelt

Resolution-strategy rules inject directly into the resolution engine — brute force that can mask underlying issues such as new dependencies appearing. Prefer constraints and component metadata rules for libraries, and reach for these only when other levers are insufficient:

- **`resolutionStrategy.force(...)`** pins a version against all requests.
- **Module replacement:** `dependencies { modules { module("g:a") { replacedBy("g2:a2", "reason") } } }` declares a renamed module superior during conflict resolution. Distinct from capability handling.
- **Disabling transitives** per dependency (`transitive = false`) as a scope-limiting tool; configurations auto-inject their default dependencies when nothing is declared; `eachDependency` resolve rules implement custom versioning schemes or deny versions.

Substitution (see `substitution-and-composites.md`) and selection rules are covered separately; do not duplicate them here.

### Excluding Transitive Dependencies

Per-declaration `exclude(group = ..., module = ...)` drops a transitive from that dependency only. The key rule: an exclusion is only effective if **every** dependency that pulls the target transitively excludes it — a single non-excluding requirer restores it. Diagnose with the `dependencies` task, then add the exclude to each requirer. Watch module-name drift (for example, `commons-collections` vs `commons-collections4`) so the exclude matches the actual coordinate. Exclude deliberately as a last resort: prefer a constraint (adjust a version), a component metadata rule (remove a wrongly-declared dependency), or capabilities (resolve mutually-exclusive implementations) before reaching for `exclude`.

## Diagnose-to-Fix Loop

1. Read the wrapper version; confirm the resolved outcome is actually wrong via `inspect_dependencies` / `dependencyInsight`.
2. Determine the cause: broken published metadata (component metadata rule) vs wrong version selection (selection rule).
3. Author the smallest scoped rule on the correct handler (`components { ... }` for metadata; `resolutionStrategy` for selection).
4. Re-run the diagnostic to confirm the winner or variant model now matches intent.

Normal conflict resolution generally selects the highest requested version, but platform, strict constraint, capability, and component metadata rules can change the winner. Inspect the graph before forcing a result.

**Anti-pattern:** authoring a rule before diagnosing the actual cause, or widening a rule's module scope beyond the affected component.

**More info:**
- Resolution rules: `gradle_docs(path="userguide/resolution_rules.md")`
- Viewing and debugging dependencies: `gradle_docs(path="userguide/viewing_debugging_dependencies.md")`
- Component metadata rules: `gradle_docs(path="userguide/component_metadata_rules.md")`
- Dependency constraints and conflicts: `gradle_docs(path="userguide/dependency_constraints_conflicts.md")`
- Declaring versions: rich versions (`strictly`/`require`/`prefer`/`reject`), ranges, and dynamic versions: `gradle_docs(path="userguide/dependency_versions.md")`
- Preventing accidental dependency upgrades (`failOnVersionConflict`, strict constraints, locking): `gradle_docs(path="userguide/how_to_prevent_accidental_dependency_upgrades.md")`
- Downgrading transitive dependencies (strict versions or `force`): `gradle_docs(path="userguide/how_to_downgrade_transitive_dependencies.md")`
- Upgrading transitive dependencies (dependency constraints): `gradle_docs(path="userguide/how_to_upgrade_transitive_dependencies.md")`
- Enforcing and constraining versions, resolving conflicts via capabilities: `gradle_docs(path="userguide/dependencies_intermediate.md")`
- Aligning dependency versions (platforms or `belongsTo` virtual platforms): `gradle_docs(path="userguide/how_to_align_dependency_versions.md")`
- Dependency constraints: `gradle_docs(path="userguide/dependency_constraints.md")`
- Excluding transitive dependencies (and capability/metadata-rule alternatives): `gradle_docs(path="userguide/how_to_exclude_transitive_dependencies.md")`
- Graph and winner inspection: `inspect_dependencies`; `dependencyInsight` via the `gradle` tool.
