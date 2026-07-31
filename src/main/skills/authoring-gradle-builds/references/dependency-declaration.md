<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Dependency Declaration & Catalog Management

Adds, removes, and modifies dependency declarations, manages version catalogs, and configures repositories.

## Adding a Dependency

### Step 1: Verify the GAV

Use `using-gradle` → Dependency Inspection or `lookup_maven_versions` to confirm the correct `group:artifact:version` coordinates.

### Step 2: Update the Version Catalog

If the project uses `gradle/libs.versions.toml`:

1. Add the version to `[versions]` if it's new:
   ```toml
   [versions]
   slf4j = "2.0.12"
   ```

2. Add the library alias to `[libraries]`:
   ```toml
   [libraries]
   slf4j-api = { module = "org.slf4j:slf4j-api", version.ref = "slf4j" }
   ```

3. Use kebab-case aliases (e.g., `slf4j-api`). Gradle generates dot-separated accessors (`libs.slf4j.api`).

4. Use `version.ref` for artifacts that must stay aligned. Use bundles only for dependencies normally added together.

### Step 3: Add to Build Script

```kotlin
dependencies {
    implementation(libs.slf4j.api)
}
```

### Without Version Catalog

If no catalog exists, declare directly:

```kotlin
dependencies {
    implementation("org.slf4j:slf4j-api:2.0.12")
}
```

## Managing Repositories

### In `settings.gradle.kts` (preferred)

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.example.com/releases")
    }
}
```

### In `build.gradle.kts` (when settings-level is not used)

```kotlin
repositories {
    mavenCentral()
}
```

### Safe Navigation for Repository URLs

When configuring repositories in init scripts or plugins:

```kotlin
val url = repository.url?.toString()
    ?: error("Repository $repository has no URL")
```

## Dependency Constraints

Add constraints without declaring a dependency:

```kotlin
dependencies {
    constraints {
        implementation("org.slf4j:slf4j-api:2.0.12") {
            because("Minimum version required for Java 21 support")
        }
    }
}
```

## Excluding Transitive Dependencies

```kotlin
dependencies {
    implementation(libs.someLib) {
        exclude(group = "unwanted.group", module = "unwanted-module")
    }
}
```

## Platform/BOM Dependencies

```kotlin
dependencies {
    implementation(platform(libs.springBoot.bom))
    implementation(libs.springBoot.starter.web) // version from BOM
}
```

## Catalog Structure for `build-logic`

Import the root catalog in `build-logic/settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
```

## Removing a Dependency

1. Remove the declaration from `build.gradle.kts`.
2. If no other module uses it, remove the alias from `libs.versions.toml`.
3. If the version is no longer referenced, remove it from `[versions]`.

## Examples

### Add a test dependency

```kotlin
// In libs.versions.toml:
// [versions]
// mockk = "1.13.10"
// [libraries]
// mockk = { module = "io.mockk:mockk", version.ref = "mockk" }

// In build.gradle.kts:
dependencies {
    testImplementation(libs.mockk)
}
```

### Add a BOM and aligned dependencies

```kotlin
dependencies {
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
```
