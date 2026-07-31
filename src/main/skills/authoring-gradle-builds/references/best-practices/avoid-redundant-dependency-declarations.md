<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: efbadb38168d2bedac122d37fa291a39845533e99488ee683d805e5644c36ce2
-->
# Avoid Redundant Dependency Declarations
Avoid declaring the same dependency multiple times, especially when it is already available transitively or through another configuration.  

## Explanation
Duplicating dependencies in Gradle build scripts can lead to:  
* **Increased maintenance**: Declaring a dependency in multiple places makes it harder to manage.

* **Unexpected behavior** : Declaring the same dependency in multiple configurations (e.g., `compileOnly` and `implementation`) can result in hard-to-diagnose classpath issues.

## Example
### Don't Do This
build.gradle.kts  

```kotlin
plugins {
    `java-library`
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0") (1)
}
```

build.gradle  

```groovy
plugins {
    id 'java-library'
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0") (1)
}
```

|-------|-------------------------------------------------|
| **1** | Redundant dependency in `implementation` scope. |

### Do This Instead
build.gradle.kts  

```kotlin
plugins {
    `java-library`
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0") (1)
}
```

build.gradle  

```groovy
plugins {
    id 'java-library'
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0") (1)
}
```

|-------|-------------------------|
| **1** | Declare dependency once |

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
