---
name: gradle-build-authoring
description: |
  Authoritative guidance for creating, modifying, and managing Gradle build files, build logic, and build engineering workflows.

  ## Positive Triggers (when to activate)
  - User asks to create or modify a build.gradle(.kts) or settings.gradle(.kts) file
  - User asks to add, remove, or update dependencies
  - User asks to create a new Gradle module or subproject
  - User asks about build configuration, DSL patterns, or version catalogs
  - User asks about performance optimization, caching, or build scans
  - User asks about testing configuration, CI/CD builds, or publishing
  - User asks about custom tasks, worker API, or convention plugins
  - User asks about dependency locking, toolchains, or compiler options
  - User asks to design or refactor build logic
  - Agent needs to register or configure a Gradle plugin
  - Agent needs to add or modify custom task types
  - Agent needs to configure multi-project builds or build-logic

  ## Negative Triggers (when NOT to activate)
  - User needs to run builds, tests, or diagnostic tasks (route to gradle skill)
  - User needs to introspect project structure or task graphs (route to gradle skill)
  - User needs dependency lookup or version resolution (route to managing_gradle_dependencies)
  - User needs to explore dependency sources (route to exploring_dependency_sources)
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "1.0"
---

# Authoritative Gradle Build Authoring & Build Logic Engineering

Designs, writes, and refactors Gradle build scripts (`build.gradle.kts`, `settings.gradle.kts`), creates modules, optimizes build performance, and establishes idiomatic build conventions using managed orchestration and structured diagnostics.

## Constitution

- **ALWAYS** prefer Kotlin DSL (`.kts`) unless the project explicitly uses Groovy.
- **ALWAYS** use lazy APIs (`tasks.register<MyTask>("myTask")`) instead of eager APIs (`tasks.create<MyTask>("myTask")`) to maintain configuration cache compatibility and configuration performance.
- **ALWAYS** use version catalogs (`gradle/libs.versions.toml`) for dependency management when present.
- **ALWAYS** check for existing conventions in the current project before proposing changes.
- **ALWAYS** use safe navigation (`?.url?.toString()`) and provide fallback values when accessing `ArtifactRepository` URLs in Gradle init scripts or plugins to prevent `NullPointerException`.
- **NEVER** use `allprojects`/`subprojects` blocks; these create tight coupling — use convention plugins applied selectively.
- **NEVER** access the `Project` object directly inside task actions; use lazy properties (`Property<T>`, `Provider<T>`) and the `Task` APIs for late binding.
- **NEVER** resolve configurations during the configuration phase; defer resolution inside `TaskAction` or `afterEvaluate` with a clear justification.
- **ALWAYS** use `:properties --property <name>` for surgical property extraction from build scripts.
- **ALWAYS** use `gradle_docs(query="tag:dsl ...", projectRoot="/path/to/project")` for authoritative DSL reference lookups when designing build configurations.

## Directives

### Idiomatic DSL Patterns

These conventions define how build scripts should be authored to maximize correctness, performance, and maintainability.

#### Prefer `register` over `create`
Use `tasks.register<MyTask>("myTask")` for deferred task configuration. Only use `tasks.getByName()` / `tasks.named()` when you need to interact with an already-defined task. Never use `tasks.create()` which eagerly configures even if the task is never executed.

#### Use Type-Safe Accessors
Prefer type-safe DSL blocks like `tasks.test { ... }` or `tasks.named<Test>("test") { ... }` over string-based accessor methods like `tasks.getByName("test")`. Type-safe accessors provide IDE support, compile-time checking, and discoverability.

#### Use Lazy Properties
Employ `Property<T>` and `Provider<T>` APIs for late binding and configuration cache compatibility. Avoid eager evaluation in task inputs; declare them as `@get:Input val myProp: Property<String> = objects.property(String::class)`.

#### Use Version Catalogs
Centralize dependency declarations in `gradle/libs.versions.toml`. Reference them in build scripts via `libs.<catalogName>.<artifactId>` (e.g., `implementation(libs.guava)`). This provides a single source of truth for versions across the multi-project build.

#### Avoid `allprojects`/`subprojects`
These blocks create tight coupling between the root project and every subproject. Instead, use convention plugins applied selectively via `plugins { id("com.example.convention") }` in the specific projects that need them.

