# Gradle Best Practices Index

Generated from Gradle 9.6.1 documentation.
Read this first to find the relevant practice, then open the linked file for detail.

## Best Practices for Dependencies
- [Use Version Catalogs to Centralize Dependency Versions](use-version-catalogs-to-centralize-dependency-versions.md) — Version Catalogs provide a centralized, declarative way to manage dependency versions throughout a build. `#version-catalog`
- [Name Version Catalog Entries Appropriately](name-version-catalog-entries-appropriately.md) — Consistent and descriptive names in your version catalog enhance readability and maintainability across your build scripts. `#version-catalog`
- [Set up your Dependency Repositories in the Settings file](set-up-your-dependency-repositories-in-the-settings-file.md) — Declare your repositories for your plugins and dependencies in `settings.gradle.kts`. `#structuring-builds` `#repositories`
- [Don't Explicitly Depend on the Kotlin Standard Library](don-t-explicitly-depend-on-the-kotlin-standard-library.md) — The Kotlin Gradle Plugin automatically adds a dependency on the Kotlin standard library (`stdlib`) to each source set, so there is no need to declare it… `#dependencies`
- [Avoid Redundant Dependency Declarations](avoid-redundant-dependency-declarations.md) — Avoid declaring the same dependency multiple times, especially when it is already available transitively or through another configuration. `#dependencies`
- [Declare Dependencies using a single GAV (`group:artifact:version`) String](declare-dependencies-using-a-single-gav-group-artifact-version-string.md) — When declaring dependencies without a version catalog, prefer using the single GAV string notation `implementation("org.example:library:1.0")`. Avoid using the… `#dependencies`
- [Use Content Filtering with multiple Repositories](use-content-filtering-with-multiple-repositories.md) — When using multiple repositories in a build, use repository content filtering to ensure that dependencies are resolved from an appropriate repository. `#repositories` `#dependencies`
- [Apply Exclusions Narrowly](apply-exclusions-narrowly.md) — When excluding transitive dependencies, apply exclusions as narrowly as possible. `#dependencies`

## General Gradle Best Practices
- [Use Kotlin DSL](use-kotlin-dsl.md) — Prefer the Kotlin DSL (`build.gradle.kts`) over the Groovy DSL (`build.gradle`) when authoring new builds or creating new subprojects in existing builds. `#kotlin-dsl`
- [Use the Latest Minor Version of Gradle](use-the-latest-minor-version-of-gradle.md) — Stay on the latest minor version of the major Gradle release you're using, and regularly update your plugins to the latest compatible versions. `#plugins`
- [Apply Plugins Using the `plugins` Block](apply-plugins-using-the-plugins-block.md) — You should always use the `plugins` block to apply plugins in your build scripts. `#structuring-builds`
- [Don't Assume your Plugin is Applied after Another](don-t-assume-your-plugin-is-applied-after-another.md) — Gradle's plugin application is deterministic but opaque. It is difficult to reason about, especially across multiple build scripts, projects, convention… `#structuring-builds` `#plugins`
- [Do Not Use Internal APIs](do-not-use-internal-apis.md) — Do not use APIs from a package where any segment of the package is `internal`, or types that have `Internal` or `Impl` as a suffix in the name. `#upgrades`
- [Set Build Flags in `gradle.properties`](set-build-flags-in-gradle-properties.md) — Set Gradle build property flags in the `gradle.properties` file. `#properties`
- [Name Your Root Project](name-your-root-project.md) — Always name your root project in the `settings.gradle(.kts)` file. `#settings`
- [Do not use `gradle.properties` in subprojects](do-not-use-gradle-properties-in-subprojects.md) — Do not place a `gradle.properties` file inside subprojects to configure your build. `#properties`
- [Avoid `afterEvaluate`](avoid-afterevaluate.md) — Do not use `project.afterEvaluate {}` to configure tasks, wire properties, or react to plugin application. Use lazy properties and `pluginManager.withPlugin()`… `#task-configuration-avoidance` `#plugins` `#structuring-builds`

## Best Practices for Performance
- [Prefer the `-bin` Gradle Distribution](prefer-the-bin-gradle-distribution.md) — Gradle publishes two distribution variants for each release: `-bin` (binaries only) and `-all` (binaries, sources, and documentation). For most builds, you… `#upgrades` `#wrapper`
- [Use UTF-8 File Encoding](use-utf-8-file-encoding.md) — Set `UTF-8` as the default file encoding to ensure consistent behavior across platforms. `#properties` `#caching`
- [Use the Build Cache](use-the-build-cache.md) — Use the Build Cache to save time by reusing outputs produced by previous builds. `#properties` `#caching`
- [Use the Configuration Cache](use-the-configuration-cache.md) — Use the Configuration Cache to significantly improve build performance by caching the result of the configuration phase and reusing it in subsequent builds. `#properties` `#caching`
- [Avoid Expensive Computations in Configuration Phase](avoid-expensive-computations-in-configuration-phase.md) — Avoid expensive computations in the configuration phase, instead, move them to task actions. `#tasks`

## Best Practices for Security
- [Best Practices for Security](best-practices-for-security.md)

## Best Practices for Structuring Builds
- [Modularize Your Builds](modularize-your-builds.md) — Modularize your builds by splitting your code into multiple projects. `#structuring-builds`
- [Do Not Put Source Files in the Root Project](do-not-put-source-files-in-the-root-project.md) — Do not put source files in your root project; instead, put them in a separate project. `#structuring-builds`
- [Favor `build-logic` Composite Builds for Build Logic](favor-build-logic-composite-builds-for-build-logic.md) — You should set up a Composite Build (often called an "included build") to hold your build logic---including any custom plugins, convention plugins, and other… `#structuring-builds` `#composite-builds`
- [Avoid Unintentionally Creating Empty Projects](avoid-unintentionally-creating-empty-projects.md) — When using a hierarchical directory structure to organize your Gradle projects, make sure to avoid unintentionally creating empty projects in your build. `#structuring-builds`
- [Use Convention Plugins for Common Build Logic](use-convention-plugins-for-common-build-logic.md) — Use convention plugins to encapsulate and reuse shared build logic across multiple projects in your build. `#structuring-builds`

## Best Practices for Tasks
- [Avoid DependsOn](avoid-dependson.md) — The task dependsOn method should only be used for lifecycle tasks (tasks without task actions). `#tasks` `#inputs-and-outputs` `#up-to-date-checking`
- [Favor `@CacheableTask` and `@DisableCachingByDefault` over `cacheIf(Spec)` and `doNotCacheIf(String, Spec)`](favor-cacheabletask-and-disablecachingbydefault-over-cacheif-spec-and-donotcacheif-string-spec.md) — The `cacheIf` and `doNotCacheIf` methods should only be used in situations where the cacheability of a task varies between different task instances or cannot… `#tasks` `#caching`
- [Do not call `get()` on a Provider outside a Task action](do-not-call-get-on-a-provider-outside-a-task-action.md) — When configuring tasks and extensions do not call `get()` on a provider, use `map()`, or `flatMap()` instead. `#tasks` `#inputs-and-outputs`
- [Group and Describe custom Tasks](group-and-describe-custom-tasks.md) — When defining custom task types or registering ad-hoc tasks, always set a clear `group` and `description`. `#tasks`
- [Avoid using eager APIs on File Collections](avoid-using-eager-apis-on-file-collections.md) — When working with Gradle's file collection types, be careful to avoid triggering dependency resolution during the configuration phase. `#tasks` `#inputs-and-outputs` `#configurations`
- [Don't resolve Configurations before Task Execution](don-t-resolve-configurations-before-task-execution.md) — Resolving configurations before the task execution phase can lead to incorrect results and slower builds. `#tasks` `#inputs-and-outputs` `#configurations`
- [Use `@PathSensitivity.NONE` for file inputs and `@PathSensitivity.RELATIVE` for directories](use-pathsensitivity-none-for-file-inputs-and-pathsensitivity-relative-for-directories.md) — Use `@PathSensitivity.NONE` for file inputs and `@PathSensitivity.RELATIVE` for directory inputs. `#tasks` `#inputs-and-outputs`
- [Use unique output files and directories](use-unique-output-files-and-directories.md) — Overlapping output files or directories cause tasks to rerun unnecessarily and waste work. `#tasks` `#inputs-and-outputs`

## Best Practices for Testing
- [Best Practices for Testing](best-practices-for-testing.md)

## Browse by Tag
- `#caching` — use-utf-8-file-encoding, use-the-build-cache, use-the-configuration-cache, favor-cacheabletask-and-disablecachingbydefault-over-cacheif-spec-and-donotcacheif-string-spec
- `#composite-builds` — favor-build-logic-composite-builds-for-build-logic
- `#configurations` — avoid-using-eager-apis-on-file-collections, don-t-resolve-configurations-before-task-execution
- `#dependencies` — don-t-explicitly-depend-on-the-kotlin-standard-library, avoid-redundant-dependency-declarations, declare-dependencies-using-a-single-gav-group-artifact-version-string, use-content-filtering-with-multiple-repositories, apply-exclusions-narrowly
- `#inputs-and-outputs` — avoid-dependson, do-not-call-get-on-a-provider-outside-a-task-action, avoid-using-eager-apis-on-file-collections, don-t-resolve-configurations-before-task-execution, use-pathsensitivity-none-for-file-inputs-and-pathsensitivity-relative-for-directories, use-unique-output-files-and-directories
- `#kotlin-dsl` — use-kotlin-dsl
- `#plugins` — use-the-latest-minor-version-of-gradle, don-t-assume-your-plugin-is-applied-after-another, avoid-afterevaluate
- `#properties` — set-build-flags-in-gradle-properties, do-not-use-gradle-properties-in-subprojects, use-utf-8-file-encoding, use-the-build-cache, use-the-configuration-cache
- `#repositories` — set-up-your-dependency-repositories-in-the-settings-file, use-content-filtering-with-multiple-repositories
- `#settings` — name-your-root-project
- `#structuring-builds` — set-up-your-dependency-repositories-in-the-settings-file, apply-plugins-using-the-plugins-block, don-t-assume-your-plugin-is-applied-after-another, avoid-afterevaluate, modularize-your-builds, do-not-put-source-files-in-the-root-project, favor-build-logic-composite-builds-for-build-logic, avoid-unintentionally-creating-empty-projects, use-convention-plugins-for-common-build-logic
- `#task-configuration-avoidance` — avoid-afterevaluate
- `#tasks` — avoid-expensive-computations-in-configuration-phase, avoid-dependson, favor-cacheabletask-and-disablecachingbydefault-over-cacheif-spec-and-donotcacheif-string-spec, do-not-call-get-on-a-provider-outside-a-task-action, group-and-describe-custom-tasks, avoid-using-eager-apis-on-file-collections, don-t-resolve-configurations-before-task-execution, use-pathsensitivity-none-for-file-inputs-and-pathsensitivity-relative-for-directories, use-unique-output-files-and-directories
- `#up-to-date-checking` — avoid-dependson
- `#upgrades` — do-not-use-internal-apis, prefer-the-bin-gradle-distribution
- `#version-catalog` — use-version-catalogs-to-centralize-dependency-versions, name-version-catalog-entries-appropriately
- `#wrapper` — prefer-the-bin-gradle-distribution