<!--
class: authored-local
skill: using-gradle
-->
# Dependencies

Audits the resolved dependency graph, resolves version conflicts, and manages library updates.

For trivial dependency additions or version bumps, see the cheat sheet in [SKILL.md](../SKILL.md).

## Configuration Scopes for Feature Developers

Use the following configurations to isolate the scope of inspection via `inspect_dependencies`. Discover the actual configuration names first for Android variants and Kotlin Multiplatform source sets; plugin, version, target, and variant determine those names.

| Configuration | Purpose | Use Case |
| :--- | :--- | :--- |
| `api` | Exposed compile and runtime dependency scope | Inspect libraries intentionally exported to consumers |
| `implementation` | Internal compile/runtime dependency scope | Inspect project-only dependencies |
| `compileOnly` | Compile-time-only dependency | Check APIs absent at runtime |
| `runtimeOnly` | Runtime-only dependency | Check providers or implementations not needed to compile |
| `testImplementation` | Test compile/runtime dependency | Audit test-scoped libraries |
| `compileClasspath` / `runtimeClasspath` | Resolved classpath views | Check compile pollution or runtime conflicts |
| `testCompileClasspath` | Resolved test classpath view | Audit test-scoped libraries |
| Android/KMP configurations | Plugin-created variant or source-set scopes | Discover with `:tasks`, `:dependencies`, or `:configurations`; never assume Java names |
## Repositories and Resolution Provenance

Treat repositories as a first-class inspection topic before interpreting a dependency result:

1. Read settings-level `pluginManagement.repositories` for plugin resolution separately from project dependency resolution.
2. Read settings-level `dependencyResolutionManagement.repositories`, then each project build file for project-level `repositories`.
3. Record repository order and content filters. Order affects which repository supplies metadata or artifacts; filters explain why a repository was or was not considered.
4. Use `dependencies` to inspect the resolved tree and `dependencyInsight` to explain selection and provenance for a specific artifact.

Settings-level repository management and content filtering exist across Gradle 7/8/9. Verify the wrapper and settings before asserting `FAIL_ON_PROJECT_REPOS`; do not assume project repositories are forbidden merely because settings declares repositories.

## Graph Audit via `inspect_dependencies`

Use `inspect_dependencies` to visualize the resolved tree and detect unexpected versions.

### Basic Audit
```json
{
  "projectPath": ":app",
  "configuration": "runtimeClasspath",
  "onlyDirect": false
}
```

### Targeted Filtering
Use a Kotlin regex over `group:name:version[:variant]` to isolate a specific dependency's footprint.
```json
{
  "projectPath": ":app",
  "dependency": "^org\\\\.jetbrains\\\\.kotlinx:kotlinx-coroutines-core(:.*)?$"
}
```

## Dependency Insight and Conflict Resolution

### Identifying the Winner

Use `dependencyInsight` to determine why a particular version was selected.
```json
{
  "commandLine": [":app:dependencyInsight", "--dependency", "slf4j-api", "--configuration", "compileClasspath"],
  "captureTaskOutput": ":app:dependencyInsight"
}
```
This shows the resolution path (who requested what version) and the final winner.

### Conflict Resolution Menu

When a conflict is detected, choose a resolution strategy based on the intent:

| Intent | Move | Handoff to `authoring-gradle-builds` |
| :--- | :--- | :--- |
| **Force Exact Version** | Force | `resolutionStrategy.force("group:artifact:version")` |
| **Remove Transitive** | Exclude | `exclude(group = "...", module = "...")` |
| **Align Group/BOM** | Platform | `implementation(platform("group:artifact:version"))` |
| **Enforce Minimum** | Constraint | `constraints { implementation("group:artifact:version") { ... } }` |

## Resolution Provenance and Freshness

A resolved dependency graph is time-, repository-, and cache-dependent. A direct declaration is not necessarily the selected runtime version.

| Scenario | Observable Symptom | Action |
| :--- | :--- | :--- |
| **Dynamic/SNAPSHOT cache** | Newer publication exists but the graph shows an older result. | Use `--refresh-dependencies` for an intentional online refresh; record the refresh and exact selected version. The documented default TTL for dynamic/changing modules is 24 hours unless the build overrides it. |
| **Offline mode** | Build succeeds from stale cache or fails for a module available remotely. | Use `--offline` only when network absence is intentional; do not treat its graph as current repository evidence. |
| **Conflict winner** | Graph shows a higher version than the direct declaration. | Use `dependencyInsight` to inspect requested versions, paths, constraints, and the selected winner. Normal conflict resolution selects the highest version unless platforms, strict constraints, capabilities, or resolution rules change it. |
| **Repository order** | Same GAV changes after repository additions or reordering. | Record repository declarations and order, preserve existing order during diagnosis, and compare with a refresh; coordinates alone do not prove artifact provenance. |

**Anti-pattern**: Re-running commands and assuming Gradle re-checks dynamic metadata immediately, or forcing a version before reading the resolution path.

### Checking for Stable Updates

Use `updatesOnly: true` and `stableOnly: true` to find available upgrades.
```json
{
  "projectPath": ":app",
  "updatesOnly": true,
  "stableOnly": true
}
```

### Version Filtering

Use `versionFilter` to narrow update searches to specific major/minor ranges.
```json
{
  "projectPath": ":app",
  "updatesOnly": true,
  "versionFilter": "^8\\."
}
```

### Maven Version Lookup

Use `lookup_maven_versions` to check the full release history of a GAV on Maven Central.
```json
{
  "coordinates": "org.jetbrains.kotlinx:kotlinx-coroutines-core"
}
```

**More info**:
- Dependencies and repositories: `gradle_docs` `tag:userguide`, path `userguide/dependency_management_basics.md`
- Configurations and graph debugging: `gradle_docs` `tag:userguide`, path `userguide/viewing_debugging_dependencies.md`.
- Conflicts and highest-version selection: `gradle_docs` `tag:userguide`, path `userguide/dependency_constraints_conflicts.md`.
- Caching, refresh, and offline behavior: `gradle_docs` `tag:userguide`, path `userguide/dependency_caching.md`.
- MCP graph and update inspection: `inspect_dependencies`; Maven releases: `lookup_maven_versions`

Version notes: Version catalogs are stable from 7.4+. For builds < 7.4, prefer existing `buildSrc`, scripts, or `ext` properties.
