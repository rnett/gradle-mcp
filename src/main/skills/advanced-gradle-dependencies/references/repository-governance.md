# Repository Governance

Govern how and where dependencies resolve by managing repository declaration, content filtering, and exclusive content. This is the governance layer beyond the basic repository-authoring in `authoring-gradle-builds`'s `dependencies-and-catalogs.md`: it owns `dependencyResolutionManagement` modes, content filtering, and `exclusiveContent`.

Read `gradle/wrapper/gradle-wrapper.properties` before version-sensitive advice; repository governance modes and exclusive-content handling change across Gradle versions.

## Declaring Repositories

The `repositories {}` DSL is the declaration surface, exposed through the `RepositoryHandler`: built-in shorthands for well-known public repositories (`mavenCentral()`, `google()`, `gradlePluginPortal()`), custom `maven {}`/`ivy {}` repositories declared by URL, and local declarations (`mavenLocal()`, `flatDir`).

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://repo.example.com/releases") }
    ivy  { url = uri("https://repo.example.com/ivy") }
}
```

**Declaration order is search order.** The first repository that yields a module's metadata supplies ALL of that module's artifacts — a later repository is not consulted for that module. Order is therefore policy: which repository "wins" a GAV is decided by position, unless content filters or exclusivity override it.

`mavenLocal()` and flat directories are prototyping-only: they bypass Gradle's dependency cache and hurt reproducibility (deeper caveats in [Repository Types and Layouts](#repository-types-and-layouts)). Keep them out of production repository sets.

Plugins resolve from a **separate** repository set: `pluginManagement.repositories` in `settings.gradle.kts` (default `gradlePluginPortal()`), distinct from project-dependency repositories. A plugin and a project dependency are fetched from different declarations.

Diagnostic lens: "which repository supplied this GAV" is answered by `dependencyInsight`; the winner is decided by declaration order combined with any content filters in effect.

## `dependencyResolutionManagement` Modes

`dependencyResolutionManagement` in `settings.gradle.kts` centralizes repository policy:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // ... repositories with content filters
    }
}
```

The `RepositoriesMode` governs whether project-level `repositories { }` blocks are allowed alongside the settings-level policy:

- **`PREFER_PROJECT`** (historical default): project repositories may be declared and are preferred over settings repositories.
- **`PREFER_SETTINGS`**: settings repositories take precedence; project repositories are allowed but not preferred.
- **`FAIL_ON_PROJECT_REPOS`**: project-level repository declarations fail the build, enforcing a single settings-level policy.

The governance question is which mode matches the build's provenance requirements. `FAIL_ON_PROJECT_REPOS` is the strongest enforcement when every project must resolve through the same declared repositories; it is also the mode that most directly prevents ad hoc project repositories from shadowing trusted content.

## Content Filtering

Content filters constrain which modules a repository may serve, fixing provenance when multiple repositories must coexist:

```kotlin
maven {
    name = "company"
    url = uri("https://repo.example.com/releases")
    content {
        includeGroupByRegex("com\\.example(\\..*)?")
    }
}
```

Filter semantics are exact: declaring an `include` means the repository serves ONLY what is included; declaring an `exclude` means it serves everything BUT that; combining both keeps only what is included and not excluded. There is no implicit membership — a filter is not a hint, it is a whitelist/blacklist over the repository's served set. Repository order still matters for metadata and artifact provenance even with filters, but filters remove most accidental shadowing.

Maven release/snapshot routing is a separate filter dimension. `mavenContent { releasesOnly() }` / `snapshotsOnly()` restricts a repository to one kind of artifact — the fix when a repository returns an unexpected snapshot, or a release-only repository is asked to serve a snapshot (and vice versa).

```kotlin
maven {
    url = uri("https://repo.example.com/releases")
    mavenContent { releasesOnly() }
}
```

**Anti-pattern:** an unfiltered private repository beside Maven Central, or a filter that omits groups the repository actually serves (which makes the filter a false guarantee).

## `exclusiveContent`

`exclusiveContent` is the strongest governance lever: it binds a content filter to a set of repositories so that only those repositories may serve the included groups/modules, forcing version conflicts to surface and preventing any other repository from providing them:

```kotlin
repositories {
    exclusiveContent {
        forRepository { maven { url = uri("https://repo.company.com") } }
        filter {
            includeGroup("com.company")
        }
    }
}
```

Use `exclusiveContent` when a module group must come from exactly one repository by policy (e.g. a private/internal group that must not be resolvable from a public mirror, or a group with strict provenance). It is stricter than a plain content filter because it both filters and excludes other repositories from serving the group.

**Anti-pattern:** relying on plain content filters to provide exclusivity, or assuming repository order gives exclusivity. Only `exclusiveContent` makes the group exclusive to the bound repository.

Note the interplay with plugin repositories: using `exclusiveContent` inside `pluginManagement` makes adding `buildscript.repositories` illegal — the build fails — because plugin-repository exclusivity forces a single settings-only declaration surface.

## Repository Types and Layouts

Repository types differ in how they store metadata and layout, which changes both what they can serve and how trustworthy their content is.

**Maven repositories have a strict limitation:** repositories declared inside POM/Ivy metadata are IGNORED — only build-declared repositories are used. This is a provenance guarantee: a dependency cannot silently pull in an unverified repository, so an updated dependency cannot unexpectedly introduce artifacts from an unverified source.

