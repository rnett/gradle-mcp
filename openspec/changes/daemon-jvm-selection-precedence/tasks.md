## 1. Phase 0: OpenSpec and Implementation-Time Probes

- [x] 1.1 Create and validate the OpenSpec proposal, design, task plan, and `build-execution` delta for daemon JVM selection precedence.
- [x] 1.2 Run probe V1 against Gradle 9.4.1 to verify whether invocation `org.gradle.user.home` and operation-environment `GRADLE_USER_HOME` each determine `gradle.gradleUserHomeDir`; record evidence in `design.md`, remove only disproven channels, and narrow test-fixture injection to the surviving channels. (Completed: both invocation channels disproven and removed; resolver uses the real client-side key `gradle.user.home`.)
- [x] 1.3 Run probe V2 for blank `toolchainVersion` and `org.gradle.java.home`; record whether Gradle treats each blank as absent or erroneous, retaining blank-as-ineffective unless evidence requires fail-closed handling. (Completed: Gradle rejects both blanks, so detection fails closed with a diagnostic.)
- [x] 1.4 Run probe V-T6 to determine whether `Launcher.setJavaHome` bypasses malformed daemon criteria parsing; record the result and adjust only T6's end-to-end parity claim. (Completed: it does not bypass; T6 is documented as fail-closed parity only.)

## 2. Phase 1: Selection Helpers and Tier 1 Tests

- [x] 2.1 Add `src/main/kotlin/dev/rnett/gradle/mcp/gradle/build/DaemonJvmSelection.kt` with the setting-source, detection, resolved-user-home, and sealed decision types from `design.md`.
- [x] 2.2 Implement `EffectiveGradleUserHomeResolver` with the final V1 channel list, first-non-blank selection, blank diagnostics, dependency-injected server process inputs, `<user.home>/.gradle` fallback, and case-insensitive Windows environment lookup.
- [x] 2.3 Implement `DaemonJvmSettingsDetector` for effective `toolchainVersion` criteria and user-home/project `org.gradle.java.home`, using `java.util.Properties` normalization, missing-file fail-open behavior, existing-file read-or-parse-failure fail-closed behavior, and returned diagnostics.
- [x] 2.4 Implement `DaemonJvmSelector` with terminal explicit input, settings deferral, environment `JAVA_HOME` fallback, invalid-value decisions, and Tooling API default behavior; do not cache file detection.
- [x] 2.5 Add `src/test/kotlin/dev/rnett/gradle/mcp/gradle/build/DaemonJvmSelectionTest.kt` and cover detector/resolver/selector cases D1-D18, including source exhaustiveness, U1-U5 precedence, blank channels, Windows key matching, normalization, explicit terminal behavior, environment fallback, and Tooling API default. Prove fail-closed behavior separately for an unreadable-file read failure and malformed-properties parse failure, and prove selector fallback from a mixed-case Windows `JAVA_HOME` key.
- [x] 2.6 Add launcher-mapping interaction cases L1-L6 that assert the exact `setJavaHome` path or its omission and the required INFO/WARN diagnostics for every decision branch, including that the mixed-case Windows `JAVA_HOME` selection results in the corresponding `setJavaHome` action.
- [x] 2.7 Rename the resolver system-property key from `org.gradle.user.home` to `gradle.user.home` — the key Gradle actually reads (`org.gradle.user.home` is ignored; latent defect).

## 3. Phase 2: Launcher Integration and Isolated Functional Tests

