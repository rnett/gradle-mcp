<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Dependencies and Catalogs

Author dependency declarations, version catalogs, repositories, and dependency-resolution policy. Use Kotlin DSL for build scripts and settings files. This reference is the authoring layer: hand off read-only coordinate lookup, graph inspection, conflict analysis, and update discovery to `using-gradle`.

## Operating Defaults

| Decision | Default | Anti-pattern |
|---|---|---|
| Dependency versions | Put shared versions in `gradle/libs.versions.toml`; use `version.ref` for aligned modules. | Repeat version literals across build scripts or hide versions in `ext`, arbitrary constants, or local variables when a catalog exists. |
| Declaration syntax | Use generated catalog accessors, then a single GAV string when no catalog is available. | Split `group`, `name`, and `version` across ad hoc variables or declare the same dependency redundantly. |
| Repository ownership | Declare dependency repositories in `settings.gradle.kts`; make order intentional and filter multiple repositories. | Add broad repositories independently in subprojects or assume repository order is irrelevant. |
| Version selection | Use constraints, platforms, and strict versions only for a stated resolution policy. | Force versions or add exclusions before inspecting the resolution path. |
| Resolution timing | Model dependencies lazily; resolve configurations only through task inputs or execution-time work. | Call `configurations.*.resolve()` during build-script configuration. |
| Removal | Remove the consuming declaration first, then delete unused catalog aliases and versions. | Delete a catalog entry while another project still consumes it. |

For rationale already covered by the frozen corpus, read [Use Version Catalogs to Centralize Dependency Versions](best-practices/use-version-catalogs-to-centralize-dependency-versions.md), [Name Version Catalog Entries Appropriately](best-practices/name-version-catalog-entries-appropriately.md), [Set up your Dependency Repositories in the Settings file](best-practices/set-up-your-dependency-repositories-in-the-settings-file.md), [Use Content Filtering with multiple Repositories](best-practices/use-content-filtering-with-multiple-repositories.md), [Declare Dependencies using a single GAV String](best-practices/declare-dependencies-using-a-single-gav-group-artifact-version-string.md), and [Apply Exclusions Narrowly](best-practices/apply-exclusions-narrowly.md). Do not restate their rationale here; apply their patterns with the MCP-specific workflow and version caveats below.

## Verify GAV Coordinates Before Authoring

1. Hand off coordinate discovery to `using-gradle`.
2. Use the `lookup_maven_versions` MCP tool to verify the release history for `group:artifact`; select an exact released version appropriate for the wrapper and project.
3. Use `inspect_dependencies` or `dependencyInsight` through `using-gradle` when the question is the selected or transitive version, not merely the declared version.
4. Record whether the selected artifact is a release, dynamic version, or changing/SNAPSHOT module before editing the catalog.

Do not invent a coordinate from a package name. Do not treat a catalog version as proof that the artifact resolves to that version: conflict resolution, constraints, platforms, capabilities, repository metadata, and resolution rules can change the winner.

**Version notes:** This workflow applies to Gradle 7, 8, and 9. Prefer the latest compatible stable release; use an exact version on 7.x rather than depending on modern resolution features. Version catalogs are stable from 7.4, but coordinate verification remains independent of catalog support.

**More info:**
- Gradle docs: `gradle_docs` `tag:userguide` with `viewing debugging dependencies`, path `userguide/viewing_debugging_dependencies.md`; https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html.
- Dependency conflicts: `gradle_docs` `tag:userguide` with `dependency constraints conflict resolution`, path `userguide/dependency_constraints_conflicts.md`; https://docs.gradle.org/current/userguide/dependency_constraints_conflicts.html.
- MCP coordinate lookup: `lookup_maven_versions`; https://gradle-mcp.rnett.dev/latest/tools/DEPENDENCY_SEARCH_TOOLS/.
- MCP graph inspection: `inspect_dependencies`; https://gradle-mcp.rnett.dev/latest/tools/PROJECT_DEPENDENCY_TOOLS/.

## Version Catalog TOML

Use `gradle/libs.versions.toml` for the default catalog. Keep aliases descriptive, kebab-case, and stable because alias changes regenerate accessors and touch consumers.