**The case against `mavenLocal()`:** it is a Maven cache, not a true repository. Origins cannot be verified, there is no dependency caching (so builds are slower), and artifacts can be silently overwritten — a reproducibility and security loss. When it is unavoidable, scope it to specific modules so it cannot shadow a real repository:

```kotlin
mavenLocal {
    content {
        includeGroup("com.example.myproject")
    }
}
```

**Ivy repositories** support named layouts and custom patterns:

- Named layouts: `layout("gradle")` (default), `layout("maven")`, `layout("ivy")`.
- Custom pattern layouts with `[organisation]/[module]/[revision]` tokens, and `m2compatible(true)` to translate Maven's dotted group paths into forward slashes for the organisation segment:

```kotlin
ivy {
    url = uri("https://repo.example.com/ivy")
    patternLayout {
        artifact("[organisation]/[module]/[revision]/[artifact]-[revision].[ext]")
        ivy("ivy-files/[organisation]/[module]/[revision]/ivy.xml")
        setM2compatible(true)
    }
}
```

- Ivy **dynamic resolve mode** (`resolve.isDynamicMode = true`) prefers `revConstraint` over `rev` when an `ivy.xml` descriptor provides it, falling back to `rev` otherwise — for repositories that define version constraints on artifacts.

**Flat directory repositories** synthesize ad-hoc metadata purely from artifact presence — the lowest-trust source. Real metadata (POM/Ivy/GMM) from any other declared repository takes precedence; a flat dir cannot override artifacts that have real metadata elsewhere. Prefer local Maven/Ivy URLs over `flatDir` where possible.

## Protocols and Authentication

Transport is encoded in the repository URL, and each transport maps to a credential type: `file` (no auth), `http(s)`/`sftp` (username/password), `s3://` (`AwsCredentials`), `gcs://` (default application credentials).

Authentication schemes are configured per repository: standard `credentials { username; password }`, HTTP header auth via `HttpHeaderCredentials` (name/value pair), and preemptive authentication where the server demands it before challenge.

- **S3:** static `AwsCredentials` (access key / secret key / optional session token) or IAM via `authentication { create<AwsImAuthentication>("awsIm") }`, which loads the role from the EC2 instance or environment.
- **GCS:** default application credentials from well-known files or environment variables — no explicit credential declaration.

Credential hygiene is a governance decision: never hardcode credentials in build files. Externalize them to `gradle.properties` or environment variables, use the property-prefixed lookups, and prefer conditional credential requirements (declare them only when a credential is actually required) so a build does not demand secrets it does not need.

Diagnostic angle: a resolution failure with auth symptoms (401/403 or connection-level credential errors) is a transport/credential problem — check the URL scheme and the configured credential type — before suspecting content filters or ordering.

## Dependency Authoring Best Practices

Repository governance also owns how dependencies are declared, because declaration shape affects how filterable and reducible the resolution is.

- **Version catalogs centralize DECLARED versions but do not pin RESOLVED ones.** A catalog is a hygiene lever, not a resolution lever: pair it with dependency locking, platforms, or version alignment for determinism.
- **Name catalog aliases for stable, unambiguous accessors:** kebab-case derived from the group/artifact, dropping TLDs (`com`, `org`, `net`, `dev`) and generic terms (`core`, `java`, `gradle`, `module`, `sdk`). A rename regenerates the accessors and touches every consumer, so choose names carefully.
- **Skip explicit Kotlin-stdlib declarations** (it arrives transitively with Kotlin tooling); avoid deliberately redundant declarations; prefer the single GAV string (`group:artifact:version`) — the map notation is deprecated.
- **Apply exclusions narrowly:** prefer capabilities or component metadata rules over broad `exclude`, and scope an `exclude` to the exact group/module/configuration that needs it rather than excluding widely and re-adding later.

## Diagnose-to-Fix Loop

1. Read the wrapper version and the current settings-level/project-level repository declarations.
2. Diagnose the symptom: provenance ambiguity (same GAV from two repositories), shadowing, or a wrong winner. Use `dependencyInsight` to see which repository supplied the artifact.
3. Choose the correct lever: `repositoriesMode` for enforcement, content filters for provenance, `exclusiveContent` for exclusivity.
4. Apply the smallest settings change and re-diagnose to confirm the artifact now resolves from the intended repository.

**Anti-pattern:** changing repository order to mask a provenance problem instead of applying a filter or exclusivity, or adding an unfiltered repository and relying on order alone.

**More info:**
- Declaring repositories: `gradle_docs(path="userguide/declaring_repositories.md")`
- Repository content filtering and `exclusiveContent`: `gradle_docs(path="userguide/filtering_repository_content.md")`
- Best practices for repositories and content filters: `gradle_docs(path="userguide/best_practices_dependencies.md")`
- Centralizing repository declarations (`dependencyResolutionManagement`, `RepositoriesMode`): `gradle_docs(path="userguide/centralizing_repositories.md")`
- Supported repository types (Maven, Ivy layouts, flat dir): `gradle_docs(path="userguide/supported_repository_types.md")`
- Supported repository protocols and authentication: `gradle_docs(path="userguide/supported_repository_protocols.md")`
- Basic repository declaration (public, custom, local): `gradle_docs(path="userguide/declaring_repositories_basics.md")`
- Basic repository authoring: `authoring-gradle-builds`'s [Dependencies and Catalogs](../../authoring-gradle-builds/references/dependencies-and-catalogs.md)
- Provenance inspection: `inspect_dependencies` and `dependencyInsight` via the `gradle` tool.
