# Resolution Mechanics

Consolidates the resolution-engine mechanics that support governance authoring: dependency caching and freshness, resolution consistency, and performance/resolution-avoidance. This is not an authoring domain itself; it is the underlying behavior that determines what a lock mode, governance mode, or substitution buys you at resolution time. Read it when a dependency question is about why resolution behaves as it does or how to keep resolution fast and consistent.

Read `gradle/wrapper/gradle-wrapper.properties` before applying version-sensitive advice; cache defaults, resolution consistency, and resolution-avoidance behavior all change across Gradle versions.

## The Dependency Management Model at a Glance

Dependency management is a producer/consumer contract expressed through configurations. A configuration is a named bucket with a role — declarable, resolvable, or consumable — that determines where a dependency is used and how it is later resolved or exposed. Producers publish consumable variants; consumers resolve through resolvable configurations; declarations flow into classpaths.

Every resolution runs two phases — a **graph** phase (which versions, and one variant per component) and an **artifact** phase (which files map to each selected variant) — each with its own failure modes and diagnostics. When a dependency question is really an authoring question, the other references carry the levers, and this section is the connective map:

- **Centralization aids** (platforms/BOMs, version catalogs) shape *what gets declared*; a catalog's `libs.<alias>` accessors centralize declarations and keep coordinates in one place (see [Advanced Version Catalogs](advanced-version-catalogs.md)).
- **Conflict levers** (resolution rules, dependency locking, component metadata rules) shape *what wins* when declaration alone is not enough.

The `dependencies` task renders the resolved tree per configuration (see [Viewing and Debugging Dependencies](viewing_debugging_dependencies.md)).

## Declaring Dependencies: the Three Types and Their Resolution Implications

Gradle has exactly three dependency types, and which one you declared determines how far it participates in the resolution machinery:

- **Module dependency** — published coordinates (`group:name:version`) resolved from a repository with full metadata: transitives, variants, attributes, and capabilities. This is the only type where graph/variant/conflict resolution applies.
- **Project dependency** — resolves from another project in the same build via variant-aware matching against the producer's consumable configurations (`apiElements`, `runtimeElements`). It never goes through a repository; the producer is built before it is consumed.
- **File dependency** — raw files with no metadata: no transitives, no variants, and not published in your module descriptor. Declare producing tasks via `builtBy(...)` so they run; prefer `files(...)` over `fileTree(...)` — a `FileTree`'s order is not stable, which hurts cacheability.

Classify the dependency type before authoring a rule: version/variant/capability machinery only applies to module dependencies. Trying to apply a version-level rule to a file or project dependency is usually a sign of a wrong model.

### Declaration Notations that Change Resolution

- `classifier@ext` and `@aar` suffixes (`"g:a:1.0:classifier@zip"`, `"g:a:1.0@aar"`) force a specific artifact file, short-circuiting attribute-based artifact selection. The graph variant is still selected; only the artifact pick is bypassed.
- Prefer the single-string notation. The map notation (`group: 'g', name: 'a', version: '1'`) is deprecated and fails the build in Gradle 10; `--warning-mode=fail` surfaces it early.
- `because("...")` documents a dependency or constraint and surfaces in `dependencyInsight` as the "Was requested" selection reason — the fastest answer to "why is this here".
- Type-safe project accessors (`enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`, then `projects.utils`) replace error-prone path strings with compile-checked references.

### Gradle Distribution-specific Dependencies

`gradleApi()`, `gradleTestKit()`, and `localGroovy()` resolve to artifacts from Gradle's own distribution, pinned to the running Gradle version — not from declared repositories. Use them for plugin/task development, TestKit functional tests, and the bundled Groovy respectively. Because they bind to the distribution, they cannot be versioned, forced, or substituted like normal modules; they resolve outside the normal graph machinery.

### JVM Project Dependency Authoring, Diagnostically

`java-library` wires the standard roles: the `implementation`/`api`/`compileOnly`/`runtimeOnly`/`test*` declarable buckets land on `compileClasspath`/`runtimeClasspath` and are exposed to consumers via `apiElements`/`runtimeElements`. The `implementation` vs `api` decision is the consumer-visibility decision — it changes what downstream projects can reference, so flipping it changes downstream resolution. It is a frequent root cause of "missing on consumer classpath" diagnoses: an `api`-visible dependency declared as `implementation` disappears from the downstream compile classpath.

