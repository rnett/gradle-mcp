<!--
class: authored-local
skill: using-gradle
-->
# Dependency Updates

Discovers newer versions of project dependencies using `inspect_dependencies` and `lookup_maven_versions`.

## Checking for Updates

### All Direct Dependencies

Use `inspect_dependencies` with `updatesOnly: true` for a flat summary of upgradeable dependencies:

```json
{
  "projectPath": ":app",
  "updatesOnly": true
}
```

Returns lines like `group:artifact: current → latest` with project paths where each is used.

### Stable Updates Only

```json
{
  "projectPath": ":app",
  "updatesOnly": true,
  "stableOnly": true
}
```

Excludes pre-release versions (alpha, beta, rc).

### Targeted Update Check

```json
{
  "projectPath": ":app",
  "dependency": "kotlinx-coroutines",
  "updatesOnly": true
}
```

Check updates for a specific dependency or group.

### Version Filter

```json
{
  "projectPath": ":app",
  "updatesOnly": true,
  "versionFilter": "^2\\."
}
```

Only consider versions matching the given regex (e.g., stay on major version 2).

## Looking Up Maven Versions

Use `lookup_maven_versions` to see the full release history of a specific artifact:

```json
{
  "coordinates": "org.jetbrains.kotlinx:kotlinx-coroutines-core"
}
```

Returns versions sorted most-recent first with publish dates. Useful for:

- Verifying exact release history instead of guessing version numbers.
- Finding when a specific version was published.
- Checking if a newer patch exists for a pinned version.

### Pagination

```json
{
  "coordinates": "org.jetbrains.kotlinx:kotlinx-coroutines-core",
  "limit": 20
}
```

Retrieve more versions from the history.

## Workflow

1. **Audit**: Run `inspect_dependencies(updatesOnly=true)` to see what's available.
2. **Verify**: Use `lookup_maven_versions` to confirm the target version exists and check its publish date.
3. **Apply**: Hand off to `authoring-gradle-builds` to update the version catalog or build script.
4. **Verify**: Run a build to ensure compatibility.

## Examples

### Check all stable updates across the project

```json
{ "projectPath": ":", "updatesOnly": true, "stableOnly": true }
```

### Look up release history for a library

```json
{ "coordinates": "com.google.guava:guava", "limit": 10 }
```
