<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Java Builds

Author JVM-based builds using the Java and Java Library plugins. This reference covers the Java plugin model, from source sets and compilation to annotation processing and mixed-language support. Use this to set up the build structure; hand off day-to-day execution, test runs, and artifact inspection to `using-gradle`.

## Operating Defaults

| Decision | Default | Anti-pattern |
|---|---|---|
| Plugin Choice | Use `java-library` for libraries (exposes an API); use `java` for applications. | Use the basic `java` plugin for libraries, hiding the API surface from consumers. |
| JVM Toolchains | Always declare a toolchain for reproducible targets. | Rely on the environment's `JAVA_HOME` or default JDK. |
| Dependency Scope | Use `api` for public surface types; `implementation` for internals. | Put everything on `api` to avoid "missing class" errors. |
| Project Layout | Stick to the convention: `src/main/java` and `src/test/java`. | Define haphazard directory structures without updating the `SourceSet` model. |

For guidance on toolchain setup and reproducible JDKs, read [JDK Toolchains](jdk-toolchains.md). For configuring test tasks and environments, see [Testing Configuration](testing-configuration.md).

## Source Sets and Configurations

The Java plugin organizes code into `SourceSet` objects. Every source set automatically creates associated configurations for dependency declaration.

### Source Set Model
A source set defines where source code and resources reside. The `main` and `test` source sets are provided by default.

```kotlin
sourceSets {
    main {
        java.setSrcDirs(listOf("src/main/java", "src/main/generated"))
        resources.setSrcDirs(listOf("src/main/resources"))
    }
    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
    }
}
```

### Source-Set-Based Configurations
Each source set (e.g., `main`) generates configurations:
- `implementation`: Internal dependencies for the source set.
- `api`: (Java Library only) Dependencies exposed to consumers of the library.
- `compileOnly`: Dependencies needed for compilation but provided at runtime.
- `runtimeOnly`: Dependencies required only at runtime.

**Default:** Use `implementation` for the vast majority of dependencies. Use `api` only when a type from the dependency appears in a public method signature or field.

**Anti-pattern:** Declaring dependencies directly on `compileClasspath` or `runtimeClasspath`. These are *resolvable* configurations; you must declare dependencies on the *declarable* "bucket" configurations (`implementation`, `api`).

## Annotation Processing

Annotation processors run during the compilation phase. They require a separate classpath from the project's implementation dependencies to avoid leaking processor internals into the runtime.

### Defining the Processor Path
Use the `annotationProcessor` configuration to declare processors.

```kotlin
dependencies {
    implementation("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30") 
    
    implementation("com.fasterxml.mapstruct:mapstruct:1.5.5.Final")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")
}
```

### Fine-Grained Processor Control
For complex builds, use the `annotationProcessorPath` of the specific `JavaCompile` task to isolate processors from the main compile classpath.

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.annotationProcessorPath = configurations.annotationProcessor.get()
}
```

**Default:** Declare processors via the `annotationProcessor` configuration.

**This is prohibited:** Adding annotation processors to `implementation` or `api`. This pollutes the runtime classpath with build-time tools.

## Mixed JVM Languages

Gradle supports joint compilation of Java, Kotlin, and Groovy. The build is structured to allow these languages to depend on each other.

### Joint Compilation Order
When mixing languages, Gradle typically compiles them in a specific order so that one can reference the other. For example, the Kotlin plugin ensures Kotlin code is compiled before Java code, allowing Java to reference Kotlin classes.

```kotlin
plugins {
    `java-library`
    kotlin("jvm")
}
```

**Default:** Use the standard plugin-provided joint compilation. If you define custom source sets for mixed languages, ensure they are registered with the correct language plugin to avoid "class not found" errors during joint compilation.

## Incremental Compilation and Avoidance

Gradle uses a "compile avoidance" mechanism to skip tasks when inputs have not changed in a way that affects the output.

### Ensuring Incrementality
To maintain incremental compilation, avoid using `@Input` on large, unstable files. Use specific `@InputFile` or `@InputDirectory` declarations.

**Default:** Trust the built-in incremental compilation of the Java plugin.

**Anti-pattern:** Forcing a clean build (`./gradlew clean`) to resolve compilation errors. This usually indicates a missing output declaration or a broken incremental-compiler assumption. Fix the task inputs instead.

### Version notes
- **Gradle 8/9:** Full support for `java-library` and toolchain-based compilation.
- **Gradle 7.x:** Toolchains are stable from 7.0; however, auto-downloading JDKs requires 7.5+.

**More info:**
- Java Plugin: `gradle_docs` `tag:userguide` path `userguide/java_plugin.md`; https://docs.gradle.org/current/userguide/java_plugin.html.
- Java Library: `gradle_docs` `tag:userguide` path `userguide/java_library_plugin.md`; https://docs.gradle.org/current/userguide/java_library_plugin.html.
- Incremental Compilation: `gradle_docs` `tag:userguide` path `userguide/java_plugin.md` search `incremental compile`; https://docs.gradle.org/current/userguide/java_plugin.html#sec:incremental_compile.
- Annotation Processing: `gradle_docs` `tag:userguide` path `userguide/java_plugin.md` search `annotation processing`; https://docs.gradle.org/current/userguide/java_plugin.html#sec:incremental_annotation_processing.