- [x] 3.1 Inject `DaemonJvmSelector` through `src/main/kotlin/dev/rnett/gradle/mcp/DI.kt` and `DefaultBuildExecutionService`, then replace the current `args.javaHome ?: env["JAVA_HOME"]` block in `BuildExecutionService.kt` with the thin `configureJavaHome` decision mapper.
- [x] 3.2 Extend `DefaultGradleConnectionService` in `GradleConnectionService.kt` and the test-facing `DefaultGradleProvider` constructor in `GradleProvider.kt` with a defaulted optional Gradle user home; apply it through `GradleConnector.useGradleUserHomeDir` without changing production defaults.
- [x] 3.3 Configure `GRADLE_MCP_TEST_SHARED_DISTS_DIR` for unit and integration tests in `build.gradle.kts`, and expose the stable build-owned directory through `SharedTestInfrastructure.sharedDistsDir`.
- [x] 3.4 Add `src/testFixtures/kotlin/dev/rnett/gradle/mcp/fixtures/gradle/TestGradleUserHome.kt` to create a controlled temporary home whose wrapper distributions use the shared distributions directory.
- [x] 3.5 Extend both `withTestGradleDefaults` overloads in `TestGradleProvider.kt` and add `testGradleProvider(...)` so the connector and resolver-visible invocation channels receive the same controlled home, using only channels retained by V1.
- [x] 3.6 Migrate `GradleProviderTest.kt` to a class-scoped controlled Gradle user home, including the inline `DefaultGradleProvider` constructions, while preserving assertions and the no-explicit-Java-home coverage for environment and Tooling API fallback.
- [x] 3.7 Add the `printJavaHome` nested-build probe task through `GradleProjectFixture.kt` or the functional test fixture and compare canonical `Path.toRealPath()` values from the marker output.
- [x] 3.8 Add `src/test/kotlin/dev/rnett/gradle/mcp/gradle/DaemonJvmPrecedenceFunctionalTest.kt` with T1-T6 for explicit-valid precedence, user-home-over-project settings, daemon criteria, environment-promotion parity, invalid-explicit terminal behavior, and fail-closed malformed settings.
- [x] 3.9 Use existing valid-directory non-JDK sentinels for losing sources and a regular-file invalid-explicit fixture so wrongful `setJavaHome` calls fail loudly; document T4 as parity-only and make T6's assertion follow V-T6 evidence.
- [x] 3.10 Return `JavaHomeConfiguration` (diagnostics + enforcement argument) from `configureJavaHome` and assemble the launcher arguments as initScripts + `listOfNotNull(javaHomeArgument)` + `allAdditionalArguments`, so the explicit `-Dorg.gradle.java.home=<home>` argument precedes any user-supplied override.
- [x] 3.11 Narrow fixture injection to the surviving V1 channels: drop `gradleUserHome`/`userHomeChannels` injection from both `withTestGradleDefaults` overloads; keep `testGradleProvider(gradleUserHome=...)` for connector pinning.
- [x] 3.12 Strengthen the T1 comment to cover explicit-wins-over-INVALID-lower-priority settings and add T1b (explicit wins over valid daemon criteria) to `DaemonJvmPrecedenceFunctionalTest`.

## 4. Phase 3: Tool Metadata and Generated Documentation

- [x] 4.1 Update the `javaHome` KDoc and `@Description` in `src/main/kotlin/dev/rnett/gradle/mcp/gradle/GradleArgs.kt` to document explicit terminal precedence, project settings, environment fallback, and Tooling API default behavior.
- [x] 4.2 Run `:updateToolsList` and verify the regenerated `docs/tools/EXECUTION_TOOLS.md` reflects the new `javaHome` contract without unrelated generated changes.
- [x] 4.3 Run `:verifyToolsList` to confirm tool metadata and generated documentation are synchronized.
- [x] 4.4 Update the spec/design wording for the final V1 channel set, V2 fail-closed blanks, V-T6 findings, and the enforcement clause (spec bullet + scenario, design Decision 8 + Verification section).

## 5. Phase 4: Verification Gates

- [x] 5.1 Run the focused `DaemonJvmSelectionTest`, `DaemonJvmPrecedenceFunctionalTest`, and migrated `GradleProviderTest` suites and inspect all failures. (All green: 26/26, T1/T1b/T2-T6, 15/16 with only the pre-existing CI-gated skip.)
- [x] 5.2 Run `:test` and confirm the full unit-test suite passes.
- [x] 5.3 Run `:integrationTest`; confirm all relevant integration tests pass and document only the two accepted pre-existing REPL failures (`JavaReplIntegrationTest.initializationError` and `KotlinReplIntegrationTest.basic execution works()`) if they remain reproducible.
- [x] 5.4 Run `:check`, including OpenSpec/tool-document verification, and resolve every regression attributable to this change.
- [x] 5.5 Confirm machine independence: every nested build that can reach settings detection uses the controlled Gradle user home, while builds carrying explicit test defaults terminate before detection.
- [x] 5.6 Re-run the gates after artifact finalization (`:updateToolsList`, `:verifyToolsList`, `:test`, `:integrationTest`) and confirm they still pass.

## 6. Phase 5: OpenSpec Archive and Spec Synchronization

- [x] 6.1 Before archive, update `design.md` with V1, V2, and V-T6 findings and update the delta's U1-U5 list to the final V1-proven removal-only channel set. (Completed: final 3-channel set + Decision 8 + Verification section.)
- [x] 6.2 Revalidate the completed OpenSpec change strictly after all implementation evidence and bounded probe adjustments are recorded.
- [ ] 6.3 After implementation is merged and all gates pass, archive `daemon-jvm-selection-precedence` with spec synchronization so the malformed `Java Home Configuration during Execution` requirement is replaced by `Java Home Selection Precedence` in the main `build-execution` spec.
