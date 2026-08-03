# File Operations

Procedural guidance for `Copy`, `Sync`, and `Delete` tasks and for lazy file handling with providers. Wire every file input/output through providers so Gradle tracks them lazily; never capture realized `File`/`Path` values or the `Project` object at configuration time.

## `Copy`

`Copy` produces files in an output directory from one or more sources. Wire `from` and `into` with providers so the copy is performed at execution time, not during configuration:

```kotlin
tasks.register<Copy>("stageAssets") {
    from(layout.projectDirectory.dir("assets"))
    into(layout.buildDirectory.dir("staged"))
    include("**/*.png")
}
```

**Configuration-cache consequence:** `from`/`into` accept `Provider<Directory>`/`Directory`/`File`-producing providers and stay lazy. Passing a realized `File` captured earlier in configuration is frozen into the configuration-cache entry; prefer the provider forms so paths re-resolve against each task's project layout.

**Default:** use `from(...)` with provider-backed directories and `into(layout.buildDirectory.dir(...))`. Filter with `include`/`exclude` rather than copying everything and deleting later.

**Anti-pattern:** calling `copy { }` from a task action (eager, not incremental, and hostile to the build cache); use a `Copy` task instead.

## `Sync`

`Sync` copies sources into a destination and **deletes any destination files not present in the sources**, making the destination exactly mirror the inputs. Use it for assembly/staging directories that must match a source layout:

```kotlin
tasks.register<Sync>("prepareDist") {
    from(layout.projectDirectory.dir("src/main/dist"))
    into(layout.buildDirectory.dir("dist"))
}
```

**Deletion semantics:** because `Sync` removes stale destination content, the destination directory must be treated as fully owned by the task. Do not put immutable hand-authored files in a `Sync` destination expecting them to survive.

**Configuration-cache consequence:** the `from`/`into` providers are resolved at execution; the resulting `Sync` and its owned output directory remain config-cache/IP compatible.

**Anti-pattern:** using `Sync` when you need additive-only copy (use `Copy`), or pointing `Sync` at a directory you share with other writers.

## `Delete`

Use `Delete` to remove files or directories as a task, with provider-backed `delete` inputs:

```kotlin
tasks.register<Delete>("cleanArtifacts") {
    delete(layout.buildDirectory.dir("artifacts"))
}

// React to a convention rather than a hard-coded path:
tasks.register<Delete>("cleanOutput") {
    delete(outDirProvider) // Provider<Directory>
}
```

**Configuration-cache consequence:** pass providers, not an eagerly resolved `File` list, to `delete(...)`; eagerly enumerating directories at configuration time is a config-cache violation and breaks isolation.

**Default:** declare a `Delete` task for cleanup instead of hand-rolling deletion in a task action with `delete { }`.

## Provider-backed files vs realized `File`/`Path`

Prefer `RegularFileProperty`/`DirectoryProperty` (provider-backed, managed) over realized `File`/`Path` values for task inputs/outputs and extension properties:

| Form | Nature | Use when |
| :--- | :--- | :--- |
| `RegularFileProperty` / `DirectoryProperty` | Lazy, managed, tracked by Gradle | Task inputs/outputs, extension properties, convention wiring |
| `Provider<RegularFile>` / `Provider<Directory>` | Lazy read-only view | Wiring derived paths (`layout.buildDirectory.dir(...)`) |
| Realized `File` / `Path` | Eager, absolute, resolved now | Only inside a task action or as a fixed static constant |

**Configuration-cache consequence:** capturing a realized `File` (or the `Project` object) in a task field or extension freezes a value that must be re-resolved (or is illegal) under the configuration cache / isolated projects. Keep file values as providers and resolve them only at an execution boundary (`@TaskAction`) or through `finalizeValueOnRead()`.

**Default:** model every file path as a `RegularFileProperty`/`DirectoryProperty` (or a `Provider` derived from `layout`), and resolve inside the action.

## Lazy file trees

Use `fileTree`, `zipTree`, and `tarTree` to model collections of files without resolving them eagerly:

```kotlin
val srcTree = layout.projectDirectory.dir("src").asFileTree
val exploded = zipTree(layout.projectDirectory.file("lib.zip"))
val tarContents = tarTree(layout.projectDirectory.file("bundle.tgz"))
```

**Archive-tree laziness:** `zipTree`/`tarTree` do **not** expand the archive at configuration time; the archive's contents are discovered when the consuming task executes (or the tree is resolved). This keeps large archives off the configuration path and preserves config-cache/IP compatibility.

**Build-cache consequence:** feeding an archive tree as a task input (`from(zipTree(...))` or as `@InputFiles`) means Gradle hashes the *contents* of the archive; a changed archive invalidates the cache. Do not copy or re-emit the archive as a whole unless the archive file itself is the intended input.

**Anti-pattern:** expanding an archive to disk during configuration, or iterating a tree at configuration time to build a file list.

## `ConfigurableFileCollection` vs `FileCollection`

| Type | Nature | Use when |
| :--- | :--- | :--- |
| `ConfigurableFileCollection` | Lazy **and mutable**; you add sources with `from(...)` | A task/extension property that is assembled during configuration and resolved at execution |
| `FileCollection` | Read-only view of a collection | Consuming an already-built collection (e.g., a task input you only read) |

```kotlin
abstract class MergeTask : DefaultTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val output: RegularFileProperty
}
```

**Default:** use `ConfigurableFileCollection` when configurers can add to the set; accept a `FileCollection` where you only consume it. Never iterate a collection at configuration time.

**This is prohibited:** configuration-time iteration or resolution of `ConfigurableFileCollection`, `FileCollection`, `FileTree`, or any provider-backed file type. Resolve/iterate only inside a task action (or after `finalizeValueOnRead()` at a deliberate boundary).

## More info

- Working with files: `gradle_docs(path="userguide/working_with_files.md")`
- Copy and archive tasks: `gradle_docs(path="userguide/working_with_files.md")`
- Lazy configuration and providers: [Managed Types and Providers](managed-types-and-providers.md)
- Gradle documentation lookup: `gradle_docs`
