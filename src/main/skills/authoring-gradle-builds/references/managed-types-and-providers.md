# Managed Types and Providers

Model build properties lazily using the Property and Provider APIs. Wire values through providers to defer realization until the execution boundary; this ensures configuration avoidance and compatibility with the configuration cache.

## Property vs Provider

Use `Property<T>` for values that can be configured (mutable) and `Provider<T>` for values that are read-only. Wire them using `set()`, `map`, `flatMap`, `zip`, and `orElse`.

```kotlin
abstract class MyTask : DefaultTask() {
    @get:Input
    abstract val message: Property<String>

    @get:Input
    abstract val suffix: Property<String>

    // Computed property wired lazily
    val fullMessage: Provider<String> = message.flatMap { msg ->
        suffix.map { s -> "$msg$s" }
    }
}

tasks.register<MyTask>("myTask") {
    message.set("Hello")
    suffix.convention(" World")
}
```

**Default:** Use `Property` for task and extension inputs. Wire multiple providers using `zip` for synchronous combinations or `flatMap` for dependent transformations.

**Anti-pattern:** Call `.get()` or `.getOrElse()` during the configuration phase to compute another property; use `map` or `flatMap` instead.

### `ProviderFactory.provider` is a configuration-time bridge

`ProviderFactory.provider { }` (and `Project.provider { }`) wraps a computation in a `Provider`, but the lambda is a configuration-time bridge and may be evaluated eagerly. Do not use it to defer file I/O, network calls, process execution, or other execution work; use a task input and read it in the task action instead.

```kotlin
val configuredValue = providers.provider { readConfigurationFile() } // Configuration-time computation
// For execution work, wire a task input and call the injected service from @TaskAction.
```

See [Do not call `get()` on a Provider outside a Task action](best-practices/do-not-call-get-on-a-provider-outside-a-task-action.md) for the rationale on avoiding premature realization.

### Field-guide rule: Distinguish `set(null)` from an absent provider

## set vs convention vs finalization

Use `convention()` to define a default that can be overridden and `set()` to provide a fixed value or wire a provider. Prefer `finalizeValueOnRead()` when the property should become fixed at its first read, or `disallowChanges()` when configuration should close at a deliberate boundary. Use `finalizeValue()` only when immediate finalization and disposal of the value's source are intentional.

```kotlin
abstract class MyExtension {
    abstract val outputDir: DirectoryProperty
}

// In plugin apply logic:
extension.outputDir.convention(layout.buildDirectory.dir("outputs"))
extension.outputDir.set(layout.projectDirectory.dir("custom-out")) // Overrides convention
extension.outputDir.finalizeValueOnRead() // Finalizes when first read
// Use disallowChanges() when the configuration boundary is explicit.
```

**Default:** Apply conventions in plugin code so users have a sensible default. Prefer `finalizeValueOnRead()` or `disallowChanges()` according to when the value must stop changing; reserve `finalizeValue()` for deliberate immediate finalization.

**Anti-pattern:** Use `set()` for defaults, which prevents users from providing their own values without knowing the original default, or finalize immediately merely to prevent later configuration.

## Scalar, File, and Collection Properties

Use specialized managed types for files and collections to enable precise input/output tracking and lazy resolution.

```kotlin
abstract class FileProcessorTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:Input
    abstract val flags: MapProperty<String, Boolean>

    @get:Input
    abstract val tags: SetProperty<String>

    @get:Input
    abstract val orderedItems: ListProperty<String>
}
```

**Default:** Always use `RegularFileProperty` and `DirectoryProperty` instead of `File` or `String` for paths. Use `ListProperty`, `SetProperty`, and `MapProperty` for managed collections.

**Anti-pattern:** Declare file inputs as `Property<File>` or `Property<String>`, which bypasses Gradle's optimized file-system tracking and prevents correct cache key computation.

## Managed and Named Managed Types

