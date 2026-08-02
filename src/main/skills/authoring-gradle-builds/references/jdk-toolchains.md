# JDK Toolchains

Use a Java toolchain to declare the Java language level that compile, test, and tool tasks must use. A toolchain makes the build's required JDK explicit and decouples that JDK from the JVM that starts the Gradle daemon. Configure the toolchain in convention or build logic shared by every JVM-producing project.

## Default decision

| Need | Default | Do not do this |
|---|---|---|
| Compile Java or run JVM tests | Configure `java.toolchain.languageVersion` | Depend on whichever `JAVA_HOME` happens to be installed |
| Select a vendor | Omit `vendor` unless reproducibility or a vendor requirement demands it | Assume every resolver offers every vendor/version combination |
| Express Java source and bytecode level | Use the toolchain language version | Treat `sourceCompatibility` and `targetCompatibility` as a substitute for selecting a JDK |
| Run Gradle itself | Check the Gradle wrapper's JVM compatibility separately | Assume a project toolchain changes the JVM running Gradle |
| Kotlin/JVM output | Align Kotlin `jvmTarget` with the same language version | Set unrelated Java and Kotlin target versions |
| Missing local JDK | Allow an approved resolver and document the network/cache policy | Hide automatic downloads from CI or developers |

## Configure the Java toolchain

```kotlin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
```

The `java {}` form is equivalent in a project that applies the Java plugin:

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
```

Use one shared `JavaLanguageVersion` convention when Java and Kotlin targets are configured in separate plugins. A toolchain selects the JDK used by Java compilation, test execution, and other toolchain-aware JVM tasks. It does not automatically rewrite every third-party task or external process; inspect those tasks when the build launches its own JVM.

## Toolchain versus compatibility properties

`sourceCompatibility` controls the Java source language level accepted by Java compilation. `targetCompatibility` controls the class-file level emitted by Java compilation. Neither property selects the JDK used to run the compiler or tests, and neither guarantees that all toolchain-aware tasks use the same JDK.

Prefer this:

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
```

Use compatibility properties only when a legacy plugin or publishing contract needs an explicit compatibility value in addition to the toolchain. If both are present, keep them consistent and document why the duplicate declaration exists. Do not use `sourceCompatibility = JavaVersion.VERSION_17` and `targetCompatibility = JavaVersion.VERSION_17` as the primary way to select the build JDK.

## JVM that runs Gradle versus JVM used by the build

The JVM that launches Gradle is a separate concern from the project toolchain. Read `gradle/wrapper/gradle-wrapper.properties`, then check the wrapper's runtime compatibility before authoring a toolchain. A project targeting Java 17 can still be built by a Gradle daemon running on a different supported JVM.

| Gradle line | JVM running Gradle | Authoring fallback |
|---|---|---|
| 9.x | Java 17 or newer | Use the current 9.x compatibility page and keep project toolchains explicit |
| 8.x | Java 8 minimum, with the supported maximum varying by minor release | Verify the exact wrapper minor before selecting the daemon JVM |
| 7.3+ | Java 8 minimum; Java 17 support begins at 7.3 | Use a supported daemon JVM and configure project toolchains independently |
| 7.0-7.2 | Java 8 minimum; Java 17 is not supported for running Gradle | Upgrade the wrapper or run Gradle on a supported older JVM |

**Anti-pattern:** configure a Java toolchain and claim that it upgrades or downgrades the JVM running Gradle. Use `gradle/gradle-daemon-jvm.properties` or the environment and wrapper policy for daemon selection; use `java.toolchain` for project work.

## Toolchain for compile, test, and run tasks