Repository lookup is order-dependent: Gradle checks each repository in order and stops at the first that contains the module. Publishing is declarative and sits outside the resolver lever set — you configure publication shapes, not resolution behavior.

## Caching and Freshness

Gradle caches dependency metadata and artifacts in the dependency cache (`GRADLE_USER_HOME`). Caching behavior varies by module type:

- **Fixed versions:** metadata and artifacts are cached as immutable; Gradle does not periodically recheck them.
- **Dynamic versions** (`1.+`, `latest.release`) and **changing modules** (`-SNAPSHOT`, or modules declared changing): metadata is rechecked after a cache TTL. The documented default TTL is 24 hours unless the build overrides it.

`--refresh-dependencies` asks configured repositories to recheck metadata, overriding the TTL for that invocation. It does **not** rerun every task and does not make a changing module reproducible. Distinguish the dependency cache from the task cache and the configuration cache: refreshing or cleaning the wrong one does not fix the symptom.

**Anti-pattern:** rerunning the same command and assuming remote metadata was rechecked immediately, or treating the dependency cache as the task cache.

The dependency cache is really two stores under `GRADLE_USER_HOME`:
- A **binary metadata cache** records resolved metadata — dynamic-version results, module descriptors, artifact pointers, and recorded *absences* — keyed per repository with a timestamp for expiry.
- A **checksum-keyed artifact file store** holds downloaded files by content hash, so different repositories serving the same coordinates never clobber each other's copy.

Metadata caches are per-repository, and artifacts are sticky to the repository that first supplied them: a module resolved from a specific repository is not silently re-resolved from another, and the build fails if it is missing there. Checksum-keyed storage is what lets repositories coexist without overwriting identical coordinates.

Tune TTLs programmatically rather than relying on flags alone:
- `resolutionStrategy.cacheDynamicVersionsFor(...)` — how long a resolved dynamic version is cached.
- `resolutionStrategy.cacheChangingModulesFor(...)` — the metadata/artifact TTL for changing modules.
- `--offline` resolves from cache only and never touches the network (failing if a module is absent).

For ephemeral CI caches: copy/share `$GRADLE_USER_HOME/caches/modules-<gradle-version>` (drop the `*.lock` / `gc.properties` files) or point multiple builds at a shared read-only cache, and keep the producing and consuming Gradle versions compatible.

## Resolution Consistency

A resolved graph is time-, repository-, and cache-dependent. Consistency levers control how reproducible resolution is and how it reacts to change:

- **Locking** makes the default mode resolve the locked versions for the locked configurations (see [Dependency Locking Deep Dive](dependency-locking-deep-dive.md)).
- **Repository policy and content filters** make provenance deterministic (see [Repository Governance](repository-governance.md)).
- **Exact, non-dynamic declarations** remove reliance on repository metadata state at resolution time.

Because a direct declaration is not necessarily the selected runtime version — platforms, strict constraints, capabilities, component metadata rules, dependency substitution, and repository content can all change the winner — confirm consistency by inspecting the resolved graph rather than inferring it from declarations.

**Anti-pattern:** assuming a catalog/declaration change alone guarantees the resolved outcome, or diagnosing a stale graph without checking the cache and repository policy that produced it.

### Consistent Resolution Across Configurations

Independently-resolved configurations can quietly select different versions of the same module — the classic compile/runtime drift where `compileClasspath` picks one version while `runtimeClasspath`, influenced by extra transitives, picks another. This is cross-configuration alignment, distinct from the reproducibility levers above:

- Align explicitly: `runtimeClasspath.shouldResolveConsistentlyWith(compileClasspath)`. If versions cannot be reconciled, the build fails instead of silently drifting.
- For Java projects, `java { consistentResolution { useCompileClasspathVersions() } }` applies the alignment across source sets (incubating).

Treat locking/repository policy as "pin the answer"; treat consistent resolution as "one version across configurations". Mixing them is fine; conflating them is a diagnosis error.

