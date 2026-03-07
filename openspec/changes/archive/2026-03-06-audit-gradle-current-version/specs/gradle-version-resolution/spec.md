## ADDED Requirements

### Requirement: Authoritative version resolution

The system SHALL provide a centralized mechanism to resolve Gradle version aliases (like `"current"`) to a specific, immutable version string.

#### Scenario: Resolve current to latest stable

- **WHEN** the user requests information for version `"current"`
- **THEN** the system SHALL fetch the latest stable version from `https://services.gradle.org/versions/current` and return its version string (e.g., `"8.6.1"`)

### Requirement: Version resolution caching

The system SHALL cache resolved version aliases for a short period (e.g., 5-10 minutes) to avoid redundant network requests while ensuring responsiveness to version updates.

#### Scenario: Cached version resolution

- **WHEN** a version alias is resolved within the cache TTL
- **THEN** the system SHALL return the cached version string without making a network request

### Requirement: Version detection fallback

If the system fails to resolve an alias via the network, it SHALL fall back to the most recent cached version of that alias or a safe default.

#### Scenario: Fallback during network failure

- **WHEN** the network is unavailable during resolution of `"current"`
- **THEN** the system SHALL return the last known stable version string if available, or return `"current"` as a last-resort fallback with a warning
