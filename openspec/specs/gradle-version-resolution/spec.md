# Capability: gradle-version-resolution

## Purpose

Provides centralized resolution of Gradle version aliases (like "current") to concrete version strings, with caching and fallback support.

## Requirements

### Requirement: Authoritative version resolution

The system SHALL provide a centralized mechanism to resolve Gradle version aliases (like `"current"`) to a specific, immutable version string.

#### Scenario: Resolve current to latest stable

- **WHEN** the user requests information for version `"current"`
- **THEN** the system SHALL fetch the latest stable version from `https://services.gradle.org/versions/current` and return its version string (e.g., `"8.6.1"`)

### Requirement: Version resolution caching

The system SHALL cache successful version resolutions for the lifetime of the server process, so a process performs a single fetch of the latest stable version and subsequent resolutions are served from the cache without further network requests. Failed resolutions SHALL NOT be cached, so later lookups can retry. Concurrent resolutions SHALL converge on the same cached value.

#### Scenario: Cached version resolution

- **WHEN** a version alias was previously resolved successfully
- **THEN** the system SHALL return the cached version string without making a network request

#### Scenario: Concurrent resolutions converge

- **WHEN** multiple resolutions of the same alias occur concurrently
- **THEN** the system SHALL return the same cached version string after the first successful resolution completes

#### Scenario: Failed resolutions are not cached

- **WHEN** a resolution attempt fails
- **THEN** the system SHALL NOT cache the failure
- **AND** a later resolution SHALL retry the network fetch instead of reusing the failed attempt

### Requirement: Version detection fallback

If the system fails to resolve the latest stable version via the network, it SHALL return the bundled Gradle version the server was built against, marked with a `BUNDLED_FALLBACK` provenance. Resolution SHALL NOT throw: callers always receive a usable version value. The fallback SHALL NOT be cached, so later lookups can retry the network fetch.

#### Scenario: Fallback during network failure

- **WHEN** the network is unavailable during resolution of `"current"`
- **THEN** the system SHALL return the bundled Gradle version the server was built against
- **AND** SHALL mark the result with a `BUNDLED_FALLBACK` provenance
- **AND** SHALL NOT throw

#### Scenario: Fallback does not suppress later retries

- **WHEN** a resolution fell back to the bundled version because of a network failure
- **THEN** a later resolution SHALL retry the fetch from `https://services.gradle.org/versions/current`
- **AND** SHALL return the live latest stable version once the fetch succeeds
