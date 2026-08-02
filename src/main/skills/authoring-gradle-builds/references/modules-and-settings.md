# Modules and Settings

Define the build graph in `settings.gradle.kts`, keep project boundaries explicit, and put reusable behavior in convention plugins. Prefer a small root project that owns build-wide coordination, not source code or hidden mutation. For the operating-side filesystem and project model, see [using-gradle Build Orientation](../../using-gradle/references/build-orientation.md); do not duplicate that first-contact workflow here.

## Multi-Project Hierarchy

Use a root project plus one project per independently built component. Keep directory names and project paths aligned unless the repository has a deliberate mapping requirement.

```text
root/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── app/build.gradle.kts
├── core/build.gradle.kts
└── build-logic/
```

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "my-project"
include(":app", ":core")
```

**Default:** Model a component as a project when it has its own sources, dependencies, lifecycle, or publication boundary. Apply behavior explicitly through convention plugins.

**Anti-pattern:** Create a project only to hold a directory, or rely on root-wide `subprojects` and `allprojects` mutation to make projects consistent. Those patterns hide ownership, couple evaluation, and complicate isolated-projects diagnostics.

See the frozen corpus entries [Modularize your builds](best-practices/modularize-your-builds.md), [Do not put source files in the root project](best-practices/do-not-put-source-files-in-the-root-project.md), and [Avoid unintentionally creating empty projects](best-practices/avoid-unintentionally-creating-empty-projects.md) for rationale. Do not restate those entries when their approved pattern is sufficient.

## `settings.gradle.kts`

Use settings for build identity, project inclusion, plugin resolution, dependency repositories, and included build wiring. Keep project-specific source and task configuration in project build scripts or convention plugins.

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "my-project"
include(":app", ":core")
```

**Default:** Name the root project explicitly and centralize repositories in settings. Use `include` for real projects only.

**Anti-pattern:** Leave the root name derived from a directory name, declare repositories ad hoc in subprojects, or use `buildscript {}` plus `apply plugin:` when the plugins DSL supports the plugin.

See [Name your root project](best-practices/name-your-root-project.md), [Set up dependency repositories in the settings file](best-practices/set-up-your-dependency-repositories-in-the-settings-file.md), and [Apply plugins using the `plugins` block](best-practices/apply-plugins-using-the-plugins-block.md).

## Naming the Root Project

Set `rootProject.name` in every authored multi-project build. The name affects project identity, generated metadata, and diagnostics; it must not depend on the checkout directory.

```kotlin
rootProject.name = "my-project"
```

**Default:** Use the repository's stable product or build name, with the naming convention already used by published artifacts and documentation.

**Anti-pattern:** Infer identity from `File(".").name`, rename the root opportunistically, or use a name that changes between local checkouts.

## Avoid Empty Projects

A hierarchical directory layout can cause Gradle to discover or include projects that contain no build logic or source. Include only intentional project boundaries.

```kotlin
rootProject.name = "my-project"
include(":app", ":core")
// Do not include ":docs" or ":samples" unless each is an intentional Gradle project.
```

**Default:** Keep non-build directories outside the project graph, or map them explicitly only when they are real projects.

**Anti-pattern:** Include every directory, create a placeholder `build.gradle.kts` to silence discovery, or put application/library source in the root project.

## Settings properties and included directories

Pass project properties with `-Pname=value` or `ORG_GRADLE_PROJECT_name=value`; include them in the property-precedence review before relying on an environment or command-line override. Gradle 9.0 fails when an included build or included project directory is missing or read-only, so validate those directories during authoring rather than treating them as optional placeholders.

## Project Isolation

Project isolation is an experimental Gradle mode that isolates mutable project state so Gradle can configure projects independently. Enable diagnostics deliberately for Gradle 9.x builds:

```properties
# gradle.properties
org.gradle.unsafe.isolated-projects=true
```

Write build logic that remains valid with isolation enabled even when the build does not require the flag in production. A project plugin must not read or mutate another project's `Project` object. Publish cross-project data through Gradle's model instead:

```kotlin
// producer/build.gradle.kts
val generatedElements by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    }
}

artifacts {
    add(generatedElements.name, layout.buildDirectory.dir("generated").map { it.asFile })
}

// consumer/build.gradle.kts
dependencies {
    implementation(project(":producer-module"))
}
```

Use project dependencies and consumable/resolvable configurations to describe relationships. For custom artifacts or advanced variant matching, see [Configurations and Variants](configurations-and-variants.md). Use `Provider`, `Property`, artifact views, and explicit task dependencies to carry values and work. Do not reach across projects during task execution.

### Low-level / Legacy Compatibility
In rare cases where variant-aware resolution is not possible or when supporting legacy builds, you can use the explicit configuration form:
```kotlin
dependencies {
    add("implementation", project(path = ":producer", configuration = "generatedElements"))
}
```
This form bypasses variant-aware selection and is not the preferred pattern for isolation-safe builds.

**Default:** Keep project configuration decoupled; test Gradle 9.x builds with isolated-projects diagnostics; model cross-project outputs as published variants/configurations and task inputs.

**Anti-pattern:** Read `project(":other").tasks` or `project(":other").extensions`, keep cross-project mutable state, mutate projects from `subprojects`, call `evaluationDependsOnChildren()`, or call `project(":other")` from a task action. Do not use evaluation order as a data-transfer mechanism.

All projects may still be configured in isolated-projects mode. Configuration-on-demand does not make isolation safe, and isolation does not itself speed up task execution. Keep configuration-cache requirements separate from project-isolation requirements.

### Version notes

- **Gradle 9.x:** Project isolation is experimental, and diagnostics and supported APIs can change between 9.x minors. Read the current wrapper before enabling it: **Version-sensitive field-guide rule:** Read `gradle/wrapper/gradle-wrapper.properties` before applying the `org.gradle.unsafe.isolated-projects` rule.
- **Gradle 9.6:** Hidden parent-project property lookup is warned about and is slated for removal; use explicit property ownership and project wiring instead.
- **Gradle 8.x:** Do not enable the experimental flag as a baseline. Use decoupled project logic and configuration-cache-compatible providers as the fallback.
- **Gradle 7.x:** Use explicit project dependencies, task inputs, and provider wiring. Do not rely on project-isolation support; preserve decoupling for future migration.

**More info:**

- `gradle_docs`: `tag:userguide`, path `userguide/isolated_projects.md`, term `cross-project access`
- Settings and hierarchy: `gradle_docs` `tag:userguide`, path `userguide/multi_project_builds.md`
- Plugin management: `gradle_docs` `tag:userguide`, path `userguide/plugins.md`, search `pluginManagement plugins block`
- Gradle documentation lookup: `gradle_docs`
