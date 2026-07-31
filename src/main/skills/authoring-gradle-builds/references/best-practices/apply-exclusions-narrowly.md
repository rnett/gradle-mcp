<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: 35a0b2b9f3815924659f9e7f4ec1616c36e234b1f776c72e58942b64cfa3fefe
-->
# Apply Exclusions Narrowly
When excluding transitive dependencies, apply exclusions as narrowly as possible.  

## Explanation
Sometimes you may need to [exclude transitive dependencies](https://docs.gradle.org/current/userguide/how_to_exclude_transitive_dependencies.html#how_to_exclude_transitive_dependencies) (Use `gradle_docs(path="userguide/how_to_exclude_transitive_dependencies.html#how_to_exclude_transitive_dependencies")`.) that cause conflicts or issues in your project.  
Exclusions can negatively affect dependency resolution performance. Applying exclusions as narrowly as possible minimizes this impact. It also reduces the risks of inadvertently and silently excluding dependencies that are required elsewhere in your build, and of accidental runtime dependency clashes.  
Gradle offers several ways to exclude transitive dependencies. When excluding transitive dependencies, keep the scope as narrow as possible:  
* Attach exclusions to **specific dependencies** rather than applying them to an entire configuration.

* Exclude a single `module` from a `group`, instead of excluding the entire `group`.

* **Avoid** global exclusions using `configurations.all { ...​ }` or `configurations.configureEach { ...​ }`.

## Example
### Don't Do This
build.gradle.kts  

```kotlin
dependencies {
    implementation("org.apache.commons:commons-pool2:2.12.1") (1)
    implementation("org.hibernate:hibernate-core:3.6.10.Final")
    // ... other dependencies ...
}

configurations {
    "implementation" {
        exclude(group = "cglib") (2)
    }

    "implementation" {
        exclude(group = "org.ow2.asm", module = "asm-util") (3)
    }
}

configurations.configureEach {
    exclude(group = "javassist", module = "javassist") (4)
}
```

build.gradle  

```groovy
dependencies {
    implementation("org.apache.commons:commons-pool2:2.12.1") (1)
    implementation("org.hibernate:hibernate-core:3.6.10.Final")
    // ... other dependencies ...
}

configurations {
    implementation {
        exclude(group: "cglib") (2)
    }

    implementation {
        exclude(group: "org.ow2.asm", module: "asm-util") (3)
    }
}

configurations.configureEach {
    exclude(group: "javassist", module: "javassist") (4)
}
```

|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | The `commons-pool2` dependency transitively includes `cglib:cglib` and `org.ow2.asm:asm-util` as optional dependencies - we want to exclude both. `hibernate-core` transitively optionally includes `cglib:cglib`, and also `javaassist:javassist` - we want to exclude both.                                   |
| **2** | This excludes **every** module provided by the `cglib` group from **every** dependency in the `implementation` configuration. If other current or future dependencies in this project rely on different modules from `cglib`, those dependencies may fail to resolve, leading to compilation or runtime errors. |
| **3** | This excludes `org.ow2.asm:asm-util` from **every** dependency in the `implementation` configuration. If future dependencies rely on `org.ow2.asm:asm-util`, they may fail at compile or runtime because the module will be silently excluded.                                                                  |
| **4** | This excludes `javaassist:javassist` from **all** dependencies in **all** configurations, including those added by plugins or in the future, which carries the same risks as above, but on a larger scale.                                                                                                      |

### Do This Instead
Exclude transitive dependencies as narrowly as possible, ideally on individual dependencies:  
build.gradle.kts  

```kotlin
dependencies {
    implementation("org.apache.commons:commons-pool2:2.12.1") { (1)
        exclude(group = "cglib", module = "cglib") (2)
        exclude(group = "org.ow2.asm", module = "asm-util")

    }
    implementation("org.hibernate:hibernate-core:3.6.10.Final") {
        exclude(group = "cglib", module = "cglib")
        exclude(group = "javassist", module = "javassist") (3)
    }
    // ... other dependencies ...
}
```

build.gradle  

```groovy
dependencies {
    implementation("org.apache.commons:commons-pool2:2.12.1") { (1)
        exclude(group: "cglib", module: "cglib") (2)
        exclude(group: "org.ow2.asm", module: "asm-util")

    }
    implementation("org.hibernate:hibernate-core:3.6.10.Final") {
        exclude(group: "cglib", module: "cglib")
        exclude(group: "javassist", module: "javassist") (3)
    }
    // ... other dependencies ...
}
```

|-------|-----------------------------------------------------------------------------------------------------------------------------------|
| **1** | Exclusions are applied only to the dependency that actually transitively includes them.                                           |
| **2** | All exclusions apply to a particular `module` instead of every module from a particular `group`.                                  |
| **3** | `javaassist:javassist` is only excluded from the `hibernate-core` dependency - the only dependency that transitively includes it. |

Though it may seem repetitive to exclude the same transitive dependencies from multiple dependencies, this approach is safer, more performant, less likely to cause accidental runtime crashes, and makes it clearer which dependencies are affected by each exclusion.  

## References
* [Excluding Transitive Dependencies](https://docs.gradle.org/current/userguide/resolution_rules.html#sec:exclude-trans-deps) (Use `gradle_docs(path="userguide/resolution_rules.html#sec:exclude-trans-deps")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