## Performance and Resolution Avoidance

Resolution is expensive; the fast path is avoiding it. Resolution-avoidance principles:

- **Model dependencies lazily.** Declare and resolve configurations only through task inputs or execution-time work; never resolve configurations (or iterate them) during the configuration phase.
- **Keep expensive work out of configuration.** Unselected tasks still pay configuration-time and resolution-time costs.
- **Avoid configuration-phase resolution.** Resolution during configuration realizes values early, breaking laziness, the configuration cache, and project isolation — and it is often the root cause of a build that resolves more than it should.

When resolution cost is a problem, diagnose the actual resolution footprint first (which configurations resolve and when), then apply the narrowest lazy/deferred change. Do not treat a fast build as proof of correctness without confirming the resolved graph matches intent.

**Anti-pattern:** resolving a configuration at configuration time to "make it work", or chasing resolution performance without first measuring which configurations resolve and when.

## The Resolution Flow: Graph Phase and Artifact Phase

Resolution is two-phase, and the phases answer different questions. Graph resolution builds the dependency graph and picks a variant per component; artifact resolution then maps each selected variant's node to concrete files.

### Graph Phase (Version and Capability Selection)

The graph phase runs node-by-node over the requested variant (the root is the resolvable configuration). Per node Gradle evaluates its dependencies, resolves version conflicts, downloads metadata for the involved components, then selects a variant per component before enqueueing those variants. Two conflict kinds surface here:

- **Version conflicts:** multiple requests for the same module with different versions are reconciled by the version-selection rules (highest, constraints, strict/rich versions, selection rules). Diagnose with `dependencyInsight`.
- **Capability conflicts:** two components declaring the same capability are mutually exclusive; only one wins. This is where platforms, `strictly`, and component metadata rules bite.

Variant selection happens inside the graph phase: after metadata is retrieved, the engine matches consumer attributes against each component's variants. Metadata retrieval downloads all needed component metadata at once, so repeated resolution is dominated by cache and repository state, not per-dependency round trips.

Each resolvable configuration computes its own independent dependency graph — which is why `compileClasspath` and `runtimeClasspath` legitimately differ for the same declarations and the same project. The `dependencies` task partially visualizes that graph, per configuration; it is a snapshot for inspection, not the live engine, and it does not show the variant selected for each node.

### Artifact Phase (Artifact Selection and Views)

Once the graph is fixed, the artifact phase maps each graph node's selected variant to an artifact set. Gradle performs attribute matching over the artifact sets a variant exposes; if no set matches, it attempts to build an artifact-transform chain to satisfy the request. Two tools shape this phase:

- **`ArtifactView`** re-runs selection over the resolved graph with *different* attributes. Use `withVariantReselection()` to pull parallel variants (e.g. sources/javadoc) from the same component, `lenient(true)` to resolve even when a module or artifact is missing, and `componentFilter` to include or exclude specific components (e.g. only project or only external modules) from the result.
- **Artifact transforms** convert an artifact from one format to another when no published variant matches. Register them with `registerTransform` declaring `from`/`to` attributes (e.g. `jar` -> `classes`); Gradle automatically assembles and runs a chain of registered transforms before the artifact becomes a task input. They apply only when no matching artifact set exists - a directly available variant wins over a transform.

The graph phase decides *what* (which versions, which variants); the artifact phase decides *which files* (which artifact sets, or how to synthesize them). Debug a missing/extra file in the artifact phase; debug a wrong winner in the graph phase.

Consumers read artifact results through `incoming` on the resolvable configuration: it exposes `FileCollection` (a flat file list), `ArtifactCollection`, and `ConfigurableFileCollection` for task inputs. The older `ResolvedConfiguration`/`LenientConfiguration` APIs (and their `ResolvedArtifact`/`ResolvedDependency` views) are in maintenance mode — legacy, and discouraged for new development.

A transform is an abstract `TransformAction` with an `@get:InputArtifact` input and a `transform(TransformOutputs)` method; optional `TransformParameters` carry configuration. Register it via `dependencies.registerTransform(...) { from.attribute(...); to.attribute(...) }`, declaring the output attributes in `to`. Transforms run during resolution, before tasks, and are cached like tasks (`UP-TO-DATE`); `@Incremental` transforms process only the changed files. Reiterate the selection rule: a directly matching artifact set always wins over running a transform chain.