```toml
[versions]
kotlin = "2.0.0"
retrofit = "2.9.0"
junit = "5.10.0"
androidx-core = "1.12.0"

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
retrofit-core = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-gson = { module = "com.squareup.retrofit2:converter-gson", version.ref = "retrofit" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }

[bundles]
networking = ["retrofit-core", "retrofit-gson"]

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
android-gradle = { id = "com.android.application", version = "8.2.0" }
```

Use the four tables as follows:

- `[versions]`: define reusable version constants. Use `version.ref` when several modules must move together.
- `[libraries]`: define module coordinates with `module = "group:name"`, or use separate `group` and `name` keys. Use a direct `version` only when the version is intentionally independent.
- `[bundles]`: group aliases that are normally consumed together. Do not use bundles to hide unrelated or optional dependencies.
- `[plugins]`: define plugin IDs and versions for `alias(libs.plugins.<alias>)`. Keep plugin versions centralized, but apply plugins through the declarative `plugins {}` block.

**Default:** Use one `libs` catalog for shared external dependencies. Add another catalog only when ownership or lifecycle is genuinely separate.

**Anti-patterns:** Do not put full GAV strings in every consuming build script when the project already has a catalog. Do not create aliases with ambiguous names, encode configuration names in aliases, or add a bundle merely to shorten one declaration.

**Version notes:** Catalogs became experimental in Gradle 7.0 and stable in 7.4. Gradle 8 and 9 support the same core TOML sections and accessors. For Gradle 7.0 through 7.3, preserve an existing catalog cautiously; for new catalog work, use the 7.x fallback of `buildSrc`, applied scripts, or `ext` when the build cannot upgrade to 7.4.

**More info:**
- Gradle docs: `gradle_docs` `tag:userguide`, path `userguide/version_catalogs.md`; https://docs.gradle.org/current/userguide/version_catalogs.html.
- Catalog rationale: [Use Version Catalogs to Centralize Dependency Versions](best-practices/use-version-catalogs-to-centralize-dependency-versions.md).

## Type-Safe Accessors and Plugin Aliases

Gradle generates type-safe Kotlin accessors from catalog aliases. Kebab-case TOML aliases become dot-separated accessors:

```kotlin
dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.retrofit.core)
    implementation(libs.bundles.networking)
    testImplementation(libs.junit.jupiter)
}

plugins {
    alias(libs.plugins.kotlin.jvm)
}
```

Use the catalog accessor in a consuming project only after the catalog is available to that project. If an alias is renamed, update every generated accessor use in the same change.

**Default:** Use accessors for project dependencies and `libs.plugins` aliases for plugins. Keep the TOML alias readable enough that generated property names remain obvious.

**Anti-patterns:** Do not guess accessor spelling, use a catalog alias before Gradle regenerates accessors, or mix an alias with a second hard-coded version for the same module.

**Version notes:** Generated accessors are supported with stable catalogs in Gradle 7.4, 8, and 9. On Gradle 7.0 through 7.3, use the existing catalog only if the build already relies on the experimental feature; otherwise use the `buildSrc` or `ext` fallback.

**More info:** `gradle_docs` `tag:userguide`, path `userguide/version_catalogs.md`; https://docs.gradle.org/current/userguide/version_catalogs.html.

## Declare Dependencies

With a catalog, declare the alias in the appropriate configuration:

```kotlin
dependencies {
    implementation(libs.slf4j.api)
    testImplementation(libs.mockk)
}
```

Without a catalog, prefer a single GAV string:

```kotlin
dependencies {
    implementation("org.slf4j:slf4j-api:2.0.12")
}
```

**This is prohibited:** Using map-notation (`group = "...", name = "...", version = "..."`) for dependency declarations. Map-notation was deprecated in Gradle 9.1 and will fail in Gradle 10. Use a single GAV string for all non-catalog declarations.

