## RENAMED Requirements

- `Java Home Configuration during Execution` -> `Java Home Selection Precedence`

## MODIFIED Requirements

### Requirement: Java Home Selection Precedence

The build execution process SHALL resolve the daemon JVM source before starting a build using this exact precedence: (1) an explicit `GradleInvocationArguments.javaHome`, (2) project daemon JVM settings, (3) `JAVA_HOME` from the resolved invocation environment, (4) the Tooling API default (no `setJavaHome` call).

- An explicit `javaHome` SHALL be terminal: when valid it is applied via `Launcher.setJavaHome` AND the same home SHALL be passed as an `org.gradle.java.home` build argument (`-Dorg.gradle.java.home=<home>`), so the explicit value wins over lower-priority `gradle.properties` values — including invalid ones, which Gradle validates before applying the Tooling API java-home request; when invalid the system SHALL warn and omit `setJavaHome`, and SHALL NOT consult daemon settings or environment `JAVA_HOME`.
- Project daemon JVM settings are exactly: `gradle/gradle-daemon-jvm.properties` with an effective `toolchainVersion`, and `org.gradle.java.home` in the effective Gradle user home's `gradle.properties` or the project root's `gradle.properties`. When any is present the system SHALL omit `setJavaHome` so Gradle's own daemon JVM selection applies.
- The distribution `gradle.properties` (`<GRADLE_HOME>`) is deliberately excluded from detection: the effective distribution is not reliably known before connection, and reading it could suppress environment `JAVA_HOME` for a setting Gradle would not apply.
- The effective Gradle user home SHALL be resolved before daemon-settings detection, from the following channels in this exact order, selecting the first non-blank value:
  1. the server process system property `gradle.user.home`;
  2. the server process environment variable `GRADLE_USER_HOME` (environment key matching SHALL be case-insensitive on Windows);
  3. the default `<user.home>/.gradle`.
  Blank or whitespace-only values SHALL be skipped with a diagnostic and the next channel consulted; the resolution SHALL be dependency-injected for testability. Invocation-level channels (`org.gradle.user.home` system properties and `GRADLE_USER_HOME` in the operation environment) were probed (V1) and removed because they do not reach the daemon's effective user home; Gradle's real client-side key is `gradle.user.home` (`org.gradle.user.home` is ignored).
- Environment `JAVA_HOME` SHALL be promoted only when no explicit `javaHome` was given and no daemon settings were detected; an invalid environment value SHALL warn and omit `setJavaHome`. On Windows, the environment `JAVA_HOME` lookup SHALL match keys case-insensitively.
- Settings files that exist but cannot be read or parsed SHALL be treated as settings present (fail-closed); only missing files fail open. Values SHALL be normalized with `java.util.Properties` semantics plus trimming; present-but-blank values SHALL also be treated as settings present (fail-closed) because Gradle rejects blank values (probe V2), and they produce a diagnostic.
- Detection helpers SHALL be pure; diagnostics are returned to and emitted by the caller.

#### Scenario: Explicit javaHome wins over settings and environment

- **WHEN** a build is initiated with a valid explicit `javaHome` while daemon settings and environment `JAVA_HOME` are also present
- **THEN** the launcher SHALL be configured with the explicit `javaHome` only

#### Scenario: Explicit javaHome wins over invalid lower-priority settings

- **WHEN** a build is initiated with a valid explicit `javaHome` while a lower-priority `gradle.properties` contains an invalid `org.gradle.java.home` value
- **THEN** the explicit home SHALL be applied via `Launcher.setJavaHome`
- **AND** the same home SHALL be passed as an `org.gradle.java.home` build argument, overriding the merged property value before Gradle's validation, so the build uses the explicit home

#### Scenario: Invalid explicit javaHome is terminal

- **WHEN** a build is initiated with an explicit `javaHome` that is not a valid directory
- **THEN** the system SHALL warn and omit `setJavaHome`
- **AND** SHALL NOT promote environment `JAVA_HOME` or consult daemon settings

#### Scenario: Daemon settings suppress environment promotion

- **WHEN** no explicit `javaHome` is given and any project daemon JVM setting is present
- **THEN** the system SHALL omit `setJavaHome` and defer to Gradle's daemon JVM selection

#### Scenario: Environment fallback preserved

- **WHEN** no explicit `javaHome` is given, no daemon settings are present, and environment `JAVA_HOME` is a valid directory
- **THEN** the launcher SHALL be configured with the environment `JAVA_HOME`

#### Scenario: Tooling API default

- **WHEN** no explicit `javaHome`, no daemon settings, and no usable environment `JAVA_HOME` exist
- **THEN** the system SHALL omit `setJavaHome` and let the Tooling API default apply

#### Scenario: Unreadable or unparseable settings file fails closed

- **WHEN** a daemon settings file exists but cannot be read or parsed
- **THEN** the system SHALL treat daemon settings as present, omit `setJavaHome`, and surface a diagnostic

#### Scenario: Effective Gradle user home resolution

- **WHEN** daemon-settings detection runs for a build without an explicit `javaHome`
- **THEN** the user-home `gradle.properties` SHALL be read from the user home resolved by the ordered channel list above
- **AND** a blank channel value SHALL be skipped with a diagnostic, falling through to the next channel
- **AND** with no channel value present anywhere, `<user.home>/.gradle` SHALL be used
