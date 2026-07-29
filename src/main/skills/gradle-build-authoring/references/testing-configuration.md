# Testing Configuration

Properly configuring tests in Gradle ensures consistent execution, detailed reporting, and support for modern testing frameworks like JUnit 5 and Kotlin Multiplatform (KMP).

## JUnit 5 Platform Setup

To use JUnit 5, you must explicitly tell Gradle to use the JUnit Platform for test execution.

```kotlin
tasks.test {
    useJUnitPlatform()
}
```

## Test Logging Configuration

Configure the `test` task to provide better visibility into test failures and successes during the build process.

```kotlin
tasks.test {
    testLogging {
        // Define which events are logged
        events("passed", "skipped", "failed")
        
        // Show detailed output for failed tests
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        
        // Log a summary at the end of the test run
        showStandardStreams = true
        showExceptions = true
    }
}
```

## Test Filtering

Declare filters in the build script (preferred for reproducible builds). For ad-hoc debugging runs, some projects use the CLI `--tests` flag on an external runner — consult the gradle skill for execution details.

### Script-based Filtering (Persistent Configuration)
For reproducible test suites, declare filters directly in the build script using the `Filter` API so that all consumers get consistent behavior regardless of how they invoke Gradle:
```kotlin
tasks.test {
    filter {
        includeTestsMatching("com.example.critical.*")
        excludeTestsMatching("com.example.flaky.*")
    }
}
```

## Kotlin Multiplatform (KMP) Test Configuration

In KMP projects, tests are configured within the `kotlin` target definition in `build.gradle.kts`.

```kotlin
kotlin {
    jvm {
        testRuns.named("test") {
            useJUnitPlatform()
        }
    }
    
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
}
```

## Test Fixtures

The `java-test-fixtures` plugin allows you to create a separate source set for test utilities, mocks, and data generators that can be shared across multiple test sets or even other modules.

### Enabling Test Fixtures
```kotlin
plugins {
    `java-library`
    `java-test-fixtures`
}
```

### Using Fixtures in Other Modules
Fixtures are exposed via the `testFixtures()` dependency configuration.

```kotlin
dependencies {
    testImplementation(testFixtures(project(":core-module")))
}
```

Fixtures are located in `src/testFixtures/kotlin` and are compiled separately from the main source and the test source.