Use the configuration that matches exposure and lifecycle: `api` for dependencies required by consumers of a library, `implementation` for internal runtime and compile dependencies, `compileOnly` for compile-time APIs supplied elsewhere, `runtimeOnly` for runtime providers, and `testImplementation` for test code. For a deeper dive into how these roles interact and how to model custom variants, read [Configurations and Variants](configurations-and-variants.md).
129:
130:**Default:** Add the narrowest declaration that supplies the required classpath. Avoid explicit Kotlin standard-library declarations when the Kotlin Gradle Plugin supplies them.
131:
132:**Anti-patterns:** Do not declare a test dependency as `implementation`, put an internal library on `api` without consumer need, or duplicate a dependency already supplied transitively or by a platform.
133:
134:**Version notes:** Configuration names and single-string notation work in Gradle 7, 8, and 9. Prefer the current Kotlin DSL and exact versions; on older 7.x builds, follow the existing declaration style if a plugin exposes a legacy configuration.
135:
136:**More info:**
137:- Gradle docs: `gradle_docs` `tag:userguide`, path `userguide/declaring_dependencies.md`; https://docs.gradle.org/current/userguide/declaring_dependencies.html.
138:- Catalogs: `gradle_docs` `tag:userguide`, path `userguide/version_catalogs.md`; https://docs.gradle.org/current/userguide/version_catalogs.html.
139:- Approved declaration rationale: [Declare Dependencies using a single GAV String](best-practices/declare-dependencies-using-a-single-gav-group-artifact-version-string.md).

## Repositories, Content Filters, and Order

Declare repositories in `settings.gradle.kts` so every project uses the same policy:

```kotlin
import org.gradle.api.initialization.resolve.RepositoriesMode

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven {
            name = "company"
            url = uri("https://repo.example.com/releases")
            content {
                includeGroupByRegex("com\\.example(\\..*)?")
            }
        }
    }
}
```

When a private repository is required, put the repositories in deliberate order and constrain the private repository to its owned groups. Repository order matters: a repository earlier in the list can determine metadata and artifact provenance, and a private repository can shadow public content if it is broad or unfiltered.

If settings-level repository management is not used, declare repositories in the project build script as a compatibility fallback, not as the preferred structure:

```kotlin
repositories {
    mavenCentral()
}
```

**Default:** Prefer settings-level declarations, `FAIL_ON_PROJECT_REPOS`, explicit order, and `content {}` filters whenever multiple repositories exist.

**Anti-patterns:** Do not add repositories ad hoc in subprojects, use an unfiltered private repository beside Maven Central, reorder repositories casually during diagnosis, or assume identical GAV coordinates prove identical artifact provenance.

**Version notes:** Repository declarations and content filtering exist across Gradle 7, 8, and 9. Current best-practice guidance is emphasized in 9.x; on 7.x and 8.x, retain settings-level declaration and use filters where multiple repositories make provenance ambiguous. If there is one trusted repository, a filter may be unnecessary.

**More info:**
- Gradle docs: `gradle_docs` `tag:best-practices`, path `userguide/best_practices_dependencies.md`, terms `repositories` and `content filtering`; https://docs.gradle.org/current/userguide/best_practices_dependencies.html.
- Repository declaration: `gradle_docs` `tag:userguide`, path `userguide/declaring_repositories.md`; https://docs.gradle.org/current/userguide/declaring_repositories.html.
- Approved patterns: [Set up your Dependency Repositories in the Settings file](best-practices/set-up-your-dependency-repositories-in-the-settings-file.md) and [Use Content Filtering with multiple Repositories](best-practices/use-content-filtering-with-multiple-repositories.md).

## Constraints and Conflict Resolution

Use a constraint to influence a module's version without adding the module to the dependency graph:

```kotlin
dependencies {
    constraints {
        implementation("org.slf4j:slf4j-api:2.0.12") {
            because("The application requires the security and runtime fixes in this release")
        }
    }
}
```

Use `strictly` when the build must reject versions outside a bounded policy:

```kotlin
dependencies {
    implementation("org.apache.logging.log4j:log4j-core") {
        version {
            strictly("2.20.0")
        }
    }
}
```

Use `strictly` sparingly. A strict constraint can reject a graph that would otherwise resolve and can conflict with a platform or another strict constraint. Use a platform or BOM for a coordinated family, and use a constraint for a minimum or preferred version policy.

Normal conflict resolution generally selects the highest requested version, but that is not universal. Platforms, enforced platforms, strict constraints, capabilities, component metadata rules, dependency substitution, and other resolution rules can change the winner. Inspect the graph before forcing a result.

**Default:** State the intent in `because(...)`, use a constraint for policy, a platform for alignment, and `strictly` only when rejection of other versions is required.

