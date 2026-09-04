# Dependency Locking Deep Dive

Goes beyond the `authoring-gradle-builds` locking basics (enable, generate, commit, CI) into lock modes and the deeper behavior of the lock state. Use this when a locking question is about which graph is locked, how a lock mode changes resolution, or why a locked build behaves as it does.

Read `gradle/wrapper/gradle-wrapper.properties` before version-sensitive advice; locking modes and lockfile behavior change across Gradle versions.

## Lock Modes

Gradle supports two locking modes that change what the lock state actually pins:

- **`DEFAULT`** (the `dependency-locking` defaults): caches the selected versions for the requested ranges/dynamic versions. A dynamic or range declaration (e.g. `1.+`) locks to the version that satisfied it, so the graph stays reproducible even with a dynamic declaration.
- **`STRICT`**: in addition to `DEFAULT`, requires that a dependency's declaration match the locked entry. A declaration change that no longer permits the locked version fails even if a compatible alternative exists, forcing an explicit `--write-locks` to accept the new selection.

Choose the mode by how strictly the build must reject drift. `STRICT` is the stronger guard when declarations and locks must move in lockstep; `DEFAULT` is the everyday choice that still delivers reproducibility.

Set the mode through the locking strategy on the relevant configurations (the exact API is version-scoped; verify against the wrapper before applying).

## What Is Locked

Locking applies per resolvable configuration, not globally. Key non-obvious behaviors:

- **Per-project lockfiles:** each project has its own `gradle.lockfile`; included builds and `buildSrc`/`build-logic` builds maintain their own lock state independently.
- **Configuration scoping:** locking `runtimeClasspath` does not lock `testRuntimeClasspath`, plugin classpaths, or custom resolvable configurations. Apply the policy at the correct project boundary.
- **Buildscript classpath:** buildscript dependencies lock into the separate `buildscript-gradle.lockfile`.
- **Non-lockable inputs:** source dependencies do not participate in dependency locking; a lockfile does not freeze every input to a build.

Diagnose "unlocked when I expected locked" by checking which configurations have `activateDependencyLocking()` and whether the graph in question resolves through a locked configuration.

## Locking Mechanics and Enforcement

Locked versions are enforced **as if declared `strictly()`**. During resolution the lock acts as a strict constraint on the selected version:

- A declaration **lower** than the locked version silently upgrades to the locked version (e.g. declared `1.0.0`, locked `1.2.0` → resolves `1.2.0`).
- A declaration **higher** than the locked version **fails resolution**, because the lockfile enforces the lower locked version strictly.

This explains the otherwise surprising failure "my locked build failed when I declared a newer version": the lock is acting as an implicit strict constraint, not a suggestion. The declared version does not take precedence over the lock. Fix it with a reviewed `--write-locks` run to accept the new version — not by removing the lock or widening the declaration.

## DeepLocking Behavior Notes

- **`--refresh-dependencies` does not rewrite lock state.** It refreshes metadata; with locking, the lock remains authoritative. Use `--write-locks` only for an intentional, reviewed upgrade.
- **`--write-locks` is not a verification escape hatch.** Generating locks merely to make a failing verification pass hides a real resolution change. Regenerate locks through the intended dependency-update workflow and review the diff.
- **Lockfile format is version-sensitive.** Lockfile layout and behavior have changed across major versions; generate with the target wrapper and test the migration before committing a broad rewrite.

**Anti-pattern:** combining dynamic versions, `--refresh-dependencies`, and `--write-locks` in an unattended pipeline, or treating lock generation as a normal verification side effect.

## Generating and Releasing Locks

Enable locking globally with `dependencyLocking { lockAllConfigurations() }` (this locks project configurations; the buildscript classpath has its own lockfile and must be locked separately), and opt out of a specific configuration with `resolutionStrategy.deactivateDependencyLocking()`.

Generate or refresh lock state with `dependencies --write-locks`, which locks every resolvable, locking-enabled configuration — or use a custom `resolveAndLockAll` task that resolves exactly the set of resolvable configurations you care about (useful when only some configurations can be resolved on a given platform). Gradle writes lock state **only if the build succeeds**; a failed build persists no partial lock state.

- **Update selectively:** refresh or adjust lock state entries for specific dependencies rather than rewriting the whole lockfile.
- **Ignore specific dependencies:** exclude chosen dependencies from lock state when they must not be pinned.

Lockfile structure aids review diffs: each line is `group:artifact:version=configurations`, dependencies and configurations are listed alphabetically, and an explicit `empty=` line marks configurations that contain no dependencies.

## Locking Limitations

Dependency locking is for fixed versions, NOT changing versions (`-SNAPSHOT`). A changing dependency's content can change under identical coordinates — exactly what a lockfile cannot pin — so locking a changing dependency is a category error. Gradle warns when persisting lock state that contains changing dependencies; treat that warning as a misuse signal and remove the changing dependency from the locked graph rather than suppressing it.

## Locking vs Verification

A lockfile pins resolved versions for reproducibility; it is not proof that an artifact is trustworthy. Dependency verification (`verification-metadata.xml`) authenticates bytes and publisher identity. Neither replaces the other. For verification workflow, see [Dependency Verification](dependency-verification.md).

**More info:**
- Dependency locking: `gradle_docs(path="userguide/dependency_locking.md")`
- Dependency caching and refresh semantics: `gradle_docs(path="userguide/dependency_caching.md")`
- Preventing accidental dependency upgrades (`failOnVersionConflict`, strict constraints, locking): `gradle_docs(path="userguide/how_to_prevent_accidental_dependency_upgrades.md")`
- Locking basics (enable, generate, commit, CI): `authoring-gradle-builds`'s [Dependency Locking](../../authoring-gradle-builds/references/dependency-locking.md)
- Locking vs verification: [Dependency Verification](dependency-verification.md)
- Generation and lockfile commands: `gradle` with `--write-locks`; graph inspection via `inspect_dependencies`.
