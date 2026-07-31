<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: 542843f36a223143d4ecf44a030f872d997c4ef5346b88dd30f1d0b86fc4a55c
-->
# Declare Dependencies using a single GAV (`group:artifact:version`) String
When declaring dependencies without a [version catalog](https://docs.gradle.org/current/userguide/version_catalogs.html#version-catalog) (Use `gradle_docs(path="userguide/version_catalogs.html#version-catalog")`.), prefer using the single GAV string notation `implementation("org.example:library:1.0")`. Avoid using the named argument notation. The named argument notation has been deprecated and will no longer be supported starting in Gradle 10.  

## Explanation
All of these declarations will be treated equivalently when Gradle resolves dependencies. However, the single-string form is more concise, easier to read, and is widely adopted in the broader JVM ecosystem.  
This format is also recommended by [Maven Central](https://central.sonatype.com/artifact/com.google.guava/guava) in its documentation and usage examples, making it the most familiar and consistent style for developers across tools.  

## Example
### Don't Do This
build.gradle.kts  

```kotlin
dependencies {
    implementation(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = "32.17.0")  (1)
    api(group = "com.google.guava", name = "guava", version = "32.1.2-jre") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")  (2)
    }
}
```

build.gradle  

```groovy
dependencies {
    implementation(group: 'com.fasterxml.jackson.core', name: 'jackson-databind', version: '2.17.0') (1)
    api(group: 'com.google.guava', name: 'guava', version: '32.1.2-jre') {
        exclude(group: 'com.google.code.findbugs', module: 'jsr305')    (2)
    }
}
```

|-------|----------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | Avoid the named argument notation when declaring dependencies                                                                                |
| **2** | Other modifiers methods and constraints like `exclude` are not included in this recommendation and can use named argument notation as needed |

### Do This Instead
build.gradle.kts  

```kotlin
dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0") (1)
    api("com.google.guava:guava:32.1.2-jre") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")  (2)
    }
}
```

build.gradle  

```groovy
dependencies {
    implementation('com.fasterxml.jackson.core:jackson-databind:2.17.0') (1)
    api('com.google.guava:guava:32.1.2-jre') {
        exclude(group: 'com.google.code.findbugs', module: 'jsr305')    (2)
    }
}
```

|-------|----------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | Use the string notation instead when declaring dependencies                                                                                  |
| **2** | Other modifiers methods and constraints like `exclude` are not included in this recommendation and can use named argument notation as needed |

## References
* [Declaring Dependencies Basics](https://docs.gradle.org/current/userguide/declaring_dependencies_basics.html#declaring-dependencies-basics) (Use `gradle_docs(path="userguide/declaring_dependencies_basics.html#declaring-dependencies-basics")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
