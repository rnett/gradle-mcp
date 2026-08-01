<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: f7da3908e40f1750bf7cef2a6a180da7576264abac48ba66527f0d81bbc58f37
-->
# Use Version Catalogs to Centralize Dependency Versions
Version Catalogs provide a centralized, declarative way to manage dependency versions throughout a build.  

## Explanation
When you define your dependency versions in a single, shared version catalog, you reduce duplication and make upgrades easier. Instead of changing dozens of `build.gradle(.kts)` files, you update the version in one place. This simplifies maintenance, improves consistency, and reduces the risk of accidental version drift between modules. Consistent version declarations across projects also make it easier to reason about behavior during testing---especially in modular builds where transitive upgrades can silently change runtime behavior in later stages of the build.  
However, version catalogs only influence declared versions, not resolved versions. Use them in combination with [dependency locking (Use `gradle_docs(path="userguide/dependency_locking.md")`.) and [version alignment (Use `gradle_docs(path="userguide/resolution_rules.md")`.) to enforce consistency across builds. To influence resolved versions, check out [platforms (Use `gradle_docs(path="userguide/platforms.md")`.).  

## Example
### Don't Do This
Avoid declaring versions in `project.ext`, constants, or local variables:  
build.gradle.kts  

```kotlin
plugins {
    id("java-library")
    id("com.github.ben-manes.versions").version("0.45.0")
}
val groovyVersion = "3.0.5"

dependencies {
    api("org.codehaus.groovy:groovy:$groovyVersion")
    api("org.codehaus.groovy:groovy-json:$groovyVersion")
    api("org.codehaus.groovy:groovy-nio:$groovyVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")

    implementation("org.apache.commons:commons-lang3") {
        version {
            strictly("[3.8, 4.0[")
            prefer("3.9")
        }
    }
}
```

build.gradle  

```groovy
plugins {
    id('java-library')
    id('com.github.ben-manes.versions').version('0.45.0')
}
def groovyVersion = '3.0.5'

dependencies {
    api("org.codehaus.groovy:groovy:$groovyVersion")
    api("org.codehaus.groovy:groovy-json:$groovyVersion")
    api("org.codehaus.groovy:groovy-nio:$groovyVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")

    implementation("org.apache.commons:commons-lang3") {
        version {
            strictly("[3.8, 4.0[")
            prefer("3.9")
        }
    }
}
```

Avoid misusing version catalogs for unrelated concerns:  
* Don't use them to store shared strings or non-library constants

* Don't overload them with arbitrary logic or plugin-specific configuration

### Do This Instead
Use a centralized `libs.versions.toml` file in your `gradle/` directory:  
gradle/libs.versions.toml  

```toml
[versions]
groovy = "3.0.5"
junit-jupiter = "5.10.0"

[libraries]
groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
groovy-nio = { module = "org.codehaus.groovy:groovy-nio", version.ref = "groovy" }
commons-lang3 = { group = "org.apache.commons", name = "commons-lang3", version = { strictly = "[3.8, 4.0[", prefer = "3.9" } }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit-jupiter" }

[bundles]
groovy = ["groovy-core", "groovy-json", "groovy-nio"]

[plugins]
versions = { id = "com.github.ben-manes.versions", version = "0.45.0" }
```

build.gradle.kts  

```kotlin
plugins {
    id("java-library")
    alias(libs.plugins.versions)
}
dependencies {
    api(libs.bundles.groovy)
    testImplementation(libs.junit.jupiter)
    implementation(libs.commons.lang3)
}
```

build.gradle  

```groovy
plugins {
    id('java-library')
    alias(libs.plugins.versions)
}
dependencies {
    api(libs.bundles.groovy)
    testImplementation(libs.junit.jupiter)
    implementation(libs.commons.lang3)
}
```

## References
* [Version Catalogs (Use `gradle_docs(path="userguide/version_catalogs.md")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
