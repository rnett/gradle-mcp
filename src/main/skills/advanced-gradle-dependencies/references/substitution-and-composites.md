# Substitution and Composites

Dependency substitution and composite builds change what a dependency resolves to without editing the consuming declaration. They are diagnose-first levers: confirm the resolution path, decide whether substitution or a composite is the correct correction, then author the smallest change and re-diagnose.

Read `gradle/wrapper/gradle-wrapper.properties` before version-sensitive advice; substitution and composite-build behavior and their interaction with other resolution rules change across Gradle versions.

## Dependency Substitution

Dependency substitution replaces one dependency with another during resolution. Register substitutions in `resolutionStrategy`:

```kotlin
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.example:old-artifact"))
            .using(module("com.example:new-artifact"))
    }
}
```

Common uses:

- **Module substitution** (`module(...)` to `module(...)`): swap one externally-resolved module for another (for example, a renamed artifact or a local-coordinate replacement).
- **Project substitution** (`module(...)` to `project(...)`): replace an external module with a local project during development.
- **Replacement only for a subset:** scope the substitution to specific configurations rather than applying it globally when the swap should not affect every graph.

Substitution happens during resolution and rewrites the dependency before variant selection and conflict resolution. Because it can change the winner, treat it as a resolution rule like constraints and platforms: inspect the graph before and after to confirm the intended effect.

**Anti-pattern:** using substitution when a version bump, alias, or platform is the actual intent, or substituting broadly in `configurations.all` when only one configuration needs the swap.

## Composite Builds (Diagnosis)

A composite build lets one Gradle build include other builds as dependencies, so a module resolves from source instead of from a repository. Authoring composites (declaring `includeBuild`, wiring plugin-management inclusion, and choosing buildSrc vs composite) lives in `authoring-gradle-builds`; see [Composite Builds](../../authoring-gradle-builds/references/composite-builds.md). This section covers diagnosing how a composite changes resolution.

Common uses to recognize when diagnosing:

- **Develop against a library in source** via project substitution without publishing or local-installing it.
- **Coordinate multiple builds** with a shared delivery/consumption contract.
- **Consume a build-logic build** as a composite for isolated build logic.

A composite substitutes the included build's published coordinates with the included build's projects automatically. This is distinct from a hand-authored `dependencySubstitution`: the plugin/build wiring declares the coupling, and the resolution substitutes accordingly. **Automatic substitution matches by `group:name`, not the full GAV**: when the requested `group:name` matches an included build project's declared coordinates, Gradle substitutes that project regardless of the requested or declared version. Do not require or expect version equality (an "exact GAV match") for substitution to fire.

**Anti-pattern:** adding an `includeBuild` solely to avoid a version bump, or relying on composite substitution without understanding that it overrides the repository-sourced version of the included coordinates. Authoring fixes for a composite (path, coordinates, plugin wiring) are routed to `authoring-gradle-builds`; diagnose here first, then route the authoring change.

### Diagnosing a Local Fork of a Module Dependency

To check whether a module resolves from a local fork of its sources:

- Confirm `includeBuild("path/to/fork")` is declared in `settings.gradle.kts` and that the fork's `group`/`name` match the external dependency it replaces — the `group:name` match is what triggers substitution; the version is not part of the match.
- Declare the external coordinate normally in the consumer (no hand-written `dependencySubstitution` needed); Gradle substitutes from the composite automatically.
- If the module still resolves externally, check the `group:name` match and that `includeBuild` points at the right directory; then route the authoring fix to `authoring-gradle-builds`.

## Diagnose-to-Fix Loop

1. Read the wrapper version; run `dependencyInsight` to confirm which dependency resolves and from where.
2. Decide the correct mechanism: a narrowly-scoped `dependencySubstitution` (rename/local dev swap) vs an `includeBuild` composite (consume from source).
3. Author the smallest change on the correct handler.
4. Re-run the diagnostic to confirm the substituted or composed dependency resolves as intended, and that no unintended coordinates changed.

**Anti-pattern:** authoring substitution or a composite before confirming the resolution path, or assuming a swap applies everywhere when `configurations.all` is not where it belongs.

**More info:**
- Dependency substitution: `gradle_docs(path="userguide/resolution_rules.md")`
- Composite builds: `gradle_docs(path="userguide/composite_builds.md")`
- Viewing and debugging dependencies: `gradle_docs(path="userguide/viewing_debugging_dependencies.md")`
- Using a local fork of a module dependency: `gradle_docs(path="userguide/how_to_use_local_forks.md")`
- Gradle API dependencies (`gradleApi()`, `gradleTestKit()`, `localGroovy()`): `gradle_docs(path="userguide/gradle_dependencies.md")`
- Authoring composite builds (declaring `includeBuild`, plugin-management inclusion, buildSrc vs composite): see [Composite Builds](../../authoring-gradle-builds/references/composite-builds.md) and [Convention Plugins](../../authoring-gradle-builds/references/convention-plugins.md).
- Graph and winner inspection: `inspect_dependencies`; `dependencyInsight` via the `gradle` tool.