Apply the same toolchain convention to every JVM target unless a task has a deliberate compatibility requirement. Java compilation uses the selected compiler; JVM test tasks and Java application execution should use the matching launcher when those tasks are toolchain-aware.

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
}
```

Do not assume a `JavaExec`, test fixture, code generator, or third-party task inherits the project toolchain. Configure its launcher or executable explicitly when the task exposes that property. Avoid hard-coded absolute JDK paths; they break on other agents and CI workers.

## Auto-provisioning

Gradle can download a matching JDK when no local installation satisfies the toolchain. Automatic download is documented from Gradle 7.5. Gradle 7.6 adds pluggable toolchain resolver repositories. Before 7.5, require a compatible local JDK or use an organization-approved provisioning mechanism outside Gradle.

```properties
org.gradle.java.installations.auto-download=true
# Optional additional installation search path.
org.gradle.java.installations.paths=C:/jdk-storage
```

**Default:** enable downloads only when the repository and CI policy permit network access, and make the cache location and trust boundary explicit.

**Anti-patterns:** rely on an undocumented download side effect, commit machine-specific paths, or let CI silently resolve a different vendor/build than local development. Pin the requested language version; add a vendor constraint only when the requirement is real.

### Foojay resolver

The Foojay resolver is a settings-level resolver plugin, not a project dependency. Apply it in `settings.gradle.kts` and manage its version through the build's plugin policy:

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
```

The resolver allows Gradle to locate a matching JDK through the Foojay Disco service. Keep resolver configuration in settings, not in `build.gradle.kts`, and treat the resolver plugin version as independently version-sensitive. If the build cannot use external resolution, install approved JDKs locally and disable or prohibit automatic download instead of adding an unreviewed resolver.

## Vendor-specific toolchains

Specify a vendor only when the build requires a particular distribution or support policy:

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.AZUL)
    }
}
```

Common vendor specifications include `ADOPTIUM`, `AMAZON`, `AZUL`, `ORACLE`, and `ALIBABA`. A vendor constraint narrows resolution and can make provisioning fail on platforms where that distribution is unavailable. Prefer the language version alone for portable libraries.

## Kotlin/JVM alignment

Set Kotlin `jvmTarget` to the same version as the Java toolchain. Keep the mapping in one convention so Java and Kotlin cannot drift. See [Kotlin Compiler Options](kotlin-compiler-options.md) for the typed Kotlin DSL and migration rules.

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

**Gotcha:** `targetCompatibility` is not the Kotlin compiler target. A Java 17 toolchain paired with Kotlin `jvmTarget = JVM_1_8` produces mixed bytecode and can fail later through tool validation or runtime linkage. Conversely, a Kotlin target newer than Java's target can produce incompatible artifacts.

## Kotlin Multiplatform

KMP configures JVM and non-JVM targets independently. Apply the Java toolchain to the JVM target and configure JVM compiler options only on compilations that produce JVM bytecode. Do not put `jvmTarget` into `commonMain` or Native target configuration.

```kotlin
kotlin {
    jvm {
        withJava()
    }

    sourceSets {
        val jvmMain by getting
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
```

## Version notes

- **Gradle 9.x:** prefer toolchains, resolver plugins, lazy task configuration, and one shared JVM target policy. Gradle itself requires JVM 17 or newer.
- **Gradle 8.x:** toolchains and automatic provisioning are supported; resolver plugin availability and daemon JVM support vary by minor version. Verify the wrapper minor before choosing the runtime JVM.
- **Gradle 7.6-7.x:** toolchain resolver plugins are available from 7.6; automatic download is documented from 7.5. For earlier 7.x builds, require a local JDK or external provisioning.
- **Kotlin:** align `jvmTarget` with the Java toolchain across the Kotlin Gradle Plugin version used by the build. Use the typed `compilerOptions` API where that version supports it; see the companion reference for its Kotlin version notes.

## More info

- Gradle toolchains: `gradle_docs` `tag:userguide`, path `userguide/toolchains.md`; resolver term `provisioning`, path `userguide/toolchain_plugins.md`
- Gradle daemon/runtime compatibility: `gradle_docs` `tag:userguide`, path `userguide/compatibility.md`
- Gradle MCP documentation lookup: `gradle_docs`
