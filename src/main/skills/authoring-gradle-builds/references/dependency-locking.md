# Dependency Locking

Use dependency locking when reproducibility matters more than automatically receiving newly published versions. A lock records the resolved module versions for a configuration. It does not replace declared constraints, repository policy, dependency verification, or a deliberate upgrade process.

**Default:** Lock every resolvable project configuration that participates in CI, including compile, runtime, test, plugin, and tooling classpaths as applicable. Generate locks from the wrapper version used by the build, review the diff, and commit the lockfiles to VCS.

**Anti-patterns:** Do not hand-edit lockfiles, lock only the root project and assume subprojects are covered, or run `--write-locks` as an unreviewed CI side effect. Do not treat a lockfile as proof that an artifact is trustworthy; use dependency verification and repository content filtering for that purpose.

## Enable Locking

Enable locking per resolvable configuration when only selected graphs need reproducibility:

```kotlin
configurations.named("runtimeClasspath") {
    resolutionStrategy.activateDependencyLocking()
}

configurations.named("testRuntimeClasspath") {
    resolutionStrategy.activateDependencyLocking()
}
```

Enable it for all configurations when the build must have a uniform policy. Gradle evaluates the rule for configurations as they are created:

```kotlin
configurations.configureEach {
    resolutionStrategy.activateDependencyLocking()
}
```

Prefer `configureEach` over an eager iteration. If a convention plugin owns the policy, apply it explicitly to each project that must be locked. Locking the root project's configurations does not lock included builds or subprojects.

## Generate and Update Locks

`--write-locks` writes the lock state for configurations resolved by the selected invocation. Resolve the intended graphs; a task named `dependencies` is useful for inspection, but it is not a substitute for resolving every configuration used by CI.

```text
gradlew.bat :app:compileJava :app:test --write-locks
```

For an intentional upgrade, change the declaration or constraint first, run the smallest representative resolution with `--write-locks`, inspect all lockfile changes, then commit the declaration and lock changes together. Keep lock generation out of ordinary verification commands. Never use `--write-locks` merely to make a failing verification pass.

### Lockfile layout

Gradle 7 and later use one lockfile per project, normally at the project directory:

```text
gradle.lockfile
```

The file contains sorted module coordinates and the configurations that use each locked module. A buildscript classpath uses the separate file:

```text
buildscript-gradle.lockfile
```

Do not recreate the pre-Gradle-7 per-configuration directory layout in a current build. When migrating an old build, let the target Gradle version write the current format and review the resulting deletion or consolidation carefully. Each subproject has its own project lockfile; included builds maintain their own lock state.

## Commit and Verify in CI

Commit every lockfile required by the build, including `buildscript-gradle.lockfile` when buildscript dependencies are locked. Review lock diffs as dependency changes, not generated noise. Ensure the checkout includes lockfiles before running CI; a missing file can make a graph unlocked rather than reproducible if locking was not enabled for that graph.

CI must activate the same locking policy and must not pass `--write-locks`. A normal locked resolution validates the requested graph against the committed lock state and fails when the state cannot satisfy it. Use a separate, review-gated dependency-update job or developer command to regenerate locks.

A useful verification shape is:

```text
gradlew.bat :app:check --offline
```

Use `--offline` only when the CI cache is intentionally pre-populated. Otherwise run the normal online build and treat repository or metadata failures separately from lock mismatches. The important invariant is unchanged: CI resolves with locking enabled and never mutates the workspace.

## Dynamic Versions and Refreshes

Dynamic declarations such as `1.+`, version ranges, and changing modules such as `-SNAPSHOT` are poor inputs to a reproducible build. Locking records the selected version, so subsequent resolution uses the locked version instead of silently selecting a newer matching version.

`--refresh-dependencies` refreshes dependency metadata and cached artifacts. It does not, by itself, rewrite committed lock state. With locking enabled, the lock remains authoritative; use `--write-locks` only when intentionally accepting a newly resolved version and reviewing that change. If the requested declaration no longer permits the locked version, fail and update the declaration and lock deliberately.

**Default:** Prefer fixed versions or version-catalog declarations, then lock the resolved graph. Use dynamic versions only when a controlled update workflow explicitly owns their refresh and lock review.

**Anti-pattern:** Combine dynamic versions, `--refresh-dependencies`, and `--write-locks` in an unattended pipeline. That turns a metadata refresh into an unreviewed dependency update.

## Locking Pitfalls

- **Wrong graph:** Running `dependencies` at the root does not resolve every subproject or every configuration. Target the projects and configurations that production and CI actually consume.
- **Partial policy:** Locking `runtimeClasspath` does not lock `testRuntimeClasspath`, plugin classpaths, or custom resolvable configurations. Apply the policy at the correct project boundary.
- **Untracked build logic:** A locked project graph does not automatically lock dependencies in `buildSrc` or an included `build-logic` build. Configure and commit locks in those builds independently.
- **Changing declarations:** A removed or changed dependency can make the existing lock state invalid. Regenerate through the intended update workflow; do not delete entries by hand.
- **Changing Gradle versions:** Lockfile format and behavior have changed across major versions. Generate with the target wrapper and test the migration before committing a broad rewrite.
- **Non-lockable inputs:** Dependency locking does not apply to source dependencies. Do not claim that a lockfile freezes every input to a build.

## Version notes

- **Gradle 9.x:** Use the current single per-project lockfile format, `configurations.configureEach`, and the latest compatible wrapper/plugin versions. Bias authoring toward the current 9.x minor; see [Use the Latest Minor Version of Gradle](best-practices/use-the-latest-minor-version-of-gradle.md).
- **Gradle 8.x:** Dependency locking and the single lockfile format are supported. Validate plugin and custom-configuration behavior against the wrapper because lock participation is configuration-specific.
- **Gradle 7.x:** Dependency locking is supported, and Gradle 7 introduced the single per-project lockfile format. Older builds may require migration from legacy per-configuration lockfiles. For 7.0 through 7.3, preserve existing dependency-management conventions cautiously; do not assume newer build logic is available.

**More info:**
- Locking: query `gradle_docs` with `tag:userguide`, path `userguide/dependency_locking.md`, term `--write-locks`
- Dependency caching and refresh semantics: query `gradle_docs` with `tag:userguide`, path `userguide/dependency_caching.md`, terms `dynamic changing refresh offline`; this is the verified hint from the dependency reference.
- Gradle task invocation and `--write-locks`: `gradle`
- Dependency graph inspection and update evidence: `inspect_dependencies`; Maven release lookup
