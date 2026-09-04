# Advanced Version Catalogs

Covers version catalog topics beyond the everyday entries and library declarations that stay in `authoring-gradle-builds`. Use this when catalog work involves plugins, bundles, multiple catalogs, catalog composition, or catalog accessors at a depth the basics do not reach.

Read `gradle/wrapper/gradle-wrapper.properties` before version-sensitive advice; catalog schema and accessor behavior change across Gradle versions.

## Catalog Sections and Their Contracts

The `gradle/libs.versions.toml` catalog has four sections with distinct contracts:

- **`[versions]`**: reusable version constants; use `version.ref` when several modules must move together.
- **`[libraries]`**: module coordinates; prefer `module = "group:name"` with `version.ref`, or a direct `version` only when intentionally independent.
- **`[bundles]`**: groups of aliases normally consumed together; do not use bundles to hide unrelated or optional dependencies.
- **`[plugins]`**: plugin IDs and versions for `alias(libs.plugins.<alias>)`.

Advanced catalog work is about getting these sections right at scale and across ownership boundaries, not about the one-off entry. Misusing a section (putting a full GAV in every consuming build script, or bundling unrelated dependencies) is an anti-pattern to correct.

## Bundles and Plugins

- **Bundles** reduce repetition when a set of libraries is always declared together. Keep bundles cohesive: a bundle should name a real unit of functionality, not a grab-bag. Coordinate bundle contents with the modules they wrap so a bundle membership change regenerates accessors predictably.
- **Plugins** are declared in the `[plugins]` section and applied through `alias(...)`. Centralize plugin versions in the catalog, but apply plugins via the declarative `plugins {}` block rather than scattering versions.

Kebab-case aliases become dot-separated accessors (e.g. `kotlin-stdlib` -> `libs.kotlin.stdlib`). Alias changes regenerate accessors and touch every consumer; keep aliases descriptive, kebab-case, and stable.

## Multiple Catalogs and Composition

Add additional catalogs only when dependency sets have separate ownership, repository scope, or release cadence. Register them in settings and consume through their generated accessors. Catalog composition (declaring one catalog in terms of another) is version-scoped; verify the available composition support against the wrapper before relying on it.

Import a root catalog into an included `build-logic` build explicitly, because an included build does not automatically receive the root build's catalog. Keep build-logic dependencies aligned with the root catalog only when they share the same compatibility contract.

## Catalog vs Platform: Centralization Trade-offs

Two centralization techniques solve different problems. A catalog centralizes coordinates for authoring: it declares requested versions that behave like any locally declared dependency and enforce nothing. A platform is a graph component that applies or enforces versions, propagates transitively, and participates in resolution directly.

Because catalog versions are requested, not enforced, Gradle may select a different version when a platform or strict constraint changes the winner. Only `enforcedPlatform` (or a strict constraint) overrides a version a catalog picked. The robust combination is a catalog that defines coordinates plus a `java-platform` module carrying constraints, consumed with `platform(project(":platform"))` alongside the catalog accessors.

**Diagnostic:** when the version that actually resolves differs from what the catalog declared, a platform or strict constraint changed the winner — inspect with `dependencyInsight` rather than editing the catalog.

## Platform Mechanics and Enforcement

`platform(...)` marks the dependency as a platform component (sets `org.gradle.category` to platform) so Gradle selects the platform. It also endorses strict versions by default; `doNotEndorseStrictVersions` disables that. A regular `platform("g:bom:ver")` treats the BOM's entries as constraints — recommendations that apply only if the module is present. `enforcedPlatform(...)` forcibly overrides all versions and is transitive to consumers, so for reusable components prefer `strictly` rich versions instead.

The `java-platform` plugin builds platforms:

- **Exclusive:** a platform cannot be combined with `java` or `java-library` in the same project.
- **Constraints, not dependencies:** declare versions in `constraints { api(...); runtime(...) }`. Adding a bare dependency fails unless you opt in with `javaPlatform { allowDependencies() }`.
- **Compose platforms:** import another BOM as an `api(platform(...))` dependency (needs `allowDependencies()`); constrain local projects with `api(project(":x"))`; share versions with subprojects via `api(platform(project(":platform")))`.
- **Publish:** apply `maven-publish` and use `from(components["javaPlatform"])` to generate a BOM; consume it with `platform` or `enforcedPlatform`.

## Catalog Accessor Depth

Generated type-safe accessors drive consumption. The advanced questions are about accessor collisions (two aliases generating the same accessor), naming stability (rename cost propagates to consumers), and choosing between a catalog accessor and a hard-coded coordinate. Keep aliases unambiguous enough that generated accessor names remain obvious and collision-free.

**Anti-pattern:** creating an alias whose kebab-case name generates an ambiguous or colliding accessor, or duplicating the same version across multiple catalogs rather than composing.

### Catalog Entry Depth: Classifiers, Artifacts, and Variants

Catalogs capture only `group`/`name`/`version` — no classifier, artifact type, exclude, or capability in TOML. Extend at the consumption site instead: `variantOf(libs.<alias>) { classifier("test-fixtures") }`, or the `artifact { name = ...; type = "aar" }` block for typed artifacts (common for Android `@aar`).

Authoring constraints: aliases must stay collision-free. Reserved keywords (`versions`, `bundles`, `plugins`, `extensions`, `class`) cannot be used as accessor names, and subgroup accessors can collide when a single-segment alias shares a name with a subgroup.

Import/publish depth: `from(...)` imports a catalog (from a file or one published via the `version-catalog` plugin) and allows overwriting versions. Prefer passing the provider type (`Provider<MinimalExternalModuleDependency>`) to compatible Gradle APIs rather than `.get()` — the unwrapped type is a `MinimalExternalModuleDependency`, which is not always the selector type those APIs expect.

### Catalog Failure Modes

Most catalog authoring failures are configuration-time errors, not resolution-time surprises:

- **Accessor clashes and reserved keywords** are the top authoring failure — rename aliases to be collision-free.
- **Undefined `version.ref` or bundle-alias references and malformed module/plugin/version notation** are configuration-time errors; check the alias against the `[versions]`/`[libraries]`/`[plugins]` sections.
- **Scale and imports:** split very large catalogs (the JVM format imposes a ~32,000-entry limit), keep each catalog to a single import invocation, and ensure imported files exist and the TOML format/version is supported by your Gradle wrapper.

**More info:**
- Version catalogs: `gradle_docs(path="userguide/version_catalogs.md")`
- Declaring dependencies: `gradle_docs(path="userguide/declaring_dependencies.md")`
- Combining catalogs and platforms: `gradle_docs(path="userguide/centralizing_catalog_platform.md")`
- Troubleshooting version catalog problems: `gradle_docs(path="userguide/how_to_fix_version_catalog_problems.md")`
- Centralizing dependency versions (catalogs and platforms overview): `gradle_docs(path="userguide/centralizing_dependencies.md")`
- Java platform plugin: `gradle_docs(path="userguide/java_platform_plugin.md")`
- Platforms (BOMs, enforced platforms): `gradle_docs(path="userguide/platforms.md")`
- Dependency declaration basics: `gradle_docs(path="userguide/declaring_dependencies_basics.md")`
- Catalog basics and consuming accessors: `authoring-gradle-builds`'s [Dependencies and Catalogs](../../authoring-gradle-builds/references/dependencies-and-catalogs.md)
- Coordinate discovery: `lookup_maven_versions`; graph inspection via `inspect_dependencies`.
