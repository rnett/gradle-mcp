## Why

Currently, the Gradle documentation and source querying tools use the string literal `"current"` as a version name when no specific version is provided or detected. This causes problems because:

1. The `"current"` Gradle version is a moving target (e.g., from 8.6 to 8.7), but the cache directory `.../current/` remains static once created, leading to stale documentation.
2. The `DistributionDownloaderService` might fail or download incorrect assets if it attempts to use `"current"` as a literal version in download URLs.
3. It makes the system non-deterministic and difficult to debug when the underlying version changes.

## What Changes

- **Update Version Resolution**: Modify `DefaultGradleDocsService` to always resolve `"current"` (or `null` when it falls back to current) to a concrete, immutable version string (e.g., `"8.6.1"`) before performing any caching or indexing
  operations.
- **Robust Version Lookup**: Implement a robust mechanism to fetch the latest stable Gradle version from authoritative sources (e.g., `https://services.gradle.org/versions/current`).
- **Cache Normalization**: Ensure that all cache paths (docs, sources, indexes) use concrete versions, making the cache naturally immutable for a given version.
- **User Feedback**: Update tool outputs to clearly show which concrete version was resolved from `"current"`.

## Capabilities

### New Capabilities

- `gradle-version-resolution`: A new capability for authoritative resolution of Gradle versions (latest stable, release candidates, etc.).

### Modified Capabilities

- `gradle-docs-querying`: Update the documentation querying logic to use resolved concrete versions for all operations.
- `gradle-internal-research`: Ensure that source-level research and internal API access also benefit from concrete version resolution.

## Impact

- **Cache Integrity**: Eliminates the stale `"current"` cache issue. Each version will have its own independent, immutable cache.
- **Predictability**: Tools will always operate on a specific version of the documentation/sources, even when "current" is requested.
- **Initial Latency**: Resolving "current" may require a single, lightweight network request to `services.gradle.org`, which can be cached for a short period.
- **Storage**: Increased disk usage as multiple versions of Gradle docs might be cached (e.g., if "current" changes), but this is desirable for correctness.
