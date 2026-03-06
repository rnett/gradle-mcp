## Why

Users need a specialized skill for authoring and editing Gradle builds that integrates best practices and documentation directly into the development workflow. This will improve build quality and developer productivity when working with
complex Gradle configurations.

## What Changes

- New skill `gradle_expert` in `skills/` directory.
- `gradle_expert/SKILL.md`: Main instruction file for the skill.
- `gradle_expert/references/`: Directory for best practices and common patterns (e.g., `best_practices.md`, `common_build_patterns.md`).
- Integration with existing tools (`gradle`, `gradle_docs`, `read_dependency_sources`, `search_dependency_sources`, `inspect_dependencies`).

## Capabilities

### New Capabilities

- `gradle-build-authoring`: Guidance and automated tasks for creating and modifying Gradle build scripts (`build.gradle.kts`, `settings.gradle.kts`) according to established conventions.
- `gradle-best-practices`: Integrated reference for Gradle idiomatic patterns, performance optimization, and project structure.
- `gradle-internal-research`: Authoritative lookup of Gradle internals, DSL reference, Javadocs, and samples using specialized search and source retrieval tools.

### Modified Capabilities

None.

## Impact

- New skill added to the `skills/` directory.
- No breaking changes.
