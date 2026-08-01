<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Upgrading and Release Notes

Consult these resources when making version-sensitive changes: wrapper upgrades, API migrations, deprecation fixes, or any change whose behavior differs across Gradle versions.

## When to Consult

- Upgrading the Gradle wrapper version (major or minor)
- Fixing deprecation warnings emitted by the build
- Migrating away from removed or changed APIs
- Verifying whether a behavior change is a known breaking change

## Key Documentation Pages

Use `gradle_docs` to read these pages at runtime:

| Page | `gradle_docs` path | Covers |
|---|---|---|
| Upgrading to Gradle 9 | `userguide/upgrading_version_9.md` | Breaking changes and migration steps for 9.x minor versions |
| Upgrading to Gradle 8 | `userguide/upgrading_version_8.md` | Breaking changes and migration steps for 8.x minor versions |
| Upgrading major versions | `userguide/upgrading_major_version_9.md` | Cross-major-version migration (e.g., 8 → 9) |
| Release Notes | `release-notes.md` | New features, fixed issues, and known problems per version |

## Tag Pointers

- `tag:upgrading` — scopes search to all upgrading/migration pages
- `tag:release-notes` — scopes search to release notes

Example queries:

```
gradle_docs(query="tag:upgrading map notation")
gradle_docs(query="tag:release-notes configuration cache")
gradle_docs(path="userguide/upgrading_version_9.md")
```

## Workflow

1. Read `gradle/wrapper/gradle-wrapper.properties` to identify the current wrapper version.
2. Open the upgrading page for the target major version (e.g., `userguide/upgrading_version_9.md`).
3. Search within the page for the specific API, behavior, or deprecation you are addressing.
4. Check `release-notes.md` for the target version for additional context on new features or known issues.
5. If the generated best-practices corpus covers the topic, cross-reference it for rationale, but treat the upgrading page as authoritative for migration steps.
