# Gradle Sources Overview

This document serves as a guide for navigating the Gradle source code (located in `gradle-build-tool/`). It focuses on "directions" to find key components relevant to the MCP server.

## Top-Level Organization

The Gradle codebase is primarily divided into two main areas:

- `subprojects/`: Contains the core Gradle engine and base APIs.
- `platforms/`: Functional groups of modules that build on top of the core.

---

## Core APIs and Model

If you are looking for the fundamental building blocks of a Gradle build:

### Public APIs

Most public APIs are in `subprojects/core-api`.

- **Project**: `subprojects/core-api/src/main/java/org/gradle/api/Project.java`
- **Task**: `subprojects/core-api/src/main/java/org/gradle/api/Task.java`
- **Configuration**: `subprojects/core-api/src/main/java/org/gradle/api/artifacts/Configuration.java`
- **Plugin**: `subprojects/core-api/src/main/java/org/gradle/api/Plugin.java`

### Internal Implementations

The "Default" implementations for core APIs are usually in `subprojects/core`.

- **DefaultProject**: `subprojects/core/src/main/java/org/gradle/api/internal/project/DefaultProject.java`
- **DefaultTask**: `subprojects/core/src/main/java/org/gradle/api/internal/DefaultTask.java`

---

## Dependency Management

The dependency management engine is one of the most complex parts of Gradle.

### API

- **DependencyHandler**: `subprojects/core-api/src/main/java/org/gradle/api/artifacts/dsl/DependencyHandler.java`
- **RepositoryHandler**: `subprojects/core-api/src/main/java/org/gradle/api/artifacts/dsl/RepositoryHandler.java`

### Implementation

Most logic resides in `platforms/software/dependency-management`.

- **DefaultDependencyHandler**: `platforms/software/dependency-management/src/main/java/org/gradle/api/internal/artifacts/dsl/dependencies/DefaultDependencyHandler.java`
- **Resolution Engine**: Look into `platforms/software/dependency-management/src/main/java/org/gradle/api/internal/artifacts/ivypotential/` (and related internal packages).

---

## DSLs (Groovy and Kotlin)

### Kotlin DSL

Everything related to `.gradle.kts` support.

- **Location**: `platforms/core-configuration/kotlin-dsl`
- **Extensions**: `platforms/core-configuration/kotlin-dsl/src/main/kotlin/org/gradle/kotlin/dsl/ProjectExtensions.kt`
- **Accessors**: Logic for generating type-safe accessors is in the `accessors` sub-package.

### Groovy DSL

- **Location**: `platforms/core-configuration/model-groovy` and `platforms/core-configuration/base-services-groovy`.

---

## Standard Plugins

Many built-in plugins are located under the `platforms/` directory based on their purpose.

### JVM Plugins (Java, Groovy, Scala, etc.)

- **Java Plugin**: `platforms/jvm/plugins-java/src/main/java/org/gradle/api/plugins/JavaPlugin.java`
- **Java Library Plugin**: `platforms/jvm/plugins-java-library/`
- **JUnit Support**: `platforms/jvm/testing-jvm/`

### Core Plugins

- **Help/Diagnostic Tasks** (e.g., `tasks`, `help`, `dependencies`):
    - Base help tasks: `platforms/core-configuration/base-diagnostics/`
    - Dependency reports: `platforms/software/software-diagnostics/`
    - `HelpTasksPlugin`: `platforms/core-configuration/base-diagnostics/src/main/java/org/gradle/api/plugins/HelpTasksPlugin.java`

---

## Execution and Worker API

- **Task Execution**: `platforms/core-execution/execution/`
- **Worker API**: `platforms/core-execution/workers/`
- **Build Cache**: `platforms/core-execution/build-cache/`

---

## Logging and CLI

- **Logging API**: `platforms/core-runtime/logging-api/`
- **Logging Implementation**: `platforms/core-runtime/logging/`
- **CLI Infrastructure**: `platforms/core-runtime/cli/`

---

## Tooling API

The API used by IDEs and other external tools (like the Gradle MCP server itself) to communicate with Gradle.

- **Location**: `platforms/ide/tooling-api/`
- **Builders**: Implementation of the models sent over the Tooling API is often in `platforms/ide/tooling-api-builders/`.
