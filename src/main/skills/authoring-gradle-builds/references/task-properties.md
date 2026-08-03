# Task Property Annotations

Declare every value a custom task reads as an input and every file it produces as an output using the standard annotation set. Correctly annotated properties are the preconditions for task up-to-date checks and build-cache reuse; a missing or wrong annotation silently disables both.

## Canonical annotation set

Use the standard `org.gradle.api.tasks` annotation set, placing each annotation on the Kotlin **getter** of a managed property:

```kotlin
abstract class ReportTask : DefaultTask() {
    @get:Input
    abstract val format: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetsDir: DirectoryProperty

    @get:InputFiles
    abstract val extraFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}
```

The canonical set is:

| Annotation | Purpose |
| :--- | :--- |
| `@Input` | A scalar/string/enum value whose content feeds the task. |
| `@InputFile` | A single file read by the task. |
| `@InputDirectory` | A single directory whose contents feed the task. |
| `@InputFiles` | A collection of files/directories read by the task (see the plural rule below). |
| `@Classpath` | A classpath-like collection normalized for ordering and duplicates. |
| `@OutputFile` | A single file produced by the task. |
| `@OutputDirectory` | A single directory the task populates. |
| `@OutputFiles` / `@OutputDirectories` | Collections of outputs (the plural output forms DO exist). |

**Default:** use the narrowest annotation that matches the property's role. Prefer `@InputFile`/`@InputDirectory` over `@InputFiles` when the cardinality is fixed at one.

**Anti-pattern:** annotate a file-carrying property as `@Input` (content is not tracked with file semantics), or omit `@PathSensitive` on a file input so the absolute path leaks into the cache key.

## `@InputDirectories` (plural) does not exist

There is **no** `@InputDirectories` annotation. For multiple directory inputs, use `@InputFiles` with `@PathSensitive`:

```kotlin
@get:InputFiles
@get:PathSensitive(PathSensitivity.RELATIVE)
abstract val schemaDirs: ConfigurableFileCollection // from(...) several dirs
```

**This is prohibited:** writing `@InputDirectories` (plural). It is not part of the Gradle API and will not compile. Use `@InputFiles` on the collection and let `@PathSensitive` control how each entry contributes to the cache key.

## Modifiers

Apply these modifiers to refine how an input participates in caching and execution:

- `@IgnoreEmptyDirectories` — empty directories do not contribute to the cache key or trigger execution; use on `@InputFiles`/`@InputDirectory` tree inputs where empty dirs are irrelevant.
- `@NormalizeLineEndings` — treat CRLF and LF line endings as equivalent when hashing file inputs, reducing spurious cache misses across operating systems.
- `@SkipWhenEmpty` — skip the task action when the input collection is empty; implies `@Incremental`, so pair it with `InputChanges` (or use it on tasks that legitimately no-op with no input). It is common on `@InputFiles` collection inputs.
- `@Incremental` — expose the input to the task action as `InputChanges`, enabling incremental (delta-only) processing.

```kotlin
@get:InputFiles
@get:Incremental
@get:SkipWhenEmpty
abstract val templates: ConfigurableFileCollection
```

**Default:** rely on `@InputFiles` over `@Input` for file trees, and add `@Incremental`/`@SkipWhenEmpty` when the task supports true incremental processing.

## Annotation placement on Kotlin getters

In Kotlin, put annotations on the **getter** of the managed property, never on the backing field (`@get:Incremental`, not `@field:Incremental`). Gradle reads task inputs/outputs through the property accessors; placing the annotation on the getter is what Gradle detects.

**Anti-pattern:** writing `@field:Input` or an unqualified annotation on the property declaration, which Gradle does not reliably see as the input declaration.

## Validation failure behavior

Declared-input validation runs when the task is scheduled. If a declared `@InputFile`/`@OutputFile` path is unresolved, an `@Input` has no value and no convention, or an output overlaps another task's declared output, the task **fails at execution start** with a validation error rather than running with a missing or ambiguous value.

**Consequence:** a green task list is not proof a task's inputs are valid; a task that fails validation never reaches its action. Use `using-gradle` to inspect the failing task's problem report.

## Inputs are the cache key

The declared `@Input*` values (after normalization and path sensitivity) are what Gradle hashes to build the up-to-date/build-cache key. Consequences:

- **Under-declaring** an input (a file the action reads that is not annotated) means a change to that file does not invalidate the cache — stale results can be served.
- **Over-declaring** (annotating an irrelevant path or using `@Input` where content should be ignored) widens the key and reduces reuse.
- A cacheable task whose action reads undeclared state (environment, wall-clock time, network, or the `Project` model) can produce incorrect results that are served to other developers.

See [Custom Tasks](custom-tasks.md) for `@CacheableTask`, `@DisableCachingByDefault`, and path-sensitivity selection, and [Managed Types and Providers](managed-types-and-providers.md) for wiring these properties lazily.

## More info

- Task inputs and outputs: `gradle_docs(path="userguide/more_about_tasks.md")`
- Custom task types and inputs: `gradle_docs(path="userguide/custom_tasks.md")`
- Up-to-date and build cache: `gradle_docs(path="userguide/build_cache.md")`
- Gradle documentation lookup: `gradle_docs`
