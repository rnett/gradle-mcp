<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Build Scans

Build scans are permanent, shareable records of what happened during a Gradle build. They provide deep insights into performance, dependency resolution, and failure analysis.

## Setup and Publishing

### Develocity Plugin

To enable build scans, apply the Develocity (formerly Gradle Enterprise) plugin in your `settings.gradle.kts`:

```kotlin
plugins {
    id("com.gradle.develocity") version "3.x" // Use latest version
}

develocity {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        
        // Add custom labels to identify build types
        value("CI", "true")
    }
}
```

### Publishing to scans.gradle.com

For open-source projects, you can publish scans for free to `scans.gradle.com`. The configuration above handles the agreement to the terms of service required for free publishing.

## CI Integration

In CI environments, you often want to publish scans automatically to avoid manual debugging of failed agents.

### Automation Strategies

Configure the `buildScan` block to handle different CI outcomes:

```kotlin
develocity {
    buildScan {
        // Always publish in CI
        publishAlways() 
        
        // OR: only publish if the build fails
        // publishOnFailure()
    }
}
```

### Scan Links in CI Output

When a scan is published, Gradle prints a URL to the console output. Most CI providers (GitHub Actions, GitLab CI) can be configured to extract this link and post it as a PR comment or a job attachment for quick access.

## Interpreting Build Scans

Once you open a scan URL, focus on these key areas:

- **Performance**: The "Performance" tab shows task execution time, avoiding "up-to-date" checks, and overhead costs.
- **Timeline**: A visualization of parallel task execution. Look for long-running tasks that block others (critical path).
- **Dependencies**: Search for specific libraries to see why a certain version was selected and which dependency pulled it in.
- **Failures**: The "Failures" tab provides a direct link to the failing task and the exact stack trace, often with more context than the CLI.
- **Infrastructure**: View the JDK version, OS, and Gradle version used by the agent to identify environment-specific regressions.