#### Resolving a Specific Artifact

Appending `@<extension>` to a module coordinate (`"g:a:1.0@zip"`) resolves exactly that artifact file, bypassing metadata, variant, capability, and transitive handling; it only works for module dependencies. The classifier form `"g:a:1.0:classifier@ext"` picks one published artifact. Pair it with `metadataSources { artifact() }` for repositories serving bare files without metadata. This is declarative and per-dependency; it complements, rather than replaces, `ArtifactView`, which is programmatic and re-selects over an already-resolved graph.

## Metadata Formats and Sources

Metadata determines transitive dependencies, so *which* metadata source served a node changes the graph. Three formats exist:

- **Gradle Module Metadata** (`.module`) — carries the full Gradle dependency model (variants, attributes, capabilities); the preferred format.
- **Maven POM** (`pom.xml`) — the Maven dependency model.
- **Ivy descriptor** (`ivy.xml`) — the Ivy dependency model.

Search precedence per repository type: `.module` first, then POM / `ivy.xml`, then the bare artifact. **GMM redirection:** a POM or Ivy descriptor carrying the redirection marker makes Gradle prefer the paired `.module`; a repository can opt out via `metadataSources { ... ignoreGradleMetadataRedirection() }`.

Customize the search with `metadataSources { gradleMetadata(); mavenPom(); ivyDescriptor(); artifact() }`. Adding `artifact()` resolves coordinates that have no metadata file at all — the standard fix for repositories serving bare JARs.

Resolution consequence: when the graph surprises you, check which metadata source served the node — `dependencyInsight` is the tool.

**More info:**
- Dependency caching: `gradle_docs(path="userguide/dependency_caching.md")`
- Dynamic versions and changing modules: `gradle_docs(path="userguide/dynamic_versions.md")`
- Viewing and debugging dependencies: `gradle_docs(path="userguide/viewing_debugging_dependencies.md")`
- Lazy configuration and resolution best practices: `gradle_docs(path="userguide/best_practices_tasks.md")`
- Dependency resolution overview (graph and artifact phases): `gradle_docs(path="userguide/dependency_resolution.md")`
- Graph resolution (graph construction, conflict resolution, metadata retrieval): `gradle_docs(path="userguide/graph_resolution.md")`
- Artifact resolution and selection: `gradle_docs(path="userguide/artifact_resolution.md")`
- Artifact views (variant reselection, lenient selection, component filters): `gradle_docs(path="userguide/artifact_views.md")`
- Artifact transforms: `gradle_docs(path="userguide/artifact_transforms.md")`
- Consistent resolution across configurations: `gradle_docs(path="userguide/dependency_resolution_consistency.md")`
- Supported metadata formats and sources (GMM, POM, Ivy): `gradle_docs(path="userguide/supported_metadata_formats.md")`
- Dependency management overview (getting started): `gradle_docs(path="userguide/getting_started_dep_man.md")`
- Dependency management basics: `gradle_docs(path="userguide/dependency_management_basics.md")`
- Dependency management in Java projects: `gradle_docs(path="userguide/dependency_management_for_java_projects.md")`
- Resolving specific artifacts (`@extension` notation): `gradle_docs(path="userguide/resolving_specific_artifacts.md")`
- How to resolve specific artifacts: `gradle_docs(path="userguide/how_to_resolve_specific_artifacts.md")`
- Declaring dependencies (module/project/file types): `gradle_docs(path="userguide/declaring_dependencies.md")`
- Declaring dependencies basics and resolution-affecting notations: `gradle_docs(path="userguide/declaring_dependencies_basics.md")`
- Gradle distribution-specific dependencies: `gradle_docs(path="userguide/gradle_dependencies.md")`
- Cache TTL vs `--refresh-dependencies` execution guidance: `using-gradle`'s [Dependencies](../using-gradle/references/dependencies.md)
- Graph inspection: `inspect_dependencies`; `dependencyInsight` via the `gradle` tool.