#### Enable Configuration Cache
Ensure build logic avoids accessing mutable project state inside task actions. Declare task inputs/outputs correctly with `@Input`, `@InputDirectory`, `@OutputFile`, `@OutputDirectories`, `@Internal`, and other incremental build annotations. Use `CacheableTask` for tasks eligible for the build cache.

#### Use Specific Annotations
Properly label task properties:
- `@Input` — primitive values or strings that affect task output.
- `@InputFiles` / `@InputDirectory` — files/directories read by the task.
- `@OutputFiles` / `@OutputDirectory` — files/directories produced by the task.
- `@Internal` — metadata not affecting outputs (e.g., computed intermediates).
This ensures correct incremental build behavior and cacheability.

#### Minimize Logic in Build Scripts
Move complex logic into convention plugins or `build-logic`. Keep `build.gradle.kts` files short and readable — they should describe *what* the build does, not *how*. Delegate intricate computation to precompiled script plugins in `build-logic/src/main/kotlin`.

#### Safe Navigation for Repository URLs
When configuring repositories in Gradle init scripts or plugins, always guard against null URLs:
```kotlin
val url = repository.url.toString()
    ?: error("Repository $repository has no URL")
```

### Version Catalog Structure

Organize `gradle/libs.versions.toml` into `[versions]`, `[libraries]`, `[bundles]`, and `[plugins]`. Use descriptive kebab-case aliases such as `slf4j-api` and `junit-jupiter`; Gradle generates dot-separated type-safe accessors such as `libs.slf4j.api` and `libs.junit.jupiter`. Reuse `version.ref` entries for artifacts that must stay aligned, and use bundles only for dependencies that consumers normally add together.

For an included `build-logic` build, import the root catalog in `build-logic/settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
```
Use the imported catalog for the convention plugin project's dependencies. When plugin implementation code needs catalog entries from the consuming build, obtain the named catalog through `VersionCatalogsExtension` rather than hard-coding coordinates.

### Configuration Cache Compatibility

Keep configuration and execution state separate. Task actions must not capture or access `Project`; model every required value as a declared `Property<T>`, `Provider<T>`, or file property and wire it during configuration without calling `get()`. Do not resolve configurations, inspect the file system, read environment variables eagerly, or run external processes during configuration. Use `ProviderFactory`, `ValueSource`, or provider-backed `exec` APIs so external state becomes a lazy, traceable input. Declare task inputs and outputs completely, then verify with `--configuration-cache`.

### JDK Toolchain Management

Declare the compilation JDK independently of the JDK that runs Gradle:
```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
```
Gradle can auto-provision a matching JDK when no local installation satisfies the request. Configure a download repository in `settings.gradle.kts`, commonly with the `org.gradle.toolchains.foojay-resolver-convention` plugin, and pin the resolver plugin version according to project conventions. Add a vendor constraint only when the build genuinely depends on a vendor-specific JDK.

### Best-Practices Consultation

Start with `references/best-practices/_index.md`. Choose the relevant area or browse its tags, then open the linked detail file before changing build logic. Treat the detail page as the focused implementation guidance and use version-specific Gradle documentation when a practice depends on the project's Gradle version.

## Workflows

### Creating a New Module

1. Discover the existing module layout (e.g., by listing projects). Place the new module alongside related ones.

2. Create directory structure:
   ```powershell
   New-Item -ItemType Directory -Force -Path "<module-name>/src/main/kotlin"
   New-Item -ItemType Directory -Force -Path "<module-name>/src/test/kotlin"
   ```

3. Add to `settings.gradle.kts`:
   ```kotlin
   include(":<module-name>")
   ```
   Place the include at an appropriate location near related modules.

4. Create `build.gradle.kts` with idiomatic patterns:
   - Apply convention plugins where applicable (check existing modules for convention plugin usage).
   - Set up standard JVM/Kotlin configuration via convention plugins rather than duplicating config.
   - Declare dependencies using version catalogs (`libs.<...>`).
   - Configure standard test setup through convention plugins.
   - If the module is a library, apply publishing conventions via convention plugins.

5. Verify the module is recognized by listing its tasks.

