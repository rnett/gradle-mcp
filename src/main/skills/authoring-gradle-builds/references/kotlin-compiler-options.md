<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Kotlin Compiler Options

Configure Kotlin compiler behavior with the typed `compilerOptions` DSL. Treat compiler options as part of the build contract: the JVM target, language/API levels, warning policy, and opt-ins must be deliberate, consistent, and scoped to the compilation that needs them.

## Default decision table

| Topic | Default | Anti-pattern |
|---|---|---|
| Kotlin Gradle configuration | Use typed `compilerOptions` properties | Add raw compiler flags when a typed property exists |
| JVM bytecode | Match `jvmTarget` to the Java toolchain | Set Kotlin and Java targets independently |
| Warnings | Set `allWarningsAsErrors` only when the build owns all warnings or has an explicit cleanup policy | Enable it blindly on third-party or migration-heavy source sets |
| Language/API compatibility | Set `languageVersion` and `apiVersion` deliberately, usually to the supported baseline | Let compiler upgrades silently raise the source or API contract |
| Opt-ins | Use `-opt-in=...` through `freeCompilerArgs` or the supported typed property | Use deprecated `-Xopt-in=...` |
| KMP | Configure JVM-only options per JVM target/compilation | Put `jvmTarget` in `commonMain` or apply JVM flags to Native/JS |

## `compilerOptions` DSL

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}
```

Use the typed property for `jvmTarget`, `languageVersion`, `apiVersion`, and `allWarningsAsErrors`. Use `freeCompilerArgs` for flags that do not have a typed property, and keep each flag's rationale near the configuration that owns it.

## Version notes: modern versus legacy Kotlin DSL

- **Kotlin 1.9.0 and newer:** the project-level `kotlin { compilerOptions { ... } }` block is documented and is the preferred form when supported by the selected Kotlin Gradle Plugin.
- **Kotlin 1.8.0 and newer:** `compilerOptions` is documented as a compile-task input, so task-level configuration is available even when project-level configuration is not.
- **Kotlin before 1.8.0:** use the legacy API supported by that plugin version, typically `kotlinOptions`, and plan migration before upgrading the plugin.
- **`-opt-in`:** available since Kotlin 1.6.0. Earlier Kotlin versions use `-Xopt-in`; the exact formal release in which `-Xopt-in` became deprecated is not verified here. Treat `-Xopt-in` as a legacy compatibility fallback only when an older compiler requires it, not as current guidance.
- **Kotlin 2.x:** prefer typed compiler options and verify plugin-specific API compatibility before copying a newer snippet into an older build.

The legacy `kotlinOptions` API is deprecated with error in current Kotlin API documentation. Do not introduce new `kotlinOptions` configuration. If an old plugin or source set still requires it, migrate the configuration at the same time as the Kotlin plugin upgrade or isolate the compatibility code and record the supported version range.

## JVM target and Java toolchain alignment

Use the same Java language level for Java compilation and Kotlin/JVM bytecode:

```kotlin
import org.gradle.jvm.toolchain.JavaLanguageVersion
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

**Gotcha:** `jvmTarget` controls Kotlin bytecode; `sourceCompatibility` and `targetCompatibility` are Java settings. Matching only `targetCompatibility` does not select a JDK and does not guarantee Kotlin alignment. A mismatched pair can yield inconsistent artifacts or fail tool validation with a class-file-version error.

If the build computes its Java version from a convention, map that single value to both the Java toolchain and Kotlin `JvmTarget`. Do not use unrelated string literals in separate subprojects.

## Warnings as errors

Enable warnings-as-errors only when the warning set is owned and kept clean:

```kotlin
kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}
```

**Default:** enable it for production source sets when CI treats warnings as defects. Scope or disable it for generated code, migration shims, or source sets that intentionally compile against warnings from an older API until those warnings have an explicit disposition.

**Anti-pattern:** pass `-Werror` through `freeCompilerArgs` when the typed `allWarningsAsErrors` property is available, or turn the policy on in one target while assuming every KMP target has the same warning set.

## `languageVersion` and `apiVersion`

`languageVersion` selects the Kotlin language syntax and semantics accepted by the compiler. `apiVersion` restricts the Kotlin API declarations that source may use. Keep both at the supported compatibility baseline unless the project intentionally adopts newer language or API behavior:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

kotlin {
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}
```

**Default:** raise the compiler plugin independently from the language/API baseline, then raise the baseline through a deliberate compatibility change. This lets a library compile with a newer compiler while preserving its published source/API floor when supported.

**Anti-pattern:** copy the Kotlin plugin version into both properties without checking source compatibility, published API policy, or all target compilers. In KMP, verify that the selected language/API versions are supported by every enabled target.

## Opt-ins

Use the current `-opt-in` spelling for compiler opt-ins:

```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        freeCompilerArgs.add("-opt-in=kotlin.experimental.ExperimentalKmpApi")
    }
}
```

The exact defect fixed in this reference is `-Xopt-in` -> `-opt-in`. Do not add `-Xopt-in` to new build logic. Prefer source-level `@OptIn(...)` when only a small set of declarations needs the opt-in; use compiler-wide `freeCompilerArgs` only when the policy applies to the whole compilation.

**Gotcha:** an opt-in is a source-compatibility decision, not a warning suppression. Keep it scoped to the target/source set that uses the experimental API, and revisit it when upgrading Kotlin or the dependency that owns the marker.

## Per-task and per-source-set configuration

Use task-level configuration when the option belongs to a specific compilation:

```kotlin
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}
```

For KMP, configure common language settings in common source sets and JVM compiler options on JVM compilations. Do not assume a source-set `languageSettings` block is interchangeable with target compiler options.

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
            }
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
```

The exact target DSL can vary by Kotlin Multiplatform plugin version. Inspect the versioned Kotlin documentation before applying a target-level snippet, and keep `jvmTarget` away from Native, JS, and common compilations.

## KMP caveats

- `jvmTarget` is valid only for JVM-producing compilations.
- Native and JS targets need their own supported compiler options; do not reuse JVM flags blindly.
- `allWarningsAsErrors`, opt-ins, language versions, and API versions can be shared only when every target supports the same option and policy.
- A common source set can use `languageSettings`, but a target compiler's typed `compilerOptions` belong to the target or compilation.
- Test compilations may inherit or override production options. Verify both production and test tasks when changing a shared convention.

## More info

- Kotlin compiler options, typed `JvmTarget`, and `-opt-in`: https://kotlinlang.org/docs/gradle-compiler-options.html.
- Legacy `kotlinOptions` deprecation: https://kotlinlang.org/api/kotlin-gradle-plugin/kotlin-gradle-plugin-api/org.jetbrains.kotlin.gradle.dsl/-kotlin-compile/kotlin-options.html.
- Kotlin 1.8.0 compiler-task input evidence and Kotlin 1.9.0 project-level DSL evidence: https://kotlinlang.org/docs/whatsnew18.html and https://kotlinlang.org/docs/whatsnew19.html.
- Gradle toolchains: `gradle_docs` `tag:userguide`, path `userguide/toolchains.md`; published docs: https://docs.gradle.org/current/userguide/toolchains.html.
- Gradle compiler/task configuration lookup: https://gradle-mcp.rnett.dev/latest/tools/GRADLE_DOCS_TOOLS/.
