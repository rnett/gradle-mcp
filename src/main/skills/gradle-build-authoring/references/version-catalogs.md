# Version Catalogs

Version catalogs provide a centralized, type-safe way to define dependencies and plugins across a multi-project build. They replace hard-coded version strings and manual dependency management with a shared TOML file.

## TOML Structure

A version catalog is defined in `gradle/libs.versions.toml`. It consists of four main sections: `[versions]`, `[libraries]`, `[bundles]`, and `[plugins]`.

### Example `libs.versions.toml`

```toml
[versions]
# Define versions as constants to be reused across libraries and plugins
kotlin = "2.0.0"
retrofit = "2.9.0"
junit = "5.10.0"
androidx-core = "1.12.0"

[libraries]
# Format: alias = { group = "...", name = "...", version.ref = "..." }
# Or use a direct version: alias = { group = "...", name = "...", version = "..." }
kotlin-stdlib = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version.ref = "kotlin" }
retrofit-core = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter", version.ref = "junit" }
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "androidx-core" }

[bundles]
# Groups multiple libraries into a single accessor
networking = ["retrofit-core", "retrofit-gson"]

[plugins]
# Define plugins for use in the plugins { } block
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
android-gradle = { id = "com.android.application", version = "8.2.0" }
```

## Usage in Build Scripts

Gradle generates type-safe accessors based on the aliases in the TOML file.

### Accessing Libraries

Use the `libs` accessor to reference libraries or bundles in `build.gradle.kts`.

```kotlin
dependencies {
    // Individual library
    implementation(libs.kotlin.stdlib)
    
    // Using a bundle
    implementation(libs.bundles.networking)
    
    // Test dependency
    testImplementation(libs.junit.jupiter)
}
```

### Accessing Plugins

Reference plugins using the `libs.plugins` accessor.

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.gradle)
}
```

## Multi-Catalog Setups

For very large projects or those sharing dependencies across different repositories, you can define additional catalogs in `settings.gradle.kts`.

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
        create("internal") {
            from(files("gradle/internal-libs.versions.toml"))
        }
    }
}
```

Usage then changes to reflect the catalog name:
```kotlin
dependencies {
    implementation(internal.myInternalLibrary)
}
```

## Version References and Constraints

### Version References
The `version.ref` key allows you to map multiple libraries to a single version variable in the `[versions]` block, ensuring consistency across a suite of related libraries (e.g., all Retrofit modules).

### Strict Version Constraints
If you need to force a specific version regardless of transitive dependencies, use strict versions in the build script:

```kotlin
dependencies {
    implementation("org.apache.logging.log4j:log4j-core") {
        version {
            strictly("2.20.0")
        }
    }
}
```