### Performance Audit

1. Check configuration cache status:
   ```
   gradle(commandLine=[":help", "--configuration-cache"])
   ```
   Review any warnings about incompatible plugins or task implementations.

2. Run a build with build scan enabled to collect performance data:
   ```
   gradle(commandLine=["clean", "build", "--scan"])
   ```
   The build scan URL will be reported in the output. Analyze the scan for:
   - Slow tasks and their execution times.
   - Configuration-phase vs. execution-phase time split.
   - Cache misses and reasons.
   - Dependency resolution bottlenecks.

3. Analyze task compatibility and identify violations:
   - Tasks that cannot be cached (`NonCacheableTask`).
   - Tasks that access project state inside actions.
   - Tasks missing proper incremental build annotations.

4. Propose fixes:
   - Migrate to lazy APIs (`Property<T>`, `Provider<T>`) for configuration cache compliance.
   - Use `@Internal`/`@Input`/`@Output` annotations correctly for incremental builds.
   - Replace eager task creation with `register`.

5. Consult the generated best-practices reference first: read `references/best-practices/_index.md`, pick the relevant practice by area or tag, then open its detail file.

6. For version-specific or deeper guidance, use `gradle_docs(query="tag:best-practices <term>", projectRoot="/path/to/project")`.

### Build Logic Refactoring

1. Identify duplicated configuration and the plugins, extensions, and task types that own it; preserve module-specific differences instead of forcing a universal convention.
2. Read `../gradle/references/common_build_patterns.md` and select the existing composite-build or convention-plugin pattern that matches the project.
3. Move shared defaults into a focused convention plugin under `build-logic`, using lazy APIs and typed extensions rather than cross-project configuration.
4. Register `build-logic` through `pluginManagement { includeBuild("build-logic") }` in `settings.gradle.kts`, following the project's existing included-build layout.
5. Apply the convention plugin only to participating modules, remove the duplicated declarations, and retain explicit overrides in each consumer.
6. Verify the affected modules compile and that the refactored logic remains configuration-cache compatible.

### Adding a Dependency

1. Use `managing_gradle_dependencies` to find and verify the correct group, artifact, and version.
2. If the project uses a version catalog, add the version and library alias to the appropriate sections of `gradle/libs.versions.toml`; preserve existing naming and version-sharing conventions.
3. Add the dependency to the consuming module's `build.gradle.kts` with its type-safe accessor, for example `implementation(libs.slf4j.api)`.
4. Run the narrowest quick compilation task for the consuming module and resolve catalog accessor or dependency errors before continuing.

### Configuring Testing

1. Configure the relevant `Test` task with `useJUnitPlatform()` and project-appropriate test logging.
2. Add include or exclude filtering only when the suite requires stable, explicit patterns.
3. Tune task properties such as `maxParallelForks` to the available resources without introducing oversubscription or test-order dependencies.
4. Read `references/testing-configuration.md` for JUnit Platform, filtering, Kotlin Multiplatform, and test fixture details.

### Setting Up CI/CD Builds

1. Configure CI daemon and resource settings, including `--no-daemon` and a runner-appropriate `--max-workers` value.
2. Apply and configure the Develocity plugin so CI builds can publish build scans under an explicit terms-of-use and access policy.
3. Configure the local build cache for persistent runners and an authenticated remote cache for ephemeral or shared runners.
4. Read `references/ci-cd-builds.md` for daemon, parallelism, cache, and pipeline patterns.

### Enabling Dependency Locking

1. Configure `dependencyLocking { lockAllConfigurations() }` or activate locking only for the configurations that require it.
2. Resolve the required configurations with `--write-locks` to generate or update lock files, then include those files with the build change.
3. Add a CI build step that resolves dependencies without `--write-locks`, so missing or inconsistent lock state fails verification.
4. Read `references/dependency-locking.md` for lock generation, selective updates, and maintenance details.

### Publishing Artifacts

1. Apply the `maven-publish` plugin and the `signing` plugin when the target repository requires signed artifacts.
2. Create the required `MavenPublication` entries from the appropriate software components and provide complete POM metadata.
3. Configure signing from protected Gradle properties or environment-backed providers; never commit private keys or credentials.
4. Configure the publishing repository and obtain credentials lazily from providers.
5. Read `references/artifact-publishing.md` for publication, signing, repository, and Maven Central requirements.

