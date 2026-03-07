## Context

The current implementation of `DefaultGradleDocsService` attempts to resolve the `"current"` Gradle version by scraping documentation pages. If this fails or if the input version is already `"current"`, it often defaults to the string
literal `"current"`. This string is then used to create cache directories (e.g., `~/.gradle-mcp/cache/reading_gradle_docs/current/`), which makes the cache non-deterministic because the actual version of Gradle referred to as `"current"`
changes over time.

## Goals / Non-Goals

**Goals:**

- Decouple the concept of an alias (like `"current"`) from the physical storage of documentation and source data.
- Implement a robust, centralized Gradle version resolution service.
- Ensure all cache and index paths use concrete, immutable version strings.
- Improve the reliability of documentation and source tools when operating without a specific version.

**Non-Goals:**

- Modifying the existing logic for detecting Gradle versions from project roots (via `gradle-wrapper.properties`), which is already functioning correctly.
- Performing a mass migration of existing `"current"` cache directories to concrete versions.

## Decisions

### 1. Introduce `GradleVersionService`

A new internal service will be created to handle all version-related logic.

- **Responsibility**: Resolve aliases like `"current"` or `"latest"` to concrete versions (e.g., `"8.6.1"`).
- **Authoritative Source**: Use `https://services.gradle.org/versions/current` (JSON) as the source of truth for the latest stable version. This is more robust than scraping HTML.
- **Caching**: The service will cache the resolved values in memory for the duration of the MCP server session, with an optional time-based refresh (e.g., every 4 hours) if needed for long-running sessions.

### 2. Mandatory Version Resolution in `DefaultGradleDocsService`

All entry points to documentation tools will call the `GradleVersionService` to resolve the version before proceeding.

- The literal string `"current"` will no longer be used as a key for `ensurePrepared` or in cache paths.
- If a user provides `"current"`, it is resolved to e.g., `"8.6.1"`, and the system checks for `.../8.6.1/` in the cache.

### 3. Graceful Fallbacks

If the resolution service is offline or unreachable:

- Attempt to use the most recent concrete version found in the local cache.
- If the cache is empty, fall back to a hardcoded "safe" version (e.g., the one the MCP server was built against or the latest known at dev time) and issue a warning.

## Risks / Trade-offs

- **[Risk] Network dependency for resolution** → [Mitigation] The resolution request to `services.gradle.org` is a small JSON fetch. We will cache it aggressively in memory and provide local fallbacks if the network is down.
- **[Risk] Storage overhead** → [Mitigation] While this might lead to more versioned directories in the cache over time as Gradle releases new versions, this is preferred over stale data. We can consider a cache cleanup task in a future
  roadmap item.
