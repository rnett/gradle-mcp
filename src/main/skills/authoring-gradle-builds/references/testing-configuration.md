# Testing Configuration

Configure the test model in build definitions. This reference covers framework selection, reporting, permanent test selection, Kotlin Multiplatform (KMP) target configuration, and reusable test fixtures. Hand off test execution, task discovery, `--tests`, failure diagnosis, and report inspection to `using-gradle`.

## Defaults and boundaries

- **Default:** Use Kotlin DSL and configure the actual test task or KMP test run that owns the tests.
- **Default:** Make framework selection explicit. For JUnit 5, call `useJUnitPlatform()` on every applicable JVM test task or test run.
- **Default:** Keep persistent selection in the build script only when the selected subset is part of the build contract. Use CLI `--tests` for an ad-hoc run, and hand that run to `using-gradle`.
- **Default:** Log failed tests with full exceptions in local and CI diagnostics; enable standard streams only when the output is intentional and bounded.
- **Default:** Share stable test utilities through `java-test-fixtures`, not by depending on another module's private test source set.
- **Anti-pattern:** Assume `tasks.test` configures every test task. KMP, Android, custom source sets, and JVM target plugins can create separate tasks.
- **Anti-pattern:** Put execution commands, retry loops, or failure triage in this authoring reference. Those are `using-gradle` concerns.
- **This is prohibited:** Use `afterEvaluate` to find test tasks. React to plugin application and use lazy task APIs instead.

**Version notes:** The APIs in this reference are available across Gradle 7, 8, and 9, but the surrounding test plugin and Kotlin Gradle Plugin APIs are version-sensitive. Bias new authoring toward the current Gradle 9.x behavior, verify the wrapper version first, and retain the 7.x fallbacks below where noted.

## JUnit Platform setup

JUnit Jupiter and other JUnit 5 engines require the JUnit Platform test framework. Configure it explicitly:

```kotlin
plugins {
    java
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
```

Without `useJUnitPlatform()`, Gradle does not select the JUnit Platform for that `Test` task. JUnit 5 tests can therefore be undiscovered, commonly producing a successful task with zero executed tests, or the task can use a different configured test framework. Do not interpret a green zero-test result as proof that JUnit 5 ran.

Configure all relevant JVM test tasks when the build has more than the conventional `test` task:

```kotlin
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

Use `tasks.withType<Test>().configureEach` only when every `Test` task in the project should use JUnit Platform. Otherwise target named tasks explicitly, so a legacy or framework-specific task is not changed accidentally. If a plugin applies later, configure its tasks from the plugin callback rather than relying on application order:

```kotlin
pluginManager.withPlugin("java") {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
```

**Anti-patterns:**

- Declare a JUnit 5 dependency and omit `useJUnitPlatform()`.
- Configure only `tasks.test` in a project with custom JVM test suites.
- Mix JUnit 4 and JUnit 5 assumptions without selecting the intended engine and platform.
- Use `tasks.create` or eagerly realize every test task just to apply framework configuration.

**Version notes:** `useJUnitPlatform()` and `Test` task framework selection are stable in Gradle 7, 8, and 9. For Gradle 7.x builds, preserve the same explicit call; do not rely on a plugin's implicit defaults. The JUnit and Kotlin Gradle Plugin versions are independent compatibility decisions and must be checked against the project's version catalog and wrapper.

**More info:**

- Gradle test tasks and JUnit Platform: `gradle_docs(path="userguide/java_testing.md")`
- Test execution and zero-test verification: hand off to `using-gradle/references/testing.md`; `query_build` documentation

## Test logging

Configure logging on the owning `Test` task. Use event selection for concise progress, `exceptionFormat` for actionable failures, and `showStandardStreams` only when test output is part of the diagnostic contract:

```kotlin
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        showStandardStreams = false
    }
}
```

`events` controls which test events are emitted. At minimum, include `failed`; add `skipped` and `passed` when the output is used as a progress signal. `exceptionFormat = FULL` keeps the complete exception context in the console. `showStandardStreams = true` includes test `stdout` and `stderr`; it is useful for a bounded diagnostic session but can flood CI logs and hide failures in noisy output.

Use a deliberately more verbose local profile when needed rather than making every build noisy:

```kotlin
tasks.named<Test>("test") {
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
```

**Defaults:** Log `failed` and full exceptions. Keep standard streams disabled unless the test suite treats them as diagnostic output. Configure shared defaults with `withType<Test>().configureEach`; override a named task only when its output contract differs.

**Anti-patterns:**

- Enable `showStandardStreams` globally to compensate for missing assertions or structured logging.
- Configure only `passed` events and omit failures or exception details.
- Treat console output as the test result. The execution handoff must inspect executed-test counts and reports.
- Add test logging by eagerly creating tasks or by mutating tasks from another project.

**Version notes:** `testLogging {}`, event names, exception formats, and `showStandardStreams` are stable across Gradle 7, 8, and 9. If a plugin supplies its own test task type or reporting model, inspect that plugin's version-specific API before applying `Test`-specific configuration.

**More info:**

- Gradle test logging, events, exception formats, and standard streams: `gradle_docs(path="userguide/java_testing.md")`
- Console execution and report inspection: hand off to `using-gradle/references/testing.md`; `query_build` documentation

## Script-based test filtering

Use the `Test.filter {}` block for a permanent, build-defined selection. This is configuration, not a command to run the filtered tests:

```kotlin
tasks.named<Test>("test") {
    filter {
        includeTestsMatching("com.example.critical.*")
        excludeTestsMatching("com.example.knownslow.*")
    }
}
```

Use `includeTestsMatching` to define the test classes or methods that belong in this task and `excludeTestsMatching` to remove known categories. Keep patterns stable, narrow, and documented in the build when they represent a product or CI contract. A filter can select no tests; do not make a zero-match result an implicit success criterion.

Configure the filter on the actual task that owns the test set:

```kotlin
tasks.named<Test>("integrationTest") {
    filter {
        includeTestsMatching("com.example.integration.*")
    }
}
```

Use CLI `--tests` for an ad-hoc class or method selection that should not change the build definition. The syntax, task path, execution, and verification belong to `using-gradle`, not here. Do not encode a temporary debugging filter in `build.gradle.kts` merely to make one local run pass.

| Intent | Authoring choice | Execution handoff |
|---|---|---|
| Permanent CI or product subset | `filter { includeTestsMatching(...) }` | `using-gradle` runs the owning task and verifies the result |
| Permanent exclusion | `filter { excludeTestsMatching(...) }` | `using-gradle` confirms the intended task was selected |
| One-off class or method | No build-script change; use `--tests` | `using-gradle` owns task selection and execution |
| Diagnose a zero-test result | Remove ambiguity in task/filter configuration | `using-gradle` inspects executed-test counts and reports |

**Anti-patterns:**

- Replace a permanent build contract with instructions to pass `--tests` manually.
- Put a one-off `includeTestsMatching` pattern in the build script and forget to remove it.
- Assume a filter on `tasks.test` affects KMP target tasks or custom source-set tasks.
- Treat a successful task with zero matching tests as a valid filtered pass.

**Version notes:** `Test.filter {}` and `includeTestsMatching`/`excludeTestsMatching` are stable across Gradle 7, 8, and 9. Gradle 7.x builds use the same script API. CLI `--tests` filtering is also stable across 7-9, but its task selection and execution are explicitly handled by `using-gradle`.

**More info:**

- Gradle test filtering: `gradle_docs(path="userguide/java_testing.md")`
- Ad-hoc filtering and test execution: [using-gradle testing](../../using-gradle/references/testing.md). MCP test result lookup

## Kotlin Multiplatform test runs

KMP does not have one universal JVM `test` task. Each target owns test runs and may expose a target-specific Gradle task. Configure the test run inside the target definition, and configure the target's dependencies in the matching source set:

```kotlin
plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.10.0")
            }
        }
    }

    jvm {
        testRuns.named("test") {
            execution {
                useJUnitPlatform()
            }
        }
    }
}
```

Apply platform-specific configuration per target. Do not assume that `tasks.test` configures `jvmTest`, `jsTest`, native test tasks, or Android target tests. Discover the generated task names through `using-gradle` before handing off execution. A filter or logging block must be attached to the target test task or run that actually owns the tests.

```kotlin
kotlin {
    jvm {
        testRuns.named("test") {
            execution {
                useJUnitPlatform()
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
```

Keep common test dependencies in `commonTest` only when the framework is common-compatible. Put JUnit Jupiter dependencies and JUnit Platform setup in the JVM-specific source set and target. Configure each target's test run independently when the targets use different frameworks or reporters.

**Anti-patterns:**

- Configure only the root `test` task in a KMP project.
- Put a JVM-only JUnit dependency in `commonTest`.
- Apply a JVM test filter to every target without checking whether the target supports the pattern or framework.
- Treat a passing target task as evidence that every KMP target ran.

**Version notes:** KMP `kotlin { target { testRuns { ... } } }` syntax is owned by the Kotlin Gradle Plugin and changes independently of Gradle 7, 8, and 9. Verify the project's KGP version before changing the test-run API. On older KGP versions, preserve the existing target-specific syntax rather than copying a newer `execution {}` form. The Gradle `Test` logging and filtering APIs remain version-stable where the target exposes a Gradle `Test` task.

**More info:**

- Gradle test filtering and reports: `gradle_docs(path="userguide/java_testing.md")`
- KMP target task discovery and execution: hand off to `using-gradle`; use its testing reference at [testing.md](../../using-gradle/references/testing.md).

## Java test fixtures

Apply `java-test-fixtures` to a JVM library that owns reusable test utilities. Pair it with `java-library` when the fixture module exposes API dependencies to fixture consumers:

```kotlin
plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    testFixturesApi("org.assertj:assertj-core:3.25.3")
    testFixturesImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}
```

The plugin creates a separate test-fixtures source set and publishes the fixture variant for project consumers. For Kotlin JVM sources, place fixture code under `src/testFixtures/kotlin` when the Kotlin plugin configures that source set; Java fixture sources use `src/testFixtures/java`.

Consume fixtures through the `testFixtures(...)` dependency notation, not by reaching into another project's source directories:

```kotlin
dependencies {
    testImplementation(testFixtures(project(":core-module")))
}
```

Use `testFixturesApi` for dependencies needed by fixture types exposed to consumers. Use `testFixturesImplementation` for dependencies used only inside fixture implementation. Keep production dependencies out of fixtures unless the fixture code genuinely requires them.

**Defaults:** Keep fixtures in the module that owns the reusable test contract. Expose only stable helpers, builders, fakes, and data generators. Use `testFixtures(project(":module"))` from consuming modules.

**Anti-patterns:**

- Depend directly on `src/test` or `src/testFixtures` file paths.
- Duplicate the same fixture implementation across modules.
- Put application production code in the fixture source set.
- Use fixture dependencies to bypass a missing production API or an incorrect module boundary.
- Assume `java-test-fixtures` alone configures Kotlin source compilation; retain the project's existing Kotlin JVM plugin and source-set conventions.

**Version notes:** `java-test-fixtures` and the `testFixtures(...)` dependency variant are supported across Gradle 7, 8, and 9. Gradle 7.x builds may have older Kotlin or publishing-plugin interactions; verify the applied Java/Kotlin plugin versions and preserve existing variant conventions. Prefer the current Gradle 9.x model for new authoring, but do not replace a working 7.x fixture setup without checking published variants.

**More info:**

- Gradle test fixtures: `gradle_docs(path="userguide/java_testing.md")`
- Fixture consumption is build configuration. Hand off running fixture-consuming tests to `using-gradle`; do not use this section to prescribe execution commands.

## Aggregate reports and fork isolation

Build aggregate test or JaCoCo reports only from matching suite variants, and wire every intended producer into the aggregation model. Keep `forkEvery` at `0` unless evidence shows a leaked state boundary; a low value is not a generic flakiness cure and can hide the underlying defect at substantial startup cost.

## Cross-reference: testing custom build logic

For tests of custom tasks or plugins, follow the frozen corpus entry [Best Practices for Testing](best-practices/best-practices-for-testing.md), which routes to Gradle TestKit and contains the approved rationale. See [Plugin Development](plugin-development.md) for the authoritative guide to authoring functional tests with TestKit. Do not duplicate that guidance here.
