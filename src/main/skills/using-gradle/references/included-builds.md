# Included Builds

Use this reference when an existing build includes other builds (`includeBuild` in settings, or `--include-build` on the command line) and you need to address their tasks, understand what they substitute, or diagnose composite-related behavior. Authoring composites is covered in [authoring-gradle-builds Composite Builds](../../authoring-gradle-builds/references/composite-builds.md); this reference covers operating them.

Read `gradle/wrapper/gradle-wrapper.properties` before version-sensitive advice; task-path addressing for included builds changes across Gradle versions.

## Recognizing an Included Build

Read `settings.gradle(.kts)` and look for `includeBuild(...)` entries (in the settings body or inside `pluginManagement { ... }`). Included builds are standalone builds: they are not subprojects, so `:projects` does not list their projects, and their configuration is not part of the root build's configuration phase.

**Do this:** record the included build's directory and root project name (`rootProject.name` in its own settings file) before addressing its tasks.

**Anti-pattern:** treating an included build's project as a subproject — expecting it in `:projects`, `:properties`, or root `dependencyInsight` output.

## Addressing Tasks in Included Builds

Address a task in an included build by prefixing the task path with the included build's root project name:

```text
:included-build-name:project-path:task-name
```

For example, with `includeBuild("../some-library")` where the included build's root project name is `some-library` and it contains a `:core` project:

```json
{"commandLine":[":some-library:core:test"]}
```

For a task in the included build's root project itself:

```json
{"commandLine":[":some-library:assemble"]}
```

**Version notes:** Gradle 7.6+ allows the included-build name to be omitted when it is unambiguous, so a task path may be addressable without the build-name prefix; Gradle 7.0–7.5 require the full `:included-build-name:...` path. Confirm with `:tasks` or `help --task` before relying on an abbreviation.

**Anti-pattern:** guessing included-build task paths; always discover them first with `:tasks` or `help --task <path>`.

## `--include-build` on the Command Line

`--include-build <dir>` includes a build for one invocation without changing settings. It is useful for one-off local experiments but is not durable automation:

```json
{"commandLine":["build","--include-build","../some-library"]}
```

**Default:** treat `--include-build` as a temporary override; anything you script or repeat belongs in `settings.gradle.kts` via `includeBuild(...)` (authored in `authoring-gradle-builds`).

**Anti-pattern:** relying on `--include-build` for a repeatable workflow; the setting vanishes on the next invocation, so a "working" build can fail later for no code change.

## What Included Builds Change When You Operate

- **Task execution:** included builds run as part of the same invocation when their tasks are selected; their task outcomes appear in build output like other tasks.
- **Dependency resolution:** a requested coordinate whose `group:name` matches an included build's project resolves to that project (automatic substitution), not the repository-sourced module; `dependencyInsight` shows the local project as the winner.
- **Plugin resolution:** plugins from an included plugin build resolve locally when `pluginManagement { includeBuild(...) }` is present.
- **Caching:** each build keeps its own configuration-cache and task-cache state; `--rerun-tasks` is documented to include included builds, so expect it to be expensive.

## Operational Pitfalls

- **Wrong paths:** included-build tasks are not addressed as `:subproject:task` of the root build; omit the included-build name only when the wrapper version supports it and the path is unambiguous.
- **Substitution surprises:** an included build can silently replace a repository module that shares its `group:name`. Check for `includeBuild` before concluding a resolution is wrong.
- **Root-build state:** included builds do not inherit root-build `gradle.properties` overrides the same way subprojects do; property and cache behavior can differ per build.
- **Verification and locking:** included builds maintain their own verification metadata and lock state; a lockfile or verification change in the root build does not automatically apply inside an included build.

**Anti-pattern:** diagnosing an included build as a "broken subproject", or assuming root-build configuration, properties, and caches govern it.

## Diagnose-to-Fix Loop for Composite Issues

1. Read `settings.gradle(.kts)` and confirm which builds are included and under which names.
2. Run `:tasks` / `:tasks --all` through the `gradle` tool to discover the included build's task paths; confirm with `help --task <path>`.
3. If a dependency resolves differently than expected, run `dependencyInsight` and check whether the winner is an included-build project.
4. Apply the smallest fix: correct task addressing, correct the `includeBuild` path or name, or route authoring changes to [authoring-gradle-builds Composite Builds](../../authoring-gradle-builds/references/composite-builds.md).
5. Re-run the diagnostic to confirm the fix.

**More info:**

- Composite builds: `gradle_docs(path="userguide/composite_builds.md")`
- Using a local fork of a module dependency: `gradle_docs(path="userguide/how_to_use_local_forks.md")`
- Task paths and graph inspection: `gradle_docs(path="userguide/task_basics.md")`
- Gradle documentation lookup: `gradle_docs`

**Cross-references:**

- Task paths and execution -> [Running Builds](running-builds.md)
- Dependency resolution and winner analysis -> [Dependencies](dependencies.md)
- Build orientation and settings reading -> [Build Orientation](build-orientation.md)
- Authoring composites -> [Composite Builds](../../authoring-gradle-builds/references/composite-builds.md)
