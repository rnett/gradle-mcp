## ADDED Requirements

### Requirement: Skill for Gradle build authoring

The system SHALL provide a `gradle_expert` skill that guides users in creating and modifying Gradle build files.

#### Scenario: User asks to add a dependency

- **WHEN** user provides a library name and asks to add it to the project
- **THEN** system uses `managing_gradle_dependencies` to find the correct coordinates and then guides the user to add it to `libs.versions.toml` and the relevant `build.gradle.kts` file.

#### Scenario: User asks to create a new module

- **WHEN** user asks to create a new Gradle module
- **THEN** system creates the directory structure, adds the module to `settings.gradle.kts`, and generates a standard `build.gradle.kts` file with common project configurations.