Prefer Gradle-created managed objects over manual instantiation. Use `ObjectFactory` to create nested managed types or named objects.

```kotlin
abstract class MyExtension {
    abstract val settings: MySettings // Nested managed type
}

abstract class MySettings {
    abstract val timeout: Property<Int>
}

// For named objects
abstract class MyNamedDomainObject {
    abstract val name: String
    abstract val value: Property<Int>
}
```

**Default:** Declare managed properties as `abstract` in classes inheriting from `DefaultTask` or other managed types. Use `NamedDomainObjectContainer` to manage a collection of uniquely named objects.

**Anti-pattern:** Use `new MySettings()` or `MySettings()` inside a task or extension; this breaks the managed lifecycle and configuration cache compatibility.

**Field-guide rule: Use provider-backed managed model types.** Abstract managed properties created through Gradle's `ObjectFactory` preserve lifecycle, validation, and caching semantics that ordinary mutable fields do not.

## Collections and Containers

Use `NamedDomainObjectContainer` for polymorphic collections of named objects. Use `register` and `configureEach` to keep the container lazy.

```kotlin
interface MyExtension {
    val items: NamedDomainObjectContainer<MyItem>
}

// Usage in build script:
extensions.configure<MyExtension> {
    items.register("first") {
        value.set(10)
    }
    items.configureEach {
        // Configures all items lazily
    }
}
```

**Default:** Use `register` over `create` to avoid eager realization of every object in the container. Use `configureEach` instead of `allItems` or `all` to maintain configuration avoidance.

**Anti-pattern:** Iterate over a container using `forEach` or `toList()` during configuration, which forces eager realization of every managed object.

## Lazy Files

Use `ProjectLayout` to resolve project-relative paths lazily. Avoid `new File()` as it resolves against the current working directory (CWD), which varies by execution environment.

```kotlin
val buildDir = layout.buildDirectory // Provider<Directory>
val projectDir = layout.projectDirectory // Directory

// Safe relative path
val inputFile = layout.projectDirectory.file("config/settings.json")

// Provider-backed copy wiring
tasks.register<Copy>("copyConfig") {
    from(inputFile)
    into(layout.buildDirectory.dir("config"))
}
```

**Default:** Use `layout.projectDirectory` and `layout.buildDirectory` for all file paths. Use `FileCollection` and `FileTree` for grouping files without resolving them to a list.

**Anti-pattern:** Use `new File("path/to/file")` or `Project.file("...")` inside a task action; these are not lazy and can cause issues in isolated projects or remote cache restores.

See [Avoid using eager APIs on File Collections](best-practices/avoid-using-eager-apis-on-file-collections.md) for the rationale on lazy file collections.

## Incubating: Dataflow Actions

**Warning:** Dataflow Actions are **incubating** and not a stable default. Use them only for non-task logic that requires annotated managed inputs. For implementation details, use `gradle_docs(path="userguide/dataflow_actions.md")`.

**Default:** Prefer standard tasks for any work that produces file outputs. Dataflow actions are intended for internal Gradle data-flow within the configuration or execution graph.

**Anti-pattern:** Using `FlowAction` for general-purpose build logic or as a replacement for custom tasks.

### Version notes

- **Gradle 9.x:** Property and Provider APIs are the stable standard. Config cache is stable; all managed types are required for compatibility. Dataflow actions remain incubating.
- **Gradle 8.x:** The same APIs apply; config cache is stable from 8.1.
- **Gradle 7.x:** Managed properties are available but some plugins may still use eager `create` patterns. Prefer `register` and `Property` where supported.

**More info:**
- Lazy configuration: `gradle_docs(path="userguide/lazy_configuration.md")`
- Properties and Providers: `gradle_docs(path="userguide/properties_providers.md")`
- Collections: `gradle_docs(path="userguide/collections.md")`
- Working with files: `gradle_docs(path="userguide/working_with_files.md")`
- Dataflow actions: `gradle_docs(path="userguide/dataflow_actions.md")`
