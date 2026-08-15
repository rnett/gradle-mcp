# Capability: test-daemon-hygiene

## Purpose

Defines standards for nested-test Gradle daemon hygiene: one canonical daemon identity for test builds, short test-only idle timeouts, and guaranteed closure of every real Gradle provider created by tests.

## Requirements

### Requirement: Standardized nested-test daemon identity

The system SHALL standardize the daemon identity of nested test builds at the `withTestGradleDefaults` choke point in `TestGradleProvider.kt` by applying a single canonical JVM-arg set to all nested test builds and by filling in the launcher `javaHome` with the test-worker JDK (`System.getProperty("java.home")`) whenever the caller has not explicitly set one. Explicit invocation arguments SHALL take precedence over the canonical defaults: an explicit `GradleInvocationArguments.javaHome` SHALL NOT be overwritten by the defaults, and deliberately variant java-home or JVM-arg configurations SHALL remain confined to dedicated tests that assert that variant behavior.

#### Scenario: Nested build uses the test-worker JDK

- **WHEN** a test invokes Gradle through `withTestGradleDefaults` without an explicit `javaHome`
- **THEN** the launcher SHALL use `System.getProperty("java.home")` as the daemon `javaHome`
- **AND** the build SHALL NOT spawn a separate daemon pool from the inherited `JAVA_HOME` environment fallback

#### Scenario: Canonical JVM arguments for nested builds

- **WHEN** a nested test build runs with test defaults
- **THEN** it SHALL use the canonical JVM-arg set (e.g., `-Xmx256m`, `org.gradle.workers.max=2`)
- **AND** deliberately variant JVM-arg or java-home configurations SHALL remain confined to dedicated tests that assert that variant behavior

#### Scenario: Explicit javaHome overrides the canonical default

- **WHEN** a test passes an explicit `javaHome` through `withTestGradleDefaults`
- **THEN** the launcher SHALL use the explicit `javaHome`
- **AND** the canonical JVM-arg set SHALL still apply

#### Scenario: Fallback variant tests bypass the java-home default

- **WHEN** a dedicated test asserts environment or Tooling API `javaHome` fallback behavior
- **THEN** the test SHALL opt out of the java-home fill-in via the documented escape hatch on `withTestGradleDefaults`
- **AND** the canonical JVM-arg set SHALL still apply
- **AND** the fallback resolution SHALL land on the test-worker JDK so the daemon identity stays canonical

### Requirement: Test daemons self-expire

The system SHALL configure nested test builds with a short test-only daemon idle timeout of 60 seconds (60000ms) so idle test daemons stop themselves instead of lingering for Gradle's default multi-hour idle timeout.

#### Scenario: Idle test daemon exits after 60 seconds

- **WHEN** a nested test build finishes
- **AND** its daemon remains idle
- **THEN** the daemon SHALL self-terminate within 60 seconds (60000ms) of becoming idle

#### Scenario: Active test daemons are not interrupted

- **WHEN** a test daemon is actively running a build
- **THEN** the idle timeout SHALL NOT interrupt the running build

### Requirement: Real providers always closed

Tests SHALL close every real `GradleProvider` they create (via `use` or `finally`) so provider-owned build and coroutine scopes are released deterministically.

#### Scenario: Provider created in a test method

- **WHEN** a test creates a real `DefaultGradleProvider`
- **THEN** the test SHALL close it after use (e.g., `use {}` or `finally { provider.close() }`)
- **AND** the test SHALL NOT rely on `close()` to stop Gradle daemons, since provider close only cancels builds and the provider coroutine scope

#### Scenario: Class-scoped provider closed once at class teardown

- **WHEN** a fixture class shares one real `GradleProvider` across test methods
- **THEN** the class SHALL close that provider deterministically exactly once at class teardown (`@AfterAll`)
- **AND** per-method server teardown SHALL NOT close it beforehand

#### Scenario: Provider close disconnects the Gradle connector

- **WHEN** a `DefaultGradleProvider` is closed (directly, or via its `DefaultGradleConnectionService`)
- **THEN** the `GradleConnectionService` SHALL be closed
- **AND** `DefaultGradleConnectionService.close()` SHALL call `GradleConnector.disconnect()` on every connector it manages, i.e. one per project root it connected to
- **AND** a `disconnect()` failure SHALL NOT prevent the remaining connectors from being disconnected or the connector map from being cleared

#### Scenario: Deterministic cleanup on shutdown

- **WHEN** the server process shuts down
- **THEN** each `DefaultGradleProvider` SHALL be closed exactly once, stopping its running builds, cancelling its coroutine scope, and closing its `GradleConnectionService` (which disconnects its `GradleConnector`s)
- **AND** the provider SHALL register a JVM shutdown hook that performs this cleanup even if the provider was not closed explicitly
- **AND** repeated close attempts SHALL be idempotent and free of side effects
