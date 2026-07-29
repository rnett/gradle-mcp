# Proposal: Split gradle skill into two focused skills

## Why

The `gradle` skill currently serves two fundamentally different audiences in a single document: software engineers running builds/tests (Gradle users) and build engineers writing/maintaining build scripts and plugins (build authors). This creates routing ambiguity (agents can't distinguish build execution from build authoring), context pollution (build-authoring content wastes context when running a build), and stunted growth (build-authoring content like CI/CD, dependency locking, Worker API can't expand without worsening pollution). Meanwhile the 36 best-practices detail files (all build-author content) are physically located under the `gradle` skill directory.

This change extracts build-authoring into a dedicated `gradle-build-authoring` skill, moves the best-practices reference there, and refines both skills with gap-filling content.

## What Changes

- **Create new `gradle-build-authoring` skill** with dedicated frontmatter, constitution, directives, workflows (8), when-to-use scenarios (8), examples (7), and resources linking to 10 new reference documents plus the best-practices index.
- **Move best-practices to gradle-build-authoring** — The `GenerateBestPracticesDoc.kt` output path changes from `gradle/references/best-practices/` to `gradle-build-authoring/references/best-practices/`. The gradle skill no longer references best-practices.
- **Trim `gradle` skill to build execution only** — Remove Constitution rules 11-15, Idiomatic DSL Patterns, Creating a New Module workflow, Performance Audit workflow, Build Logic Refactoring, and corresponding examples. Add cross-reference to `gradle-build-authoring`. Update frontmatter with negative trigger for build-authoring.
- **Author 10 new reference documents** for build authoring: version-catalogs.md, testing-configuration.md, ci-cd-builds.md, dependency-locking.md, worker-api.md, jdk-toolchains.md, build-scans.md, continuous-builds.md, kotlin-compiler-options.md, artifact-publishing.md.
- **Expand `common_build_patterns.md`** with a build-logic composite build section covering `includeBuild("build-logic")`, precompiled script plugins, and type-safe project accessors.

## Capabilities

### New Capabilities

- `gradle-build-authoring`: Dedicated skill for Gradle build script authoring, plugin development, convention plugins, version catalogs, build performance optimization, dependency locking, JDK toolchains, artifact publishing, testing configuration, CI/CD configuration, and continuous builds.

### Modified Capabilities

- `gradle-skill`: Trimmed to Gradle build execution, test running, project introspection, diagnostics, and documentation research only. No longer covers module creation, performance audits, build logic refactoring, or best-practices consultation (all migrated to `gradle-build-authoring`).
- `skill-metadata`: Added scenario for build-authoring skill discovery with positive and negative trigger verification.

## Impact

- **New directory**: `src/main/skills/gradle-build-authoring/` with SKILL.md, references/ (10 new docs + best-practices/).
- **Moved directory**: `src/main/skills/gradle/references/best-practices/` -> `src/main/skills/gradle-build-authoring/references/best-practices/`.
- **Modified files**: `src/main/skills/gradle/SKILL.md` (trimmed), `src/main/skills/gradle/references/common_build_patterns.md` (expanded).
- **Modified generator**: `GenerateBestPracticesDoc.kt` output path changed.
- **New OpenSpec specs**: Delta specs for `gradle-skill`, `gradle-build-authoring`, `skill-metadata`.
- **No tool changes**: All MCP tools unchanged.