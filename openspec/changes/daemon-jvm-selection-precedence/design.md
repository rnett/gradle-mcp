## Context

See `proposal.md` for the issue #238 motivation. `DefaultBuildExecutionService.configureLauncher` currently resolves `args.javaHome ?: env["JAVA_HOME"]` and calls `Launcher.setJavaHome` for a valid directory. A Tooling API Java-home request overrides Gradle's own daemon JVM selection, so promoting inherited or invocation environment `JAVA_HOME` can suppress project-owned daemon JVM criteria and `org.gradle.java.home`.

All build entry points converge on `configureLauncher`, and `RunningBuild.projectRoot` is available there. The invocation already exposes explicit `javaHome`, additional system properties, the resolved operation environment, and environment-source behavior. Connector creation is centralized in `DefaultGradleConnectionService`, which is also the control point for a deterministic Gradle user home in nested-build tests.

## Goals / Non-Goals

**Goals:**

- Make launcher Java-home selection a pure, testable decision with explicit ordering, terminal behavior, diagnostics, and fail-closed settings detection.
- Let Gradle retain authority over its daemon JVM chain whenever project daemon JVM settings exist.
- Preserve environment `JAVA_HOME` as an effective fallback when explicit input and project settings are absent.
- Isolate every nested-build test that reaches settings detection from the developer's real Gradle user home.
- Prove source selection separately from real-Gradle parity, because some correct and incorrect branches have the same observable daemon JVM.

**Non-Goals:**

- Changing REPL worker JVM selection or `renderCommandLine()` display semantics.
- Detecting `org.gradle.java.home` from a distribution-level `<GRADLE_HOME>/gradle.properties`.
- Caching settings detection across builds.
- Archiving or synchronizing this change before implementation and verification are complete.

## Decisions

### 1. Model selection as a terminal precedence decision

Add `DaemonJvmSelection.kt` with a stateless `DaemonJvmSettingsDetector`, `EffectiveGradleUserHomeResolver`, `DaemonJvmSelector`, result/diagnostic types, and a sealed decision representing explicit use, environment use, deferral to Gradle settings, Tooling API default, invalid explicit input, or invalid environment input.

The selector evaluates inputs in this order:

1. A non-null explicit `javaHome` is terminal. A path that exists and is a directory becomes `UseJavaHome(EXPLICIT)`. An invalid path becomes `InvalidExplicit`, emits a warning, omits `setJavaHome`, and performs no settings detection or environment fallback.
2. With no explicit value, detect project daemon JVM settings. Any effective or fail-closed source becomes `DeferToGradleSettings`, which omits `setJavaHome` and lets Gradle apply its own ordering.
3. With no detected settings, a valid environment `JAVA_HOME` becomes `UseJavaHome(ENVIRONMENT)`. An invalid value warns and omits `setJavaHome`.
4. With no usable source, choose `ToolingApiDefault` and omit `setJavaHome`.

`configureLauncher` becomes a thin mapper from the decision to `Launcher.setJavaHome`, INFO/WARN diagnostics, or no action. Environment-key lookup is case-insensitive on Windows. This replaces the current two-source fallback without changing `GradleInvocationArguments`.

**Alternative rejected:** Falling through after an invalid explicit value would silently replace a caller-selected JVM with a lower-priority environment value. The explicit channel must either be honored or left to Gradle.

### 2. Detect only settings Gradle can reliably apply to this project

Detection runs on every build without an explicit Java home and reads exactly these sources:

- `<projectRoot>/gradle/gradle-daemon-jvm.properties` with an effective `toolchainVersion`;
- `org.gradle.java.home` in `<effectiveGradleUserHome>/gradle.properties`;
- `org.gradle.java.home` in `<projectRoot>/gradle.properties`.

The detector answers whether Gradle has daemon JVM settings; it does not select among them. Gradle remains responsible for criteria precedence and user-home-over-project property precedence. `toolchainVendor` without `toolchainVersion` and `toolchainUrl.*` do not make criteria effective.

The distribution-level `<GRADLE_HOME>/gradle.properties` is excluded because the effective wrapper distribution is resolved later by the Tooling API. Consulting a potentially unrelated `GRADLE_HOME` could suppress a valid environment fallback for a setting the build never sees.

### 3. Normalize properties and fail closed on unreadable settings

Parse settings with `java.util.Properties.load` semantics, trim candidate values, and treat missing keys as ineffective. Probe V2 showed Gradle rejects present-but-blank values (`Value '' given for toolchainVersion is an invalid Java version`; `Value '' given for org.gradle.java.home Gradle property is invalid`), so blank values are treated as settings present (fail-closed) with a diagnostic, not as absent. A missing file is absent, while an existing file that cannot be read or parsed is treated as settings present and returns a warning diagnostic. This prevents environment promotion from masking configuration Gradle is about to reject.

