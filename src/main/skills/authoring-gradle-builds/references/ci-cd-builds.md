# CI/CD Builds

Author CI builds as reproducible, diagnosable invocations of the checked-in Gradle wrapper. CI is not a separate build definition: it is another environment that must consume declared inputs, the same dependency graph, and the same task outputs.

## Non-negotiable defaults

- Run `gradlew`/`gradlew.bat`, never a globally installed `gradle`. Keep `gradle/wrapper/gradle-wrapper.properties` and the wrapper JAR under version control, and pin the distribution URL to the intended version.
- Set `GRADLE_USER_HOME` to a job-owned, cacheable directory. Cache dependency artifacts, the wrapper distribution, and (when policy permits) the local build cache; do not share a writable Gradle User Home concurrently between unrelated jobs.
- Enable the build cache with `--build-cache` or `org.gradle.caching=true`. Prefer a remote cache for ephemeral runners and a local cache for persistent runners; make remote cache writes trusted and avoid publishing outputs from untrusted pull requests.
- Enable the configuration cache with `--configuration-cache` or `org.gradle.configuration-cache=true` after the build and its plugins pass compatibility checks. Treat the cache as a separate optimization from the build cache.
- Keep the daemon enabled unless the CI provider already scopes and cleans the process. Use `--no-daemon` for genuinely ephemeral jobs where process lifetime and cleanup matter more than warm-build performance.
- Add `--stacktrace` to the normal CI command. Add `--continue` when the objective is a failure inventory across independent tasks, not when the first failure should stop the job.

The generated best-practices corpus owns the rationale for cache and shared-property defaults. See [Use the Build Cache](best-practices/use-the-build-cache.md), [Use the Configuration Cache](best-practices/use-the-configuration-cache.md), and [Set Build Flags in `gradle.properties`](best-practices/set-build-flags-in-gradle-properties.md). Do not duplicate those entries; use this reference for CI-specific authoring constraints.

## Wrapper and Gradle User Home

The wrapper makes the Gradle runtime reproducible, but it does not make the environment reproducible by itself. Pin the JDK, use Gradle toolchains for compilation, and make repository declarations, dependency locking, and credentials explicit. A CI cache key should include at least the wrapper distribution, OS/architecture, JDK/toolchain identity, dependency lockfiles, and relevant build scripts.

Use a job-local `GRADLE_USER_HOME` such as `.gradle-user-home` under the runner workspace or a provider-managed cache directory. Preserve it between jobs only through the CI provider's cache mechanism. Never place credentials in the cached directory or print its contents.

**Anti-patterns**:

- Running `gradle` from the runner image and allowing the image to choose the Gradle version.
- Sharing one writable `GRADLE_USER_HOME` among parallel jobs.
- Caching only `build/`, which is task output local to one checkout and is not a substitute for the Gradle User Home or build cache.
- Using `--refresh-dependencies` on every build. Use it only for intentional freshness diagnosis.

## Build cache in CI

The build cache reuses task outputs when Gradle computes the same task inputs. It cannot repair tasks with undeclared inputs, unstable outputs, machine-specific paths, timestamps, network reads, or mutable global state. Make custom tasks cacheable and model every output-affecting value before expecting cache hits.

Enable the local cache in `settings.gradle.kts` when the build needs an explicit settings-level policy:

```kotlin
buildCache {
    local {
        isEnabled = true
    }
}
```

Use `--build-cache` for a CI invocation when the policy should be selected by the pipeline. Use a remote cache only with an authenticated, trusted endpoint and a documented read/write policy. Do not add a made-up remote cache type or endpoint to a build definition: configure the provider-supported cache backend in settings and verify its credentials separately.

**Anti-patterns**:

- Treating a cache hit as proof that a task action ran.
- Enabling remote writes for untrusted code.
- Adding absolute workspace paths, hostnames, current time, or undeclared environment values to cacheable task outputs.
- Disabling cacheability instead of declaring missing inputs and outputs.

## Configuration cache in CI

The configuration cache stores the configured task graph, while the build cache stores task outputs. Enable both when the build is compatible. Test the exact CI task set, because one incompatible plugin or task can prevent configuration-cache reuse even when ordinary local builds succeed.

Keep CI conditionals configuration-cache-safe. Read `CI`, branch names, feature flags, and other environment-backed values through declared providers or a `ValueSource`; do not call `System.getenv`, `System.getProperty`, arbitrary file APIs, or external commands during configuration without modeling the input. If the value affects a task, wire a `Property` or `Provider` into that task rather than branching on an eagerly realized value.

See [Advanced Configuration](advanced-configuration.md) for service injection, providers, and the verified `ValueSource` pattern. Configuration-cache reuse is invalidated when relevant declared inputs change; hidden environment reads can instead reuse stale configuration.

**Anti-patterns**:

- Assuming `if (providers.environmentVariable("CI").isPresent) { ... }` alone makes all work inside the branch cache-safe.
- Calling `.get()` on a provider while configuring unrelated tasks.
- Retaining `Project`, `Task`, or live model objects for execution-time callbacks.
- Treating a successful first CI build as evidence that subsequent builds will reuse the configuration cache.

## Diagnostics and failure policy

Use `--stacktrace` on every CI verification command. Use `--info` for cache, configuration-cache, or task-input diagnosis; reserve `--debug` for a deliberately captured diagnostic job because it is noisy. Use `--continue` to collect independent failures, then rerun the root failure without it so dependency failures are not obscured.

Keep diagnostics inside the CI log and artifact policy. Do not add `--scan` automatically: a build scan publishes build metadata and may require Terms of Service acceptance. Use it only when publication is authorized; otherwise use local logs, test reports, and the Gradle MCP execution tools.

## Daemon and resource policy

A daemon is useful on persistent runners and for several Gradle invocations in one job. `--no-daemon` is reasonable for a single invocation on a disposable runner, but it does not turn Gradle into a low-memory process: compilation, workers, and test forks still consume resources. Set `--max-workers` from the runner's actual capacity rather than assuming the host CPU count.

Do not encode `org.gradle.daemon=false` globally merely because one CI provider is ephemeral. Keep the default in the pipeline command or provider configuration unless every environment should use the same policy.

## Version notes

- **Gradle 9.x:** Bias to the latest supported 9.x minor. Configuration cache is stable and remains opt-in; validate third-party plugins and use the current wrapper's diagnostics. Prefer the current cache and provider APIs.
- **Gradle 8.x:** Configuration cache is stable from 8.1 onward. Gradle 8.0 and older plugin combinations require explicit compatibility testing. Build-cache and wrapper practices are the same.
- **Gradle 7.x:** Use the wrapper and explicit cache testing, but treat configuration cache as experimental or migration work rather than a universal CI default. On 7.x, fall back to ordinary providers and explicit task inputs when newer configuration-cache APIs or plugin support are unavailable.

**More info**:

- Wrapper and CI environment: `gradle_docs(path="userguide/gradle_wrapper.md")`
- Build cache: `gradle_docs(path="userguide/build_cache.md")`
- Configuration cache requirements: `gradle_docs(path="userguide/configuration_cache_requirements.md")`
- Gradle properties and build environment: `gradle_docs(path="userguide/build_environment.md")`
- CI execution and diagnostics: `gradle` and build lookup via `query_build`/`wait_build`
