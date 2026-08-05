# Capability: gradle-build-authoring

## Purpose

Specifies how the `authoring-gradle-builds` skill guides build engineers in authoring and maintaining Gradle build logic, separate from the `using-gradle` skill which covers build execution and diagnostics.

## MODIFIED Requirements

### Requirement: Skill for Gradle build authoring

The system SHALL provide an `authoring-gradle-builds` skill that guides build engineers in authoring and maintaining Gradle build logic, separate from the `using-gradle` skill which covers build execution and diagnostics.

#### Scenario: User asks to create a convention plugin

- **WHEN** user asks to extract shared build logic into a convention plugin
- **THEN** system activates `authoring-gradle-builds` and guides creation of a `build-logic` composite build with a precompiled script plugin

#### Scenario: User asks to modify build.gradle.kts

- **WHEN** user asks to modify a `build.gradle.kts` or `settings.gradle.kts` file
- **THEN** system activates `authoring-gradle-builds` (not `using-gradle`) for the build-authoring guidance. For additional test suites, it guides the use of the `jvm-test-suite` plugin over manual source-set registration.

#### Scenario: Constitution enforces safe authoring practices

- **WHEN** an agent authoring build logic registers tasks or declares dependencies
- **THEN** it follows constitution rules: lazy APIs (`register` over `create`), no `allprojects/subprojects`, never access Project object inside task actions, never resolve configurations during configuration phase

#### Scenario: Authoring workflows cover full lifecycle

- **WHEN** a user needs help with any common build-authoring task
- **THEN** the skill provides structured workflows for all eight domains: module creation, performance audit, build logic refactoring, dependency addition, testing configuration (including `jvm-test-suite` registration and `check` wiring), CI/CD setup, dependency locking, and artifact publishing

#### Scenario: Zero tool-execution content

- **WHEN** an agent reads the `authoring-gradle-builds` skill
- **THEN** it contains no references to tool-execution operations: no `query_build`, no `captureTaskOutput`, no `--tests` syntax, no build command invocations

### Requirement: Build-authoring skill references

The `authoring-gradle-builds` skill SHALL reference the following resources:

- `references/convention-plugins.md` for multi-project builds and convention plugin patterns
- `references/best-practices/_index.md` for categorized best-practices (co-located in the same skill directory)
- `references/upgrading-and-release-notes.md` for Gradle version migration guides and release notes pointers
- 21 authored reference documents under `references/`, hybrid-routed: 9 are woven into the `SKILL.md` body through its checklist, workflows, and Constitution directives; 12 remain in an authored Decision Routing table for authoring actions without another prose home.

#### Scenario: Agent references common build patterns

- **WHEN** an agent in `authoring-gradle-builds` needs to set up a convention plugin
- **THEN** it opens `references/convention-plugins.md` for the pattern

#### Scenario: Agent consults gap-filling references

- **WHEN** an agent in `authoring-gradle-builds` needs CI/CD configuration guidance
- **THEN** it opens `references/ci-cd-builds.md` for best practices and cross-references the `using-gradle` skill's CI-as-evidence orientation guidance.

#### Scenario: Agent consults upgrading and release notes for version-sensitive changes

- **WHEN** an agent in `authoring-gradle-builds` makes a version-sensitive change (wrapper upgrade, API migration, deprecation fix)
- **THEN** it consults the upgrading page for the wrapper's major version via `gradle_docs(path="userguide/upgrading_version_<N>.md")` and checks `gradle_docs(query="tag:release-notes")` for breaking changes
- **AND** the SKILL.md Before You Modify checklist SHALL route to `references/upgrading-and-release-notes.md`

## ADDED Requirements

### Requirement: Cross-reference from gradle skill

The `using-gradle` skill SHALL include a cross-reference directing build-authoring questions to the `authoring-gradle-builds` skill.

#### Scenario: Agent routes build-script question correctly

- **WHEN** agent reads the `gradle` skill and encounters a build-script modification request
- **THEN** it finds the "Build Authoring" cross-reference section and activates `authoring-gradle-builds` instead
