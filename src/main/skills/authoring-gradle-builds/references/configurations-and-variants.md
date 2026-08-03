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
    register("myInternalLib") {
        isCanBeResolved = false
        isCanBeConsumed = false
        // This is a pure 'bucket' (Declarable)
    }
    register("myRuntimeClasspath") {
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
    val commonDependencies = register("commonDependencies")
    implementation {
        extendsFrom(commonDependencies.get())
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

## Custom Attributes

Gradle matches variants using attributes: typed key/value pairs on configurations. Define custom attributes when the built-in ones (`org.gradle.usage`, `org.gradle.libraryelements`, `org.gradle.category`, etc.) cannot express the distinction a resolution must make, such as a "classification" or "targetPlatform".

### `Attribute.of(...)`

Define a custom attribute with a name and a type; the attribute's value is a `Named` object, so declare a type that implements `Named`:

```kotlin
// shared/src/main/kotlin/platform/Platform.kt
interface Platform : Named {
    companion object {
        val TARGET_PLATFORM = Attribute.of("com.example.targetPlatform", Platform::class.java)
    }
}

// Declared per-variant, e.g. "jvm" or "native"
class DefaultPlatform(override val name: String) : Platform
```

### Where attributes are set

- **Producer / consumable configuration** (`outgoing { capability(...) }` or the configuration's `attributes {}` block): declares the variant's values.
- **Consumer / resolvable configuration** (`attributes {}` on the resolvable configuration): declares the attributes the consumer requires for matching.

```kotlin
// Producer: each variant declares its own platform value
val jvmElements = configurations.register("jvmElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Platform.TARGET_PLATFORM, objects.named(Platform::class.java, "jvm"))
    }
}

// Consumer: requires the JVM variant
val jvmClasspath = configurations.register("jvmClasspath") {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Platform.TARGET_PLATFORM, objects.named(Platform::class.java, "jvm"))
    }
}
```

### Compatibility and disambiguation rules

Gradle selects a variant by proposing all outgoing attributes on each candidate and comparing against the consumer's requested attributes:

- **Compatibility:** an attribute is compatible when the consumer's requested value is a subtype of (or equal to) the candidate's value, or the candidate's value is the "fallback" (default) for that attribute. An attribute added to only one side does not disqualify a variant.
- **Disambiguation:** when more than one variant is compatible, Gradle prefers the candidate whose value is the most specific (and not the fallback). If multiple variants remain tied, resolution fails with an ambiguous-variant error; add a disambiguating attribute to break the tie.
- **Nonexistent attribute:** a consumer requesting an attribute that no candidate carries yields the "no matching variant" error; add a fallback (default) value on the producer so a generic consumer still matches.

**Default:** add a fallback/default attribute value on the producer for each custom attribute so unqualified consumers still resolve; keep the set of attributes minimal and shared across producer/consumer (define them in a convention plugin or shared model).

**Anti-pattern:** inventing ad hoc string keys per project (no `Attribute.of` type), or overloading a built-in attribute to carry project-specific meaning; define one typed attribute and reuse it everywhere it must match.

### Relationship to feature variants and capabilities

- **Custom attributes** select among hardware/format axes (`targetPlatform`, `linkage`) where every consumer should get exactly one variant.
- **Feature variants** (see above) add real functionality (`cloud`, `local`) that consumers opt into via a capability; they do not participate in attribute matching by default.
- **Capabilities** express "this module can stand in for that one" (mutual exclusion / alternate implementation).

Use a custom attribute when a resolution axis must be selected automatically; use features/capabilities when the consumer must explicitly opt in or when variants are mutually exclusive.

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

### IP-safe cross-project artifact sharing

When a project must share an arbitrary artifact (not just a Java library), model it as a consumable configuration that a producing task's provider-backed output is attached to, then consume it from a resolvable configuration in another project. This stays provider-backed and isolated-projects-compatible.

```kotlin
// producer/build.gradle.kts
val generatedElements = configurations.register("generatedElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    }
}

artifacts {
    add(generatedElements.name, generateConfig.map { it.outputFile.get().asFile })
}

// consumer/build.gradle.kts
val generatedConfig = configurations.register("generatedConfig") {
    isCanBeResolved = true
    isCanBeConsumed = false
}
dependencies {
    generatedConfig(project(":producer"))
}
```

The complete lazy producer/consumer recipe (task registration, provider wiring, and resolution) is in [Managed Types and Providers](managed-types-and-providers.md). Prefer it over `project(path, configuration)`, which bypasses variant-aware selection.

**Configuration-cache / isolated-projects consequence:** sharing through resolution means the consumer never touches the producer's `Project` object; the producing task runs only when its configuration is actually resolved. Direct task access (`project(":producer").tasks[...]`) realizes the producer eagerly and breaks both the configuration cache and isolation.

### Inspecting Outgoing Variants
Use the `outgoingVariants` property to debug what a project is exposing. Use `using-gradle` to inspect the attributes of these variants.

### Version notes
- **Gradle 8/9:** Strict enforcement of configuration roles. Using a resolvable configuration as a declarable one will throw an exception.
- **Gradle 7.6+:** Improved support for resolver repositories and variant-aware resolution.

**More info:**
- Dependency Configurations: `gradle_docs(path="userguide/dependency_configurations.md")`
- Component Capabilities: `gradle_docs(path="userguide/component_capabilities.md")`
- Sharing Outputs: `gradle_docs(path="userguide/how_to_share_outputs_between_projects.md")`
- Configuration Roles: `gradle_docs(path="userguide/declaring_configurations.md")`
