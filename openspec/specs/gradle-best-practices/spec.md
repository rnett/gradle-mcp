# Capability: gradle-best-practices

## Purpose

Specifies how the `gradle_expert` skill provides best practices reference documents for Gradle build authoring and performance optimization.

## Requirements

### Requirement: Integrated best practices reference

The `gradle_expert` skill SHALL include a set of reference documents that detail best practices for Gradle build authoring.

#### Scenario: User asks for performance optimization

- **WHEN** user asks how to improve build performance
- **THEN** system references `best_practices.md` and provides actionable advice such as enabling the configuration cache, using lazy APIs, or avoiding certain plugins.

#### Scenario: User asks for project structure advice

- **WHEN** user asks for the recommended project structure
- **THEN** system provides guidance based on official Gradle recommendations and existing project patterns found in `common_build_patterns.md`.