**Anti-patterns:** Do not use `force` as the first response to a conflict, add strict versions without a compatibility reason, or infer the winner from the direct declaration alone.

**Version notes:** Constraints and dependency version declarations are available in Gradle 7, 8, and 9. Resolution behavior is version-sensitive when platforms, capabilities, or rules are involved; verify the target wrapper and inspect the resolved graph. Gradle 7.x builds that lack a suitable catalog can use direct constraints in Kotlin DSL.

**More info:**
- Gradle docs: `gradle_docs` `tag:userguide`, path `userguide/dependency_constraints_conflicts.md`; https://docs.gradle.org/current/userguide/dependency_constraints_conflicts.html.
- Platforms and alignment: `gradle_docs` `tag:userguide`, path `userguide/platforms.md`; https://docs.gradle.org/current/userguide/platforms.html.
- Resolution rules: `gradle_docs` `tag:userguide`, path `userguide/resolution_rules.md`; https://docs.gradle.org/current/userguide/resolution_rules.html.
- Graph verification: `inspect_dependencies`; https://gradle-mcp.rnett.dev/latest/tools/PROJECT_DEPENDENCY_TOOLS/.

## Exclusions and BOMs

Exclude a transitive module only at the narrowest declaration that has a verified reason:

```kotlin
dependencies {
    implementation(libs.some.library) {
        exclude(group = "unwanted.group", module = "unwanted-module")
    }
}
```

Use a BOM through `platform()` to align a family without adding every member as a direct dependency:

```kotlin
dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter.web)
}
```

Use `enforcedPlatform()` only when all versions imported by the platform must be enforced and the consequences for consumers are understood.

**Default:** Prefer a platform for coordinated family versions and a narrow exclusion for a known unwanted transitive module.

**Anti-patterns:** Do not apply a global exclusion to compensate for one bad edge, exclude a module without checking whether the remaining library still works, or use `enforcedPlatform()` as a general conflict fix.

**Version notes:** `platform()` and dependency exclusions are available across Gradle 7, 8, and 9. Verify the published BOM's supported Gradle and library versions. On older 7.x builds, keep the same declarations but avoid assuming modern platform metadata or plugin behavior without checking the wrapper.

**More info:**
- Platforms: `gradle_docs` `tag:userguide`, path `userguide/platforms.md`; https://docs.gradle.org/current/userguide/platforms.html.
- Exclusions: `gradle_docs` `tag:userguide`, path `userguide/dependency_constraints_conflicts.md`, term `exclude transitive dependencies`; https://docs.gradle.org/current/userguide/dependency_constraints_conflicts.html.
- Approved exclusion rationale: [Apply Exclusions Narrowly](best-practices/apply-exclusions-narrowly.md).

## Multiple Catalogs

