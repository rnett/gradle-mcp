# Composite Builds

A composite build is a build that includes other standalone builds (included builds) and consumes their projects instead of their published artifacts. Use it to develop against a library in source, share build logic, or compose separately owned builds. This reference covers authoring composites; for operating a build that already includes them, see [using-gradle Included Builds](../../using-gradle/references/included-builds.md).

Read `gradle/wrapper/gradle-wrapper.properties` before version-sensitive advice; included-build behavior and task-path addressing change across Gradle versions.

## Declaring an Included Build

Include another build from the root `settings.gradle.kts` with `includeBuild(...)`. Each included build is a complete, standalone build with its own `settings.gradle.kts` (and optionally its own `buildSrc`); it is not a subproject of the root build.

```kotlin
// root/settings.gradle.kts
rootProject.name = "my-app"
includeBuild("../some-library")
```

The included build's root project name identifies it in task paths and substitutions. Prefer the settings declaration for anything durable; reserve the CLI form for one-off local experiments.

### CLI `--include-build`

Pass `--include-build <dir>` on the command line to include a build for a single invocation without editing settings:

```text
gradle build --include-build ../some-library
```

**Default:** declare durable composites in `settings.gradle.kts`; use `--include-build` only for temporary local experiments. Anything you would script or share belongs in settings.

**Anti-pattern:** relying on `--include-build` for a workflow you will repeat; the setting disappears on the next invocation, so a "working" build can fail later for no code change.

## What Including a Build Changes

An included build stays a separate build: it has its own settings, lifecycle, configuration cache, and build-logic boundary, and it is not a multi-project subproject of the root build. The root build cannot refer to an included build's projects with `project(":...")`, and the included build cannot see the root build's projects either. The only couplings are:

- **Dependency substitution:** a requested external coordinate can resolve to an included build's project (see below).
- **Plugin resolution:** a plugin can be supplied by an included plugin build (see below).
- **Task wiring:** tasks can depend on tasks in an included build through cross-build task dependencies (see below).

**Anti-pattern:** treating an included build like a subproject — configuring it from the root, reading its `Project` objects, or assuming root settings apply to it.

## Consuming Dependencies from an Included Build

When a dependency in the root build requests a coordinate whose `group:name` matches a project in an included build, Gradle substitutes that project automatically — no `dependencySubstitution` rule is required. **Matching is by `group:name` only; the requested version is not part of the match, and the included build's project version does not need to equal the requested version.**

```kotlin
// root/build.gradle.kts
dependencies {
    // Resolves to the some-library project when the coordinate matches group:name
    implementation("com.example:some-library:1.0")
}
```

```kotlin
// some-library/build.gradle.kts
group = "com.example"
// version is irrelevant for the substitution match
```

The version in the declaration stays in the requested graph for conflict reporting, but the resolved component is the included build's project.

**Anti-pattern:** expecting the substitution to require the requested version to equal the included build's version (an "exact GAV match"), or hand-authoring a `dependencySubstitution` rule when the automatic `group:name` match already applies.

### Explicit `dependencySubstitution` Rules

When you need substitution beyond the automatic coordinate match — or when the included build's declared coordinates do not align with what consumers request — declare an explicit rule in the consuming build's `resolutionStrategy`:

```kotlin
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.example:old-artifact"))
            .using(project(":some-library"))
    }
}
```

Explicit rules also cover module-to-module swaps that are unrelated to composites. Prefer automatic composite substitution when the coordinates align; use an explicit rule when they do not. See [Substitution and Composites](../../advanced-gradle-dependencies/references/substitution-and-composites.md) for the diagnose-first workflow.

## Consuming Plugins from an Included Build

Include a build in `pluginManagement` to resolve plugins from it instead of from a plugin portal:

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("build-logic")
}
```

The included build contributes its plugins (including precompiled script plugins) to resolution. This is the standard pattern for convention plugins; see [Convention Plugins](convention-plugins.md) for the full build-logic workflow.

**Default:** use `pluginManagement { includeBuild(...) }` for project-specific plugins and convention plugins; keep plugin repositories in `pluginManagement.repositories`.

**Anti-pattern:** declaring `includeBuild` at the settings root and separately in `pluginManagement`, which double-includes the build for different purposes without a clear reason.

## buildSrc vs Composite Build

`buildSrc` is a special, implicit included build for a single root build's own build logic. A composite build is the general mechanism: it works for any included build, supports dependency substitution and cross-build task wiring, and gives each build narrower configuration-cache and input invalidation boundaries.

**Default:** use a `build-logic` included build via `pluginManagement { includeBuild("build-logic") }` for reusable build logic; fall back to `buildSrc` only for very small builds where the logic rarely changes.

**Anti-pattern:** using `buildSrc` for large or frequently changing logic — every `buildSrc` change invalidates the whole build's configuration cache — or using a composite build when a simple multi-project layout would do.

## Nested and Indirect Included Builds

An included build may itself include further builds. The composite relationship is not transitive for configuration: each build keeps its own settings, and the root build sees only its direct included builds. Indirect included builds (an included build's own included builds) do not participate in the root build's substitutions unless the middle build re-declares the wiring.

**Default:** keep the included-build graph flat and explicit; declare each coupling at the build that needs it.

**Anti-pattern:** assuming an included build's `includeBuild` is visible to the root build, or relying on a transitive chain to substitute dependencies across three levels.

## Scripting Cross-Build Task Wiring

Cross-build task dependencies let a task in the root build depend on a task in an included build:

```kotlin
// root/build.gradle.kts
tasks.register("buildAll") {
    dependsOn(gradle.includedBuild("some-library").task(":assemble"))
}
```

Address the included build by its root project name. Depend on the included build's task, not on its project object.

**Default:** wire cross-build work through `dependsOn` on the included build's task; keep the dependency direction explicit.

**Anti-pattern:** trying to configure or read the included build's project from the root build, or depending on tasks by guessed names without `help --task`.

## Version notes

- **Gradle 9.x:** included-build substitution, `pluginManagement { includeBuild(...) }`, and cross-build task wiring are stable. Gradle 9.0 fails when an included build directory is missing or read-only; validate included-build directories during authoring rather than treating them as optional placeholders.
- **Gradle 8.x:** the same substitution and plugin-resolution behavior applies; verify version-sensitive details against the wrapper.
- **Gradle 7.6+:** included builds may be addressed with abbreviated names in task paths when unambiguous; `--include-build` is available throughout 7.x.
- **Gradle 7.0–7.5:** task paths must use the full included-build name; abbreviated addressing is not supported.

**More info:**

- Composite builds: `gradle_docs(path="userguide/composite_builds.md")`
- Using a local fork of a module dependency: `gradle_docs(path="userguide/how_to_use_local_forks.md")`
- Sharing build logic with `buildSrc`: `gradle_docs(path="userguide/sharing_build_logic_between_subprojects.md")`
- Settings API (`includeBuild`): `gradle_docs(path="dsl/org.gradle.api.initialization.Settings.md")`
- Plugin management (`pluginManagement { includeBuild(...) }`): `gradle_docs(path="dsl/org.gradle.plugin.management.PluginManagementSpec.md")`
- Gradle documentation lookup: `gradle_docs`

**Cross-references:**

- Build-logic composites and convention plugins -> [Convention Plugins](convention-plugins.md)
- Settings and project structure -> [Modules and Settings](modules-and-settings.md)
- Diagnosing substitution and composite resolution -> [Substitution and Composites](../../advanced-gradle-dependencies/references/substitution-and-composites.md)
- Operating a build with included builds -> [Included Builds](../../using-gradle/references/included-builds.md)
