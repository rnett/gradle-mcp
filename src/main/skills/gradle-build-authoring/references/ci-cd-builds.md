# CI/CD Builds

Optimizing Gradle for CI/CD environments requires balancing build speed, reproducibility, and observability. CI environments differ from local development primarily by the lack of a persistent daemon and the need for clean-slate isolation.

## Daemon Management

The Gradle Daemon is designed for local development to speed up consecutive builds. In CI, a daemon can consume memory across pipeline stages or fail to shut down, leading to "zombie" processes.

### Disabling the Daemon
Use the `--no-daemon` flag in your CI pipeline scripts.

```yaml
# GitHub Actions example
- name: Run Tests
  run: ./gradlew test --no-daemon
```

Alternatively, set it globally in `gradle.properties`:
```properties
org.gradle.daemon=false
```

## Build Scans (`--scan`)

Build scans provide a deep, shareable record of a build's execution, including performance bottlenecks, dependency resolution, and failure analysis.

```bash
./gradlew build --scan
```

In CI, you can automate the acceptance of the Terms of Service:
```properties
# gradle.properties
system.prop.gradle.scan.terms.accepted=yes
```

## Parallel Execution and Resource Control

To maximize throughput in CI agents, enable parallel execution and constrain worker counts to avoid OOM (Out of Memory) errors.

### Parallelism
- `--parallel`: Allows Gradle to execute decoupled projects in parallel.
- `--max-workers`: Sets the maximum number of worker processes. Match this to the number of CPU cores available on your CI runner.

```bash
./gradlew build --parallel --max-workers=4
```

## Build Cache Configuration

The build cache allows CI to reuse outputs from previous builds, drastically reducing execution time.

### Local vs Remote Cache
- **Local Cache**: Stored on the runner. Useful if you use persistent runners (e.g., self-hosted GitHub Runners).
- **Remote Cache**: A shared server (e.g., Gradle Enterprise). Essential for ephemeral runners.

Enable the build cache in `settings.gradle.kts`:
```kotlin
buildCache {
    local {
        isEnabled = true
    }
    // remote<HttpApiCache> {
        // url = uri("https://cache.example.com")
        // credentials { ... }
    // }
}
```

## GitHub Actions Patterns

A standard high-performance Gradle setup in GitHub Actions usually involves the `gradle-build-action` for automated caching of the Gradle User Home.

```yaml
- uses: gradle/actions/setup-gradle@v3
  with:
    cache-read-only: false # Enable write access to cache on main branch

- name: Build with Gradle
  run: ./gradlew build --no-daemon --parallel
```

### CI-specific `settings.gradle.kts`
You can use environment variables to conditionally change project behavior in CI.

```kotlin
if (providers.environmentVariable("CI").isPresent) {
    // Disable heavy tasks or change logging levels for CI
}
```
