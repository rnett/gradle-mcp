<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Configurations and Variants

Author the configuration and variant model to control dependency resolution, isolate project outputs, and expose artifacts for consumption. This reference covers configuration roles, feature variants, and variant-aware consumption. Use this to model how your project provides and consumes binaries; hand off the resulting resolution graph inspection to `using-gradle`.

## Configuration Roles and Hygiene

Every configuration in Gradle has a role. Mixing these roles leads to resolution failures and breaks the Configuration Cache.

### The Three Roles
- **Declarable (Bucket):** Used to declare dependencies (e.g., `implementation`, `api`). These are not resolved themselves; they feed into resolvable configurations.
- **Resolvable:** Used to resolve a classpath for a task or runtime (e.g., `runtimeClasspath`, `compileClasspath`). You cannot declare dependencies directly on these.
- **Consumable:** Used to expose artifacts to other projects or the external world (e.g., `apiElements`, `runtimeElements`).

```kotlin
configurations {
    create("myInternalLib") {
        isCanBeResolved = false
        isCanBeConsumed = false
        // This is a pure 'bucket' (Declarable)
    }
    create("myRuntimeClasspath") {
        isCanBeResolved = true
        isCanBeConsumed = false
        // This is a resolver
    }
}
```

**Default:** Explicitly set `isCanBeResolved` and `isCanBeConsumed` when creating custom configurations to lock their role.

**Anti-pattern:** Creating a "god" configuration that is both resolvable and consumable. This violates role hygiene and can trigger unexpected resolution during the configuration phase.

### Configuration Inheritance (`extendsFrom`)
Use `extendsFrom` to share dependency sets between configurations without duplicating declarations.

```kotlin
configurations {
    val commonDependencies by creating
    implementation {
        extendsFrom(commonDependencies)
    }
}
```

**Default:** Extend the correct "bucket" configurations to maintain a clean hierarchy.

## Feature Variants and Capabilities

Feature variants allow a project to provide multiple optional sets of functionality (e.g., a "cloud" vs "local" implementation) without creating separate projects.

### Registering Features
Use `registerFeature` to create a feature variant. This automatically creates new source sets and configurations.

```kotlin
java {
    registerFeature("cloud") {
        usingSourceSet(sourceSets["cloud"])
    }
}

dependencies {
    "cloudImplementation"("com.aws:aws-java-sdk-s3:1.12.0")
}
```

### Capabilities and Selection
A capability identifies a specific piece of functionality. Use capabilities to handle mutually exclusive variants (e.g., different logging backends) or to provide an alternate implementation of the same API.

```kotlin
configurations {
    runtimeElements {
        outgoing {
            capability("com.example:logging-impl:1.0")
        }
    }
}
```

**Default:** Model custom functionality as features. Use capabilities when a module should be treated as a replacement for another.

**Anti-pattern:** Creating separate sub-projects for every optional feature. This increases configuration overhead and complicates the project structure.

## Variant-Aware Consumption

Consume outputs from other projects via dependency resolution, not through hard-coded task dependencies. This ensures compatibility with Isolated Projects.

### Consumer/Producer Recipe
1. **Producer:** Exposes artifacts via a Consumable configuration (e.g., `apiElements`).
2. **Consumer:** Declares a dependency on the producer project.
3. **Gradle:** Matches the Consumer's requested attributes (e.g., `org.gradle.library.elements`) against the Producer's outgoing variants to select the correct artifact.

```kotlin
// Consumer build.gradle.kts
dependencies {
    implementation(project(":producer-module"))
}
```

**Default:** Rely on Gradle's attribute matching for artifact wiring. If you need custom artifacts, define a new consumable configuration with a unique attribute.

**This is prohibited:** Using `tasks.getByName("jar")` or `project(":other").tasks["jar"]` to wire outputs. This creates tight coupling and breaks the configuration-cache/isolated-projects model.

### Inspecting Outgoing Variants
Use the `outgoingVariants` property to debug what a project is exposing. Use `using-gradle` to inspect the attributes of these variants.

### Version notes
- **Gradle 8/9:** Strict enforcement of configuration roles. Using a resolvable configuration as a declarable one will throw an exception.
- **Gradle 7.6+:** Improved support for resolver repositories and variant-aware resolution.

**More info:**
- Dependency Configurations: `gradle_docs` `tag:userguide` path `userguide/dependency_configurations.md`
- Component Capabilities: `gradle_docs` `tag:userguide` path `userguide/component_capabilities.md`
- Sharing Outputs: `gradle_docs` `tag:userguide` path `userguide/how_to_share_outputs_between_projects.md`
- Configuration Roles: `gradle_docs` `tag:userguide` path `userguide/declaring_configurations.md`
