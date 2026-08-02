# Set Build Flags in `gradle.properties`
Set Gradle build property flags in the `gradle.properties` file.  

## Explanation
Instead of using command-line options or environment variables, set build flags in the root project's `gradle.properties` file.  
Gradle comes with a long list of [Gradle properties (Use `gradle_docs(path="userguide/build_environment.md")`.), which have names that begin with `org.gradle` and can be used to configure the behavior of the build tool. These properties can have a **major impact** on build performance, so it's important to understand how they work.  
You should not rely on supplying these properties via the command-line for every Gradle invocation. Providing these properties via the command line is intended for short-term testing and debugging purposes, but it's prone to being forgotten or inconsistently applied across environments. A permanent, idiomatic location to set and share these properties is in the `gradle.properties` file located in the root project directory. This file should be added to source control in order to share these properties across different machines and between developers.  
You should understand the default values of the properties your build uses and avoid explicitly setting properties to those defaults. Any change to a property's default value in Gradle will follow the standard [deprecation cycle (Use `gradle_docs(path="userguide/feature_lifecycle.md")`.), and users will be properly notified.  

|---|------------------------------------------------------------------------------------------------------------------------------------------|
|   | Properties set this way are not inherited across build boundaries when using [composite builds (Use `gradle_docs(path="userguide/composite_builds.md")`.). |

## Example
### Don't Do This
```kotlin
├── build.gradle.kts
└── settings.gradle.kts
```

```groovy
├── build.gradle
└── settings.gradle
```

build.gradle.kts  

```kotlin
tasks.register("first") {
    doLast {
        throw GradleException("First task failing as expected")
    }
}

tasks.register("second") {
    doLast {
        logger.lifecycle("Second task succeeding as expected")
    }
}

tasks.register("run") {
    dependsOn("first", "second")
}
```

build.gradle  

```groovy
tasks.register("first") {
    doLast {
        throw new GradleException("First task failing as expected")
    }
}

tasks.register("second") {
    doLast {
        logger.lifecycle("Second task succeeding as expected")
    }
}

tasks.register("run") {
    dependsOn("first", "second")
}
```

This build is run with `gradle run -Dorg.gradle.continue=true`, so that the failure of the `first` task does **not** prevent the `second` task from executing.  
This relies on person running the build to remember to set this property, which is error prone and not portable across different machines and environments.  

### Do This Instead
```kotlin
├── build.gradle.kts
└── gradle.properties
└── settings.gradle.kts
```

```groovy
├── build.gradle
└── gradle.properties
└── settings.gradle
```

gradle.properties  

```properties
org.gradle.continue=true
```

This build sets the `org.gradle.continue` property in the `gradle.properties` file.  
Now it can be executed using only `gradle run`, and the continue property will always be set automatically across all environments.  

## References
* [Gradle properties (Use `gradle_docs(path="userguide/build_environment.md")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
