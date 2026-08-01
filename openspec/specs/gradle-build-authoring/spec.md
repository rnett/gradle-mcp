# Capability: gradle-build-authoring

## Purpose

Specifies how the `gradle-build-authoring` skill guides build engineers in authoring and maintaining Gradle build logic, separate from the `gradle` skill which covers build execution and diagnostics.

## MODIFIED Requirements

### Requirement: Skill for Gradle build authoring

The system SHALL provide a `gradle-build-authoring` skill that guides build engineers in authoring and maintaining Gradle build logic, separate from the `gradle` skill which covers build execution and diagnostics.

#### Scenario: User asks to create a convention plugin

- **WHEN** user asks to extract shared build logic into a convention plugin
- **THEN** system activates `gradle-build-authoring` and guides creation of a `build-logic` composite build with a precompiled script plugin

#### Scenario: User asks to modify build.gradle.kts

- **WHEN** user asks to modify a `build.gradle.kts` or `settings.gradle.kts` file
- **THEN** system activates `gradle-build-authoring` (not `gradle`) for the build-authoring guidance

#### Scenario: Constitution enforces safe authoring practices

- **WHEN** an agent authoring build logic registers tasks or declares dependencies
- **THEN** it follows constitution rules: lazy APIs (`register` over `create`), no `allprojects/subprojects`, never access Project object inside task actions, never resolve configurations during configuration phase

#### Scenario: Authoring workflows cover full lifecycle

- **WHEN** a user needs help with any common build-authoring task
- **THEN** the skill provides structured workflows for all eight domains: module creation, performance audit, build logic refactoring, dependency addition, testing configuration, CI/CD setup, dependency locking, and artifact publishing

#### Scenario: Zero tool-execution content

- **WHEN** an agent reads the `gradle-build-authoring` skill
- **THEN** it contains no references to tool-execution operations: no `query_build`, no `captureTaskOutput`, no `--tests` syntax, no build command invocations

### Requirement: Build-authoring skill references

The `gradle-build-authoring` skill SHALL reference the following resources:

- `../gradle/references/common_build_patterns.md` for multi-project builds and convention plugin patterns
- `references/best-practices/_index.md` for categorized best-practices (co-located in the same skill directory)
- `references/upgrading-and-release-notes.md` for Gradle version migration guides and release notes pointers
- 10 gap-filling reference documents under `references/` covering version catalogs, testing, CI/CD, dependency locking, Worker API, JDK toolchains, build scans, continuous builds, Kotlin compiler options, and artifact publishing

#### Scenario: Agent references common build patterns

- **WHEN** an agent in `gradle-build-authoring` needs to set up a convention plugin
- **THEN** it opens `../gradle/references/common_build_patterns.md` for the pattern

#### Scenario: Agent consults gap-filling references

- **WHEN** an agent in `gradle-build-authoring` needs CI/CD configuration guidance
- **THEN** it opens `references/ci-cd-builds.md` for best practices

#### Scenario: Agent consults upgrading and release notes for version-sensitive changes

- **WHEN** an agent in `gradle-build-authoring` makes a version-sensitive change (wrapper upgrade, API migration, deprecation fix)
- **THEN** it consults the upgrading page for the wrapper's major version via `gradle_docs(path="userguide/upgrading_version_<N>.md")` and checks `gradle_docs(query="tag:release-notes")` for breaking changes
- **AND** the SKILL.md "Before You Modify" checklist and Decision Routing table SHALL route to `references/upgrading-and-release-notes.md`

## ADDED Requirements

### Requirement: Cross-reference from gradle skill

The `gradle` skill SHALL include a cross-reference directing build-authoring questions to the `gradle-build-authoring` skill.

#### Scenario: Agent routes build-script question correctly

- **WHEN** agent reads the `gradle` skill and encounters a build-script modification request
- **THEN** it finds the "Build Authoring" cross-reference section and activates `gradle-build-authoring` instead