The detector and user-home resolver perform no logging. They return diagnostics to `configureLauncher`, which owns emission. Process system properties and environment access are injected into the resolver so tests do not depend on hidden process state.

### 4. Resolve the effective Gradle user home from request-specific inputs first

The resolver selects the first non-blank channel in the final probe-V1 order: the server process system property `gradle.user.home`, the server process environment variable `GRADLE_USER_HOME`, then `<user.home>/.gradle`. Blank channels are skipped with diagnostics. Windows environment-key matching is case-insensitive (provided natively by `System.getenv`).

Probe V1 (completed) measured the invocation-level channels against `gradle.gradleUserHomeDir`: both the invocation `org.gradle.user.home` system property and operation-environment `GRADLE_USER_HOME` fail to reach the daemon's effective user home, so they were removed (removal-only adjustment, as specified). The probe also exposed that Gradle's real client-side key is `gradle.user.home`; the historical `org.gradle.user.home` key is ignored (latent defect), so the resolver uses `gradle.user.home`. Fixture injection was narrowed to the connector channel (`GradleConnector.useGradleUserHomeDir`) plus the resolver's injected server-process inputs.

Probe V2 (completed) ran blank `toolchainVersion` and blank `org.gradle.java.home` directly against Gradle 9.6.1: Gradle rejects both (`Value '' given for toolchainVersion is an invalid Java version`; `Value '' given for org.gradle.java.home Gradle property is invalid`), so detection fails closed — a present-but-blank value is settings present with a diagnostic, not absent.

### 5. Isolate nested-build tests with one controlled user home

Extend `DefaultGradleConnectionService` with an optional `gradleUserHome: Path?` and call `GradleConnector.useGradleUserHomeDir` when present. Forward the same optional path through the test-facing `DefaultGradleProvider` constructor. Production dependency injection keeps the no-argument connection-service behavior.

Add `TestGradleUserHome` and a `testGradleProvider` fixture factory. The fixture pins the connector and injects the same controlled path into both invocation channels U1 and U2 until V1 narrows the surviving set. Migrate `GradleProviderTest` as a class because its no-explicit-Java-home cases reach detection. Keep distribution downloads efficient by junctioning each controlled home's wrapper distributions to a stable build-owned shared distributions directory configured for both unit and integration test tasks.

### 6. Use two verification tiers

Tier 1 source-selection tests exercise the pure selector and launcher mapper with stubbed detection and assert the exact decision plus the exact `setJavaHome` call or omission. They cover detector, resolver, terminal explicit input, settings deferral, environment promotion, invalid values, Tooling API default, normalization, and fail-closed behavior.

Tier 2 functional tests run real nested builds and parse a `printJavaHome` task marker, comparing canonical paths. Valid-directory non-JDK sentinels make wrongful launcher selection fail loudly. A regular-file fixture proves invalid explicit input. The T1/T1b-T6 matrix covers explicit precedence over settings and environment (T1), explicit precedence over valid daemon criteria (T1b), user-home-over-project settings (T2), criteria settings (T3), environment parity (T4), invalid-explicit terminal behavior (T5), and malformed-settings parity (T6). Existing `GradleProviderTest` coverage supplies end-to-end parity for the no-settings/no-environment Tooling API default branch.

Probe V-T6 (completed) showed `setJavaHome` does not bypass malformed criteria-file parsing: the enforcement argument overrides only the merged `org.gradle.java.home` property value before validation, and an unparseable criteria file aborts the build regardless of the java-home source. T6's end-to-end claim is therefore parity-only — with env `JAVA_HOME` present and no explicit value, the fail-closed deferral omits `setJavaHome` and surfaces Gradle's own criteria error; source-selection fail-closed remains proven by Tier 1.

### 7. Keep tool metadata aligned with behavior

Update the `javaHome` KDoc and `@Description` to state the complete precedence and invalid-explicit behavior, then run `:updateToolsList` so generated execution-tool documentation matches the schema. No tool field is added or removed.

### 8. Enforce an explicit javaHome as a build argument

An EXPLICIT `UseJavaHome` decision is applied twice: `Launcher.setJavaHome(home)` plus the same home as a raw `-Dorg.gradle.java.home=<home>` launcher argument. Gradle converts `org.gradle.java.home` properties into daemon JVM criteria before applying the Tooling API java-home request and eagerly validates them, so a lower-priority invalid `gradle.properties` value would abort daemon selection before the Tooling API request is honored. The raw `-D` argument overrides the merged property value before that validation, and `setJavaHome` is applied last, so the pair is safe.

