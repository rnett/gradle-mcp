<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Dependency Locking

Dependency locking ensures that the exact version of every transitive dependency is recorded, preventing "non-deterministic" builds where a dependency update in a remote repository breaks your build without any changes to your own code.

## Enabling Dependency Locking

Dependency locking is enabled per-configuration. Usually, this is applied to `runtimeClasspath` and `compileClasspath`.

```kotlin
configurations.all {
    resolutionStrategy.activateDependencyLocking()
}
```

Alternatively, you can target specific configurations:
```kotlin
configurations.runtimeClasspath {
    resolutionStrategy.activateDependencyLocking()
}
```

## Generating Lock Files

Once locking is enabled, you must generate the lock files. Gradle creates a `.lockfile` for each locked configuration in the project directory.

### Generating Locks
Run the build with the `--write-locks` flag.

```bash
./gradlew dependencies --write-locks
```

This command resolves all dependencies and writes the exact versions to files like `gradle/dependency-locks/runtimeClasspath.lockfile`. You must commit these files to version control.

## CI Verification

In CI, you want the build to fail if the resolved dependencies differ from the lock files, rather than automatically updating them.

### Verifying Locks
By default, if a lock file exists, Gradle will use it. If the lock file is missing or doesn't match a strict constraint, the build fails.

### Updating Locks in CI
If you intentionally want to update dependencies as part of a PR, you can run:

```bash
./gradlew dependencies --write-locks
```

Or, use the `--lock-file-update` flag to update only the specific locks required by changes in the build script.

## Maintenance and Updating

Locks eventually become stale. Update them periodically to pull in security patches and new versions.

1. Update the version in `libs.versions.toml`.
2. Run `./gradlew dependencies --write-locks` to refresh the lock files.
3. Commit the updated `.lockfile` items along with the version change.

### Lock File Structure
Lock files are simple text files containing a list of GAV (Group, Artifact, Version) coordinates. They are not human-editable; they should always be managed by Gradle.