## When to Use

- **Build Script Authoring & Modification**: When writing or modifying `build.gradle.kts`, `settings.gradle.kts`, or build logic plugins.
- **Dependency & Plugin Management**: When adding, removing, or updating dependencies, plugins, or version catalog entries.
- **New Module Creation**: When adding a new project or module to a multi-project build.
- **Build DSL Configuration**: When configuring build logic, DSL patterns, or version catalogs for correct and performant builds.
- **Performance Optimization**: When builds are slow, misusing the configuration cache, or failing during the configuration phase — use build scans and audit workflows to diagnose.
- **Testing & Publishing Configuration**: When setting up test tasks, CI/CD pipelines, or publishing conventions.
- **Custom Tasks & Worker API**: When designing or implementing custom task types, parallel task execution, or worker API usage.
- **Convention Plugins**: When creating, applying, or refactoring convention plugins for common build logic.
- **Multi-Project Build Structure**: When configuring `settings.gradle.kts`, `build-logic`, composite builds, or included builds.
- **Dependency Locking & Toolchains**: When setting up dependency locking, Gradle toolchain management, or compiler option configuration.
- **Dependency Management**: When adding, updating, or managing dependencies and their catalog aliases.
- **Testing Configuration**: When configuring test frameworks, test suites, filtering, logging, or `Test` task behavior.
- **CI/CD Build Setup**: When preparing reproducible, observable, and resource-bounded Gradle builds for CI pipelines.
- **Dependency Locking**: When implementing or maintaining dependency locks for reproducible resolution.
- **Artifact Publishing**: When configuring publications, signing, credentials, or artifact repositories.
- **Build Scan Analysis**: When enabling or analyzing build scans to investigate build performance and failures.

## Examples

### Create a new sub-project module

1. Map the project structure to find where the new module fits best alongside related ones.

2. Create the directory structure:
   ```powershell
   New-Item -ItemType Directory -Force -Path subproject/src/main/kotlin
   New-Item -ItemType Directory -Force -Path subproject/src/test/kotlin
   ```

3. Register the module in `settings.gradle.kts`:
   ```kotlin
   // Append to settings.gradle.kts:
   include(":subproject")
   ```

4. Create `subproject/build.gradle.kts`:
   ```kotlin
   plugins {
       java
   }

   dependencies {
       implementation(libs.guava)
   }
   ```
   Reasoning: Applies the Java plugin and declares a dependency via version catalog, following project conventions.

5. Verify the module is recognized by listing its tasks to confirm Gradle discovers it.

### Add a dependency via version catalog

```json
{
  "tool": "edit_file",
  "path": "gradle/libs.versions.toml",
  "content": "[versions]\nslf4j = \"2.0.16\"\n\n[libraries]\nslf4j-api = { module = \"org.slf4j:slf4j-api\", version.ref = \"slf4j\" }\n"
}
```
// Reasoning: After `managing_gradle_dependencies` verifies the coordinate and version, this creates the kebab-case alias that Gradle exposes as `libs.slf4j.api`.

```json
{
  "tool": "edit_file",
  "path": "app/build.gradle.kts",
  "content": "dependencies {\n    implementation(libs.slf4j.api)\n}\n"
}
```
// Reasoning: Adds the catalog-backed dependency to the consuming module; follow it with the narrowest module compilation task.

### Configure JUnit 5 testing

```json
{
  "tool": "edit_file",
  "path": "build.gradle.kts",
  "content": "tasks.test {\n    useJUnitPlatform()\n    maxParallelForks = 2\n    testLogging {\n        events(\"passed\", \"skipped\", \"failed\")\n        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL\n    }\n}\n"
}
```
// Reasoning: Runs JUnit 5 on the JUnit Platform, bounds test process parallelism, and emits actionable failure details.

### Set up build scans for CI

