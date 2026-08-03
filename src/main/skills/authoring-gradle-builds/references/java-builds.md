# Java Builds

Author JVM-based builds using the Java and Java Library plugins. This reference covers the Java plugin model, from source sets and compilation to annotation processing and mixed-language support. Use this to set up the build structure; hand off day-to-day execution, test runs, and artifact inspection to `using-gradle`.

## Operating Defaults

| Decision | Default | Anti-pattern |
|---|---|---|
| Plugin Choice | Use `java-library` for libraries (exposes an API); use `java` for applications. | Use the basic `java` plugin for libraries, hiding the API surface from consumers. |
| JVM Toolchains | Always declare a toolchain for reproducible targets. | Rely on the environment's `JAVA_HOME` or default JDK. |
| Java version targeting | Use `options.release` to enforce the bytecode level and Java API floor. | Use `sourceCompatibility`/`targetCompatibility` to target a Java version. |
| Dependency Scope | Use `api` for public surface types; `implementation` for internals. | Put everything on `api` to avoid "missing class" errors. |
| Project Layout | Stick to the convention: `src/main/java` and `src/test/java`. | Define haphazard directory structures without updating the `SourceSet` model. |

For guidance on toolchain setup and reproducible JDKs, read [JDK Toolchains](jdk-toolchains.md). For configuring test tasks and environments, see [Testing Configuration](testing-configuration.md).

## `options.release` and Java version targeting

`options.release` enforces **both** the bytecode level **and** the Java API floor (strict `javac --release` semantics). Configure it on Java compile tasks alongside the toolchain:

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // selects the compile/test JDK
    }
    options {
        release.set(17) // or options.release = JvmTarget.JDK_17
    }
}
```

**Relationship to the toolchain:** the toolchain selects which JDK compiles; `options.release` fixes the target the compiler must honor. Use both for reproducible targets — the toolchain provides a compiler, `options.release` prevents accidental use of APIs newer than the target regardless of which JDK happens to be selected.

**Non-equivalence:** `sourceCompatibility`/`targetCompatibility` are legacy source/class-file fallbacks that do **NOT** enforce the API floor and must not be equated with `options.release`. Never use them as the primary way to target a Java version. See [JDK Toolchains](jdk-toolchains.md) for the full decoupling.

For Kotlin/JVM, align `jvmTarget` with the same version (see [Kotlin Compiler Options](kotlin-compiler-options.md)). A Kotlin target newer than `options.release` can produce artifacts that violate the Java API floor.

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
    register("integrationTest") {
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

## Java execution and application boundaries

Use the `application` plugin for runnable distributions and configure `mainClass`; use `JavaExec` for focused execution. Gradle 9.0 changed the toolchain boundary for `JavaExec`, so verify which toolchain launches the process instead of assuming it follows compilation automatically.

**Version-sensitive field-guide rule:** Read `gradle/wrapper/gradle-wrapper.properties` before applying the Gradle 9.0 `JavaExec` rule.

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
- Java Plugin and incremental compilation: `gradle_docs(path="userguide/java_plugin.md")`
- Java Library: `gradle_docs(path="userguide/java_library_plugin.md")`
- Annotation Processing: `gradle_docs(path="userguide/java_plugin.md")`