This uses the same client-side `-D` launcher-argument channel as the in-repo `TEST_DAEMON_IDLE_TIMEOUT_ARG` precedent (`TestGradleProvider.kt`): `ProviderConnection.initParams` feeds initial `-D` arguments into `DaemonBuildOptions` AFTER the user-home gradle.properties conversion. Enforcement is EXPLICIT-only — an ENVIRONMENT-origin `UseJavaHome` yields no argument, so environment promotion never overrides a project's daemon JVM settings. The enforcement argument precedes `allAdditionalArguments` in the launcher argument list so a user-supplied `-Dorg.gradle.java.home` in `additionalArguments` still wins on conflict.

## Risks / Trade-offs

- **Risk:** The server and daemon could resolve different Gradle user homes. -> **Mitigation:** V1 measures U1/U2 against `gradle.gradleUserHomeDir`; only proven channels remain in implementation, fixtures, and the archived spec.
- **Risk:** A developer's real user-home properties could make tests machine-dependent. -> **Mitigation:** Pin both the Tooling API connector and resolver-visible invocation inputs to one controlled home.
- **Risk:** Functional tests can pass when two branches select the same JVM. -> **Mitigation:** Treat Tier 1 decision and launcher-interaction tests as authoritative source-selection proof; use functional tests only for end-to-end parity.
- **Risk:** A malformed file could be hidden by environment fallback. -> **Mitigation:** Existing but unreadable files fail closed and defer error handling to Gradle.
- **Trade-off:** Detection reads two or three small files on every applicable build. Correctness under changing project settings is preferred over caching.
- **Trade-off:** Distribution-only `org.gradle.java.home` does not suppress environment promotion. This avoids false positives from an unknown or unrelated distribution home.

## Migration Plan

1. Run and record probes V1, V2, and V-T6; apply only the bounded adjustments described above.
2. Add selection helpers and Tier 1 tests without changing launcher behavior.
3. Rewire launcher selection, dependency injection, controlled-user-home fixtures, and Tier 2 tests.
4. Update tool metadata and regenerate tool documentation.
5. Run unit, integration, documentation, and full quality gates.
6. After implementation is merged and verified, archive the change with spec synchronization and fold V1's final channel list into the main `build-execution` requirement.

Rollback restores the previous `configureLauncher` Java-home block and removes the new injected collaborators and test-only user-home plumbing. The OpenSpec change is not archived until all verification gates pass.

## Open Questions

None remaining — all implementation-time probes were resolved during implementation and are recorded in the Decisions and Verification sections:

- **V1 (resolved):** Invocation `org.gradle.user.home` and operation-environment `GRADLE_USER_HOME` do not determine `gradle.gradleUserHomeDir`; both channels were removed (removal-only adjustment), and the resolver uses Gradle's real client-side key `gradle.user.home`.
- **V2 (resolved):** Gradle rejects blank `toolchainVersion` and blank `org.gradle.java.home` (`Value '' given for toolchainVersion is an invalid Java version`; `Value '' given for org.gradle.java.home Gradle property is invalid`), so detection fails closed with a diagnostic instead of treating blanks as absent.
- **V-T6 (resolved):** `Launcher.setJavaHome` does not bypass malformed daemon criteria parsing; an unparseable criteria file aborts the build regardless of the java-home source, so T6 is documented as parity-only.

## Verification

Recorded 2026-08-05 during apply finalization. Production code, tests, and fixtures for decision D-19 were implemented and compiled by a prior worker; this session re-ran the suites, ran the gates, and finalized the artifacts.

- **Focused suites (task 5.1):** `DaemonJvmSelectionTest` 26/26 pass; `DaemonJvmPrecedenceFunctionalTest` T1, T1b, T2-T6 all pass; migrated `GradleProviderTest` 15/16 pass with only the pre-existing CI-gated skip (`captures published build scans when enabled`).
- **T1/T1b claim updates:** T1's discrimination chain was strengthened: the losing sources are non-JDK sentinel directories (F1 = project `org.gradle.java.home`, F2 = env `JAVA_HOME`), so a wrongful selection fails loudly, and the claim now covers explicit winning over INVALID lower-priority settings via the enforcement argument. T1b was added: explicit wins over VALID daemon criteria (`toolchainVersion=99`) because the `-Dorg.gradle.java.home` enforcement argument creates an `org.gradle.java.home` daemon criterion that outranks `toolchainVersion` criteria.
- **Gates:** `:updateToolsList` and `:verifyToolsList` pass; `:test` 487 passed / 2 skipped / 0 failed; `:integrationTest` 96/96 passed / 0 failed — the previously-known REPL failures did not reproduce.