```json
{
  "tool": "edit_file",
  "path": "settings.gradle.kts",
  "content": "plugins {\n    id(\"com.gradle.develocity\") version \"4.2\"\n}\n\ndevelocity {\n    buildScan {\n        termsOfUseUrl.set(\"https://gradle.com/help/legal-terms-of-use\")\n        termsOfUseAgree.set(\"yes\")\n        publishing.onlyIf { providers.environmentVariable(\"CI\").isPresent }\n    }\n}\n"
}
```
// Reasoning: Enables Develocity build scans only in CI while making the publication policy explicit.

### Enable dependency locking

```json
{
  "tool": "edit_file",
  "path": "build.gradle.kts",
  "content": "dependencyLocking {\n    lockAllConfigurations()\n}\n"
}
```
// Reasoning: Activates locking for every resolvable configuration; generate lock state with `dependencies --write-locks`, then verify it in CI without that flag.

### Publish to Maven Central

```json
{
  "tool": "edit_file",
  "path": "build.gradle.kts",
  "content": "plugins {\n    `java-library`\n    `maven-publish`\n    signing\n}\n\njava {\n    withSourcesJar()\n    withJavadocJar()\n}\n\npublishing {\n    publications {\n        create<MavenPublication>(\"mavenJava\") {\n            from(components[\"java\"])\n            pom {\n                name.set(\"Example Library\")\n                description.set(\"Reusable example components\")\n                url.set(\"https://github.com/example/library\")\n                licenses {\n                    license {\n                        name.set(\"The Apache License, Version 2.0\")\n                        url.set(\"https://www.apache.org/licenses/LICENSE-2.0.txt\")\n                    }\n                }\n            }\n        }\n    }\n    repositories {\n        maven {\n            name = \"MavenCentral\"\n            url = uri(\"https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/\")\n            credentials(PasswordCredentials::class)\n        }\n    }\n}\n\nsigning {\n    sign(publishing.publications[\"mavenJava\"])\n}\n"
}
```
// Reasoning: Creates source, Javadoc, signed Maven artifacts with required POM metadata and lets Gradle obtain `MavenCentralUsername` and `MavenCentralPassword` from protected properties.

### Configure JDK toolchains

```json
{
  "tool": "edit_file",
  "path": "build.gradle.kts",
  "content": "java {\n    toolchain {\n        languageVersion.set(JavaLanguageVersion.of(21))\n    }\n}\n"
}
```
// Reasoning: Compiles and tests with JDK 21 independently of the JDK that launches Gradle; configure the Foojay resolver in `settings.gradle.kts` when auto-provisioning is required.

## Troubleshooting

- **Configuration Cache Violations**: When the configuration cache reports incompatibilities, examine the referenced task/action and replace direct `Project` access with `Task`-level APIs or `@Nested`/`@Optional` annotations.
- **Eager Configuration Slowdowns**: If build startup is slow, audit for `tasks.create()` calls and migrate them to `tasks.register()`.
- **Version Catalog Errors**: Ensure catalog references match the exact entry name defined in `libs.versions.toml`; typos in catalog names cause unresolved symbol errors.
- **Plugin Application Conflicts**: When two plugins apply conflicting defaults, use `plugins { id(...) apply false }` to defer application, then manually configure.

## Resources

- [Gradle Best Practices](references/best-practices/_index.md) — Categorized index of Gradle best practices by area and tag; open the linked detail file for the selected practice.
- [Common Build Patterns](../gradle/references/common_build_patterns.md) — Common patterns for multi-project builds and convention plugins.
- [Version Catalogs](references/version-catalogs.md) — Version catalog structure and usage.
- [Testing Configuration](references/testing-configuration.md) — Test framework and task configuration.
- [CI/CD Builds](references/ci-cd-builds.md) — CI/CD build best practices.
- [Dependency Locking](references/dependency-locking.md) — Dependency locking for reproducibility.
- [Worker API](references/worker-api.md) — Worker API for parallel task execution.
- [JDK Toolchains](references/jdk-toolchains.md) — JDK toolchain configuration.
- [Build Scans](references/build-scans.md) — Build scan setup and analysis.
- [Continuous Builds](references/continuous-builds.md) — Continuous build mode usage.
- [Kotlin Compiler Options](references/kotlin-compiler-options.md) — Kotlin compiler options configuration.
- [Artifact Publishing](references/artifact-publishing.md) — Artifact publishing configuration.