Use additional catalogs only when the dependency sets have separate ownership, repository scope, or release cadence. Register them in settings:

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
        create("internal") {
            from(files("gradle/internal-libs.versions.toml"))
        }
    }
}
```

Consume an additional catalog by its generated accessor:

```kotlin
dependencies {
    implementation(internal.my.internal.library)
}
```

**Default:** Keep the default `libs` catalog as the shared external catalog; add named catalogs only for a clear boundary.

**Anti-patterns:** Do not split one dependency family across catalogs, duplicate the same version in multiple catalogs, or create catalogs only to avoid choosing a clear alias name.

**Version notes:** Multiple catalogs are supported with version catalogs in Gradle 7.4, 8, and 9. For Gradle 7.0 through 7.3, use the existing experimental support cautiously or fall back to `buildSrc`, applied scripts, or `ext`.

**More info:** `gradle_docs` `tag:userguide`, path `userguide/version_catalogs.md`; https://docs.gradle.org/current/userguide/version_catalogs.html.

## Import a Catalog into `build-logic`

An included `build-logic` build has its own settings and does not automatically receive the root build's catalog. Import the root TOML explicitly in `build-logic/settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
```

Use the imported catalog from convention-plugin code or build-logic project build scripts:

```kotlin
dependencies {
    implementation(libs.kotlin.gradle.plugin)
}
```

**Default:** Import the catalog explicitly and keep build-logic dependencies aligned with the root catalog only when they share the same compatibility contract.

**Anti-patterns:** Do not assume an included build inherits root settings, declare a second uncoordinated version for the same plugin, or use `../` paths that do not match the actual `build-logic` location.

**Version notes:** Catalog import with `from(files(...))` is supported for stable catalogs in Gradle 7.4, 8, and 9. On 7.0 through 7.3, use the existing experimental mechanism cautiously or use build-logic `buildSrc`/`ext` fallback patterns.

**More info:** `gradle_docs` `tag:userguide`, path `userguide/version_catalogs.md`, term `sharing catalogs`; https://docs.gradle.org/current/userguide/version_catalogs.html. For build-logic structure, read [Favor `build-logic` Composite Builds for Build Logic](best-practices/favor-build-logic-composite-builds-for-build-logic.md).

## Dynamic Versions and SNAPSHOT Cache Freshness

Avoid dynamic versions (`1.+`, `latest.release`) and changing/SNAPSHOT dependencies by default. They make the resolved graph depend on metadata cache state and repository availability.

Gradle's documented default cache TTL for dynamic versions and changing modules is 24 hours unless the build overrides it. If an intentional refresh is required, hand off execution to `using-gradle` and use `--refresh-dependencies`; record the exact selected version and repository context. Do not rerun the same command and assume Gradle immediately rechecks remote metadata.

**Default:** Use stable exact releases in catalogs and declarations. Use SNAPSHOT only for an explicit development dependency with an understood refresh policy.

**Anti-patterns:** Do not commit dynamic selectors for production dependencies, diagnose a stale SNAPSHOT graph without checking cache policy, or claim that a catalog makes a changing module reproducible.

**Version notes:** Dynamic and changing-module caching behavior applies to Gradle 7, 8, and 9; exact cache defaults and resolution rules are version-sensitive. Prefer the latest Gradle minor and verify the wrapper's documentation before changing TTL settings.

**More info:** `gradle_docs` `tag:userguide`, path `userguide/dependency_caching.md`; https://docs.gradle.org/current/userguide/dependency_caching.html. For refresh and graph evidence, hand off to `using-gradle`; `inspect_dependencies` is documented at https://gradle-mcp.rnett.dev/latest/tools/PROJECT_DEPENDENCY_TOOLS/.

## Remove a Dependency Safely

1. Remove the dependency declaration from every consuming `build.gradle.kts` or convention plugin.
2. Search all projects and build logic for the alias or direct GAV.
3. Remove the alias from `[libraries]` only after no consumer remains.
4. Remove the `[bundles]` entry or its member if the removed library was bundled.
5. Remove the `[versions]` entry only when no library or plugin references it.
6. Remove repository content filters only if no remaining dependency needs that repository, then re-check repository order and policy.
7. Hand off to `using-gradle` to inspect the affected classpaths and verify that no required transitive API was removed.

**Default:** Remove declarations first and clean catalog metadata second, with graph verification after the change.

**Anti-patterns:** Do not delete an alias based on one module's usage, remove a shared version still referenced by a plugin, or treat a successful compile as proof that runtime and test configurations remain correct.

**Version notes:** The removal workflow is valid for Gradle 7, 8, and 9. Catalog cleanup differs only in the availability of stable catalog accessors: use the 7.4+ catalog workflow or the 7.x `buildSrc`/`ext` fallback.

**More info:** `gradle_docs` `tag:userguide`, path `userguide/viewing_debugging_dependencies.md`; https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html. Graph inspection: `inspect_dependencies`; https://gradle-mcp.rnett.dev/latest/tools/PROJECT_DEPENDENCY_TOOLS/.

## Version Summary

| Gradle | Catalog guidance | Dependency-authoring fallback |
|---|---|---|
| 9.x | Catalogs are stable; prefer the latest compatible minor and current Kotlin DSL. | Use settings-level repositories, filters, constraints, platforms, and lazy resolution; verify behavior against the wrapper. |
| 8.x | Catalogs are stable and preferred. | Preserve the same patterns, checking plugin and metadata compatibility. |
| 7.4-7.x | Catalogs are stable from 7.4. | For 7.0-7.3 or incompatible legacy plugins, use `buildSrc`, applied scripts, or `ext`; avoid introducing a new catalog until the build can use stable support. |

Do not confuse declared versions with resolved versions. Use exact releases and catalogs for authoring, then hand off graph evidence and conflict diagnosis to `using-gradle`.
