<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: 3301fdf7742d0e9e7503313fca691516b7cb8bb509cf239bd50a537d5b7e195f
-->
# Name Version Catalog Entries Appropriately
Consistent and descriptive names in your version catalog enhance readability and maintainability across your build scripts.  

## Explanation
Version catalogs provide a centralized way to manage dependencies by mapping full dependency coordinates to concise, reusable aliases like `airlift-aircompressor`. Adopting clear naming conventions for those aliases ensures that developers can easily identify and use dependencies throughout the project.  
Aliases are typically made up of 1 to 3 segments. For example `org.apache.commons:commons-lang3` could be represented as `commonsLang3`, `apache-commonsLang3`, or `commons-lang3`.  
The following guidelines help in naming catalog entries effectively:  
1. **Use dashes to separate segments** : Prefer hyphen/dashes (`-`) over underscores (`_`) to separate different parts of the entry name.

   Example: For `org.apache.logging.log4j:log4j-api`, use `log4j-api`
2. **Derive the first segment from the project group** : Use a unique identifier from the project's group ID as the first segment. Do not include the top level domain in the segment (`com`, `org`, `net`, `dev`).

   Example: For `com.fasterxml.jackson.core:jackson-databind`, use `jackson-databind` or `jackson-core-databind`
3. **Derive the second segment from the artifact ID**: Use a unique identifier from the artifact ID as the second segment.

   Example: For `com.linecorp.armeria:armeria-grpc`, use `armeria-grpc`
4. **Avoid generic terms in the segments** : Exclude terms that are obvious or implied in the context of your project (`core`, `java`, `gradle`, `module`, `sdk`), especially if the term appears by itself.

   Example: For `com.google.googlejavaformat:google-java-format`, use `google-java-format`, not `google-java` or `java`
5. **Omit redundant segments**: If the group and artifact IDs are the same, avoid repeating them.

   Example: For `io.ktor:ktor-client-core`, use `ktor-client-core`, not `ktor-ktor-client-core`
6. **Convert internal dashes to camelCase**: If the artifact ID contains dashes, convert them to camelCase for better readability in code.

   Example: `spring-boot-starter-web` becomes `springBootStarterWeb`
7. **Suffix plugin libraries with `-plugin`** : When referencing a plugin as a library (not in the `[plugins]` section), append `-plugin` to the name.

Example: For `org.owasp:dependency-check-gradle`, use `dependency-check-plugin`  

## Example
gradle/libs.versions.toml  

```toml
[versions]
slf4j = "2.0.13"
jackson = "2.17.1"
groovy = "3.0.5"
checkstyle = "8.37"
commonsLang = "3.9"

[libraries]
# SLF4J
slf4j-api = { module = "org.slf4j:slf4j-api", version.ref = "slf4j" }

# Jackson
jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson" }
jackson-dataformatCsv = { module = "com.fasterxml.jackson.dataformat:jackson-dataformat-csv", version.ref = "jackson" }

# Groovy bundle
groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
groovy-nio = { module = "org.codehaus.groovy:groovy-nio", version.ref = "groovy" }

# Apache Commons Lang
commons-lang3 = { group = "org.apache.commons", name = "commons-lang3", version = { strictly = "[3.8, 4.0[", prefer = "3.9" } }

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

repositories {
    mavenCentral()
}

dependencies {
    // SLF4J
    implementation(libs.slf4j.api)

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformatCsv)

    // Groovy bundle
    api(libs.bundles.groovy)

    // Commons Lang
    implementation(libs.commons.lang3)
}
```

build.gradle  

```groovy
plugins {
    id 'java-library'
    alias(libs.plugins.versions)
}

repositories {
    mavenCentral()
}

dependencies {
    // SLF4J
    implementation libs.slf4j.api

    // Jackson
    implementation libs.jackson.databind
    implementation libs.jackson.dataformatCsv

    // Groovy bundle
    api libs.bundles.groovy

    // Commons Lang
    implementation libs.commons.lang3
}
```

## References
* [Best Practices for Naming Gradle Version Catalog Entries](https://blog.gradle.org/best-practices-naming-version-catalog-entries)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
